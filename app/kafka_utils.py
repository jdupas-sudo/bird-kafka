import asyncio
import json
import logging
import os
import datetime
from aiokafka import AIOKafkaProducer, AIOKafkaConsumer
from app.models import SessionLocal, BirdEvent, Sighting

logger = logging.getLogger("birds_kafka")

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")

# Outbound event topics — published by the REST layer, consumed for audit logging.
TOPIC_BIRD_CREATED      = "bird.created"
TOPIC_BIRD_UPDATED      = "bird.updated"
TOPIC_BIRD_DELETED      = "bird.deleted"
TOPIC_SIGHTING_CREATED  = "sighting.created"

# Inbound command topics — published by external clients, consumed to trigger DB writes.
TOPIC_SIGHTING_INGEST   = "sighting.ingest"

# Topics the consumer subscribes to.
# Splitting into sets makes the routing logic in _consume() explicit.
_EVENT_TOPICS  = {TOPIC_BIRD_CREATED, TOPIC_BIRD_UPDATED, TOPIC_BIRD_DELETED, TOPIC_SIGHTING_CREATED}
_INGEST_TOPICS = {TOPIC_SIGHTING_INGEST}

ALL_TOPICS = list(_EVENT_TOPICS | _INGEST_TOPICS)

producer: AIOKafkaProducer | None = None
consumer_task: asyncio.Task | None = None


# ---------------------------------------------------------------------------
# Producer
# ---------------------------------------------------------------------------

async def start_producer():
    global producer
    for attempt in range(30):
        try:
            producer = AIOKafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP,
                value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            )
            await producer.start()
            logger.info("Kafka producer started")
            return
        except Exception:
            logger.warning(f"Kafka producer connect attempt {attempt + 1}/30, retrying...")
            await asyncio.sleep(2)
    logger.error("Could not connect Kafka producer after 30 attempts")


async def stop_producer():
    global producer
    if producer:
        await producer.stop()
        producer = None


async def publish(topic: str, message: dict):
    if producer:
        await producer.send_and_wait(topic, message)
        logger.info(f"Published to {topic}: {message}")
    else:
        logger.warning(f"No Kafka producer available, message dropped: {topic}")


# ---------------------------------------------------------------------------
# Consumer handlers
# ---------------------------------------------------------------------------

def _handle_event(db, msg) -> None:
    """Persist an outbound event to the BirdEvent audit log."""
    event = BirdEvent(
        event_type=msg.topic,
        topic=msg.topic,
        payload=json.dumps(msg.value),
        processed_at=datetime.datetime.utcnow(),
    )
    db.add(event)
    db.commit()
    logger.info(f"Logged event from {msg.topic}")


async def _handle_sighting_ingest(db, msg) -> None:
    """
    Create a Sighting record from a sighting.ingest message.

    Expected message shape:
        {
            "bird_id":       <int>,      # required — must reference an existing bird
            "location":      <str>,      # required
            "latitude":      <float>,    # optional
            "longitude":     <float>,    # optional
            "observer_name": <str>,      # optional
            "notes":         <str>       # optional
        }

    After persisting the sighting, fires a standard sighting.created event so
    the new record appears in the /api/events audit log, regardless of whether
    it was created via REST or via this Kafka path.
    """
    data = msg.value
    sighting = Sighting(
        bird_id=data["bird_id"],
        location=data["location"],
        latitude=data.get("latitude"),
        longitude=data.get("longitude"),
        observer_name=data.get("observer_name"),
        notes=data.get("notes"),
        observed_at=datetime.datetime.utcnow(),
    )
    db.add(sighting)
    db.commit()
    db.refresh(sighting)
    logger.info(f"Created sighting {sighting.id} for bird {sighting.bird_id} via Kafka ingest")

    # Fire the standard event so it flows through the same audit pipeline as
    # REST-created sightings. source field lets consumers distinguish the path.
    await publish(TOPIC_SIGHTING_CREATED, {
        "id": sighting.id,
        "bird_id": sighting.bird_id,
        "location": sighting.location,
        "source": "kafka-ingest",
    })


# ---------------------------------------------------------------------------
# Consumer loop
# ---------------------------------------------------------------------------

async def _consume():
    for attempt in range(30):
        try:
            consumer = AIOKafkaConsumer(
                *ALL_TOPICS,
                bootstrap_servers=KAFKA_BOOTSTRAP,
                group_id="birds-event-logger",
                auto_offset_reset="earliest",
                value_deserializer=lambda v: json.loads(v.decode("utf-8")),
            )
            await consumer.start()
            logger.info("Kafka consumer started")
            break
        except Exception:
            logger.warning(f"Kafka consumer connect attempt {attempt + 1}/30, retrying...")
            await asyncio.sleep(2)
    else:
        logger.error("Could not start Kafka consumer after 30 attempts")
        return

    try:
        async for msg in consumer:
            db = SessionLocal()
            try:
                # Route by topic: ingest topics trigger DB writes;
                # event topics are logged to the audit table.
                if msg.topic in _INGEST_TOPICS:
                    await _handle_sighting_ingest(db, msg)
                else:
                    _handle_event(db, msg)
            except Exception as e:
                logger.error(f"Error processing message from {msg.topic}: {e}")
                db.rollback()
            finally:
                db.close()
    finally:
        await consumer.stop()


def start_consumer():
    global consumer_task
    consumer_task = asyncio.create_task(_consume())


async def stop_consumer():
    global consumer_task
    if consumer_task:
        consumer_task.cancel()
        try:
            await consumer_task
        except asyncio.CancelledError:
            pass
        consumer_task = None
