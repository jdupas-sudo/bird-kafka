# Birds Kafka

REST + Kafka bird encyclopedia — built for Gatling load testing.

## Stack

- **FastAPI** — REST API with CRUD for birds, sightings, diet, and stats
- **Kafka** (KRaft mode, no Zookeeper) — event topics (bird.created/updated/deleted, sighting.created) + ingest topic (sighting.ingest) for async sighting creation
- **SQLite** — lightweight persistence with seed data (15 bird species)
- **Gatling** — Java-based load test simulation

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/birds` | List birds (paginated: `skip`, `limit`) |
| GET | `/api/birds/search?q=` | Search by name, family, or region |
| GET | `/api/birds/{id}` | Get bird by ID |
| POST | `/api/birds` | Create a bird |
| PUT | `/api/birds/{id}` | Update a bird |
| DELETE | `/api/birds/{id}` | Delete a bird |
| GET | `/api/birds/{id}/sightings` | List sightings for a bird |
| POST | `/api/birds/{id}/sightings` | Record a sighting |
| GET | `/api/birds/{id}/diet` | List diet items for a bird |
| GET | `/api/stats` | Aggregated statistics |
| GET | `/api/events` | Kafka event log (filterable by `event_type`) |
| POST | `/api/kafka/sightings` | Async sighting via Kafka — returns 202, persisted by consumer |

## Quick Start

```bash
docker compose up
```

The API will be available at `http://localhost:8080`. Interactive docs at `http://localhost:8080/docs`.

## Running Gatling Load Tests

```bash
# Start the stack, then run Gatling
docker compose up -d
docker compose --profile test run gatling
```

HTML reports are written to `./gatling-results/`.

By default, this runs all discovered simulations sequentially:
- `birds.BirdsApiSimulation`
- `birds.KafkaDirectSimulation`
- `birds.KafkaLagSimulation`

To run only one simulation, pass `gatling.simulationClass`:

```bash
docker compose --profile test run gatling -Dgatling.simulationClass=birds.BirdsApiSimulation
docker compose --profile test run gatling -Dgatling.simulationClass=birds.KafkaDirectSimulation
docker compose --profile test run gatling -Dgatling.simulationClass=birds.KafkaLagSimulation
```

### Configuration

Override load parameters with environment variables:

```bash
docker compose --profile test run -e USERS=50 -e DURATION=60 gatling
```

| Variable | Default | Description |
|----------|---------|-------------|
| `USERS` | `10` | Number of virtual users per scenario |
| `DURATION` | `30` | Ramp-up duration in seconds |
| `TEST_TYPE` | `breakpoint` | Injection profile: `smoke`, `capacity`, `soak`, `stress`, `breakpoint`, `ramp-hold` |
| `TARGET_URL` | `http://birds-api:8080` | API base URL |
| `KAFKA_DIRECT_MESSAGES_PER_USER` | `30` | Direct Kafka sends each virtual user performs in `KafkaDirectSimulation` |
| `KAFKA_DIRECT_HOT_KEY_BIRD_ID` | `1` | Fixed key used in the hot-partition direct Kafka scenario |
| `KAFKA_DIRECT_LARGE_NOTES_BYTES` | `4096` | Size of the `notes` field in the large-payload direct Kafka scenario |
| `KAFKA_LAG_USERS` | `max(1, USERS / 2)` | Virtual users in `KafkaLagSimulation` |
| `KAFKA_LAG_MAX_POLLS` | `20` | Max `/api/events` polls per probe before fail |
| `KAFKA_LAG_POLL_INTERVAL_MS` | `250` | Delay between lag-poll attempts |
| `KAFKA_LAG_EVENTS_LIMIT` | `200` | Event page size used by lag polling |

`BirdsApiSimulation` supports these load profiles:
- `smoke`: `atOnceUsers(1)`
- `capacity`: stepwise `incrementUsersPerSec(...)`
- `soak`: `constantUsersPerSec(...)`
- `stress`: `stressPeakUsers(...)`
- `breakpoint`: `rampUsers(...)`
- `ramp-hold`: `rampUsersPerSec(...).to(...), constantUsersPerSec(...)`

### Gatling Scenarios

| Scenario | VUs | Description |
|----------|-----|-------------|
| Browse | USERS | Health, list, get details, diet, sightings, stats |
| Search | USERS / 2 | Random search queries across bird data |
| Create & Cleanup | USERS / 3 | Full sync CRUD lifecycle: create, sighting, update, delete |
| Kafka Sighting Ingest | USERS / 4 | Async sighting via `POST /api/kafka/sightings` (202 path) |
| Events | USERS / 5 | Poll `/api/events` to measure Kafka consumer lag |

`KafkaDirectSimulation` now runs four pure-Kafka producer profiles:
- spread keys + small payload + `acks=1`
- hot key + small payload + `acks=1`
- spread keys + small payload + `acks=all`
- spread keys + large payload + `acks=1`

`KafkaLagSimulation` is a dedicated pass/fail consumer-lag check:
- sends one async `POST /api/kafka/sightings` probe per VU
- polls `/api/events?event_type=sighting.created` until that probe appears
- fails the final verification check when the probe is not observed within the poll budget

## Project Structure

```
.
├── app/
│   ├── main.py           # FastAPI routes
│   ├── models.py          # SQLAlchemy models
│   ├── schemas.py         # Pydantic schemas
│   ├── kafka_utils.py     # Kafka producer & consumer
│   └── seed.py            # Seed data (15 species)
├── gatling/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/test/
│       ├── java/birds/
│       │   ├── BirdsApiSimulation.java   # Lean orchestrator: protocol + injection profile
│       │   ├── KafkaDirectSimulation.java # Pure producer-side Kafka publish load
│       │   ├── KafkaLagSimulation.java    # Dedicated consumer-lag pass/fail probe
│       │   ├── endpoints/
│       │   │   ├── BirdEndpoints.java    # Atomic request definitions (CRUD + sub-resources)
│       │   │   └── SystemEndpoints.java  # health, stats, events
│       │   ├── groups/
│       │   │   ├── BrowseGroup.java      # Read-only navigation journey
│       │   │   ├── SearchGroup.java      # Keyword search journey
│       │   │   ├── CreateCleanupGroup.java  # Full write lifecycle + Kafka events
│       │   │   ├── KafkaSightingGroup.java  # Async sighting ingest via Kafka
│       │   │   ├── KafkaLagGroup.java       # Poll-until-visible lag probe logic
│       │   │   └── EventsGroup.java         # Kafka consumer lag check
│       │   └── utils/
│       │       ├── Config.java           # Env-var configuration (TARGET_URL, USERS, DURATION)
│       │       ├── Keys.java             # Session variable name constants
│       │       └── Feeders.java          # Feeder definitions (random ID, CSV search terms)
│       └── resources/
│           ├── bodies/
│           │   ├── create-bird.json      # POST /api/birds body (EL template)
│           │   ├── create-sighting.json  # POST /api/birds/{id}/sightings body
│           │   ├── ingest-sighting.json  # POST /api/kafka/sightings body (EL template)
│           │   └── update-bird.json      # PUT /api/birds/{id} body
│           └── data/
│               └── search-terms.csv     # Keywords fed to the Search scenario
├── Dockerfile
├── docker-compose.yml
└── requirements.txt
```
