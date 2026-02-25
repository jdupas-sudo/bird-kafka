import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import func

from app.models import Base, engine, get_db, Bird, Sighting, DietItem, BirdEvent
from app.schemas import (
    BirdCreate, BirdUpdate, BirdOut,
    SightingCreate, SightingOut,
    SightingIngest,
    DietItemOut, EventOut,
)
from app.seed import seed_database
from app.kafka_utils import (
    start_producer, stop_producer, start_consumer, stop_consumer,
    publish,
    TOPIC_BIRD_CREATED, TOPIC_BIRD_UPDATED, TOPIC_BIRD_DELETED,
    TOPIC_SIGHTING_CREATED, TOPIC_SIGHTING_INGEST,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("birds_kafka")


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    seed_database()
    await start_producer()
    start_consumer()
    logger.info("Application started")
    yield
    await stop_producer()
    await stop_consumer()
    logger.info("Application stopped")


app = FastAPI(
    title="Birds API",
    description="REST + Kafka bird encyclopedia — built for Gatling load testing",
    version="1.0.0",
    lifespan=lifespan,
)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

@app.get("/api/health")
def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Birds CRUD
# ---------------------------------------------------------------------------

@app.get("/api/birds", response_model=list[BirdOut])
def list_birds(
    skip: int = Query(0, ge=0),
    limit: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
):
    return db.query(Bird).offset(skip).limit(limit).all()


@app.get("/api/birds/search", response_model=list[BirdOut])
def search_birds(
    q: str = Query(..., min_length=1),
    db: Session = Depends(get_db),
):
    pattern = f"%{q}%"
    return (
        db.query(Bird)
        .filter(
            Bird.common_name.ilike(pattern)
            | Bird.scientific_name.ilike(pattern)
            | Bird.family.ilike(pattern)
            | Bird.region.ilike(pattern)
        )
        .all()
    )


@app.get("/api/birds/{bird_id}", response_model=BirdOut)
def get_bird(bird_id: int, db: Session = Depends(get_db)):
    bird = db.query(Bird).filter(Bird.id == bird_id).first()
    if not bird:
        raise HTTPException(status_code=404, detail="Bird not found")
    return bird


@app.post("/api/birds", response_model=BirdOut, status_code=201)
async def create_bird(payload: BirdCreate, db: Session = Depends(get_db)):
    bird = Bird(**payload.model_dump())
    db.add(bird)
    db.commit()
    db.refresh(bird)
    await publish(TOPIC_BIRD_CREATED, {"id": bird.id, "common_name": bird.common_name})
    return bird


@app.put("/api/birds/{bird_id}", response_model=BirdOut)
async def update_bird(bird_id: int, payload: BirdUpdate, db: Session = Depends(get_db)):
    bird = db.query(Bird).filter(Bird.id == bird_id).first()
    if not bird:
        raise HTTPException(status_code=404, detail="Bird not found")
    updates = payload.model_dump(exclude_unset=True)
    for key, value in updates.items():
        setattr(bird, key, value)
    db.commit()
    db.refresh(bird)
    await publish(TOPIC_BIRD_UPDATED, {"id": bird.id, "updated_fields": list(updates.keys())})
    return bird


@app.delete("/api/birds/{bird_id}", status_code=204)
async def delete_bird(bird_id: int, db: Session = Depends(get_db)):
    bird = db.query(Bird).filter(Bird.id == bird_id).first()
    if not bird:
        raise HTTPException(status_code=404, detail="Bird not found")
    name = bird.common_name
    db.delete(bird)
    db.commit()
    await publish(TOPIC_BIRD_DELETED, {"id": bird_id, "common_name": name})
    return None


# ---------------------------------------------------------------------------
# Sub-resources: Sightings
# ---------------------------------------------------------------------------

@app.get("/api/birds/{bird_id}/sightings", response_model=list[SightingOut])
def list_sightings(bird_id: int, db: Session = Depends(get_db)):
    bird = db.query(Bird).filter(Bird.id == bird_id).first()
    if not bird:
        raise HTTPException(status_code=404, detail="Bird not found")
    return bird.sightings


@app.post("/api/birds/{bird_id}/sightings", response_model=SightingOut, status_code=201)
async def create_sighting(bird_id: int, payload: SightingCreate, db: Session = Depends(get_db)):
    bird = db.query(Bird).filter(Bird.id == bird_id).first()
    if not bird:
        raise HTTPException(status_code=404, detail="Bird not found")
    sighting = Sighting(bird_id=bird_id, **payload.model_dump())
    db.add(sighting)
    db.commit()
    db.refresh(sighting)
    await publish(TOPIC_SIGHTING_CREATED, {
        "id": sighting.id,
        "bird_id": bird_id,
        "bird_name": bird.common_name,
        "location": sighting.location,
    })
    return sighting


# ---------------------------------------------------------------------------
# Sub-resources: Diet
# ---------------------------------------------------------------------------

@app.get("/api/birds/{bird_id}/diet", response_model=list[DietItemOut])
def list_diet(bird_id: int, db: Session = Depends(get_db)):
    bird = db.query(Bird).filter(Bird.id == bird_id).first()
    if not bird:
        raise HTTPException(status_code=404, detail="Bird not found")
    return bird.diet_items


# ---------------------------------------------------------------------------
# Aggregated stats
# ---------------------------------------------------------------------------

@app.get("/api/stats")
def stats(db: Session = Depends(get_db)):
    total_birds = db.query(func.count(Bird.id)).scalar()
    total_sightings = db.query(func.count(Sighting.id)).scalar()
    families = db.query(func.count(func.distinct(Bird.family))).scalar()
    avg_wingspan = db.query(func.avg(Bird.wingspan_cm)).scalar()
    by_status = (
        db.query(Bird.conservation_status, func.count(Bird.id))
        .group_by(Bird.conservation_status)
        .all()
    )
    return {
        "total_birds": total_birds,
        "total_sightings": total_sightings,
        "distinct_families": families,
        "average_wingspan_cm": round(avg_wingspan, 1) if avg_wingspan else 0,
        "by_conservation_status": {s: c for s, c in by_status},
    }


# ---------------------------------------------------------------------------
# Kafka ingest: async sighting creation
# ---------------------------------------------------------------------------

@app.post("/api/kafka/sightings", status_code=202)
async def ingest_sighting_via_kafka(payload: SightingIngest):
    """
    Publish a sighting creation request to the sighting.ingest Kafka topic.

    Returns 202 Accepted immediately — the sighting is not yet persisted at this
    point. The consumer picks up the message, writes the Sighting row, and fires
    a standard sighting.created event, which will then appear in /api/events.

    Use POST /api/birds/{id}/sightings instead if you need a synchronous response
    with the created sighting's ID.
    """
    await publish(TOPIC_SIGHTING_INGEST, payload.model_dump())
    return {"status": "accepted", "message": "Sighting queued for processing"}


# ---------------------------------------------------------------------------
# Kafka events log (read processed events)
# ---------------------------------------------------------------------------

@app.get("/api/events", response_model=list[EventOut])
def list_events(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=200),
    event_type: Optional[str] = None,
    db: Session = Depends(get_db),
):
    q = db.query(BirdEvent)
    if event_type:
        q = q.filter(BirdEvent.event_type == event_type)
    return q.order_by(BirdEvent.id.desc()).offset(skip).limit(limit).all()
