# RailPredictor

A rule-based, simulation-driven Train ETA Prediction System for Indian Railways. Given a live
train number, it fetches real-time position/delay data from RailRadar, runs a deterministic
disruption/cascade/recovery simulation, blends in real historical delay data and weather (when
available), and returns a predicted arrival time at the train's next station plus its final
destination, with an explicit confidence score.

**No machine learning is used anywhere in this system** — every prediction is rule-based and
explainable. See [docs/architecture.md](docs/architecture.md) for the full design and
[docs/prediction-model.md](docs/prediction-model.md) for the exact formula.

## Prerequisites

- Java 21 (JDK)
- No local Maven install needed — this project uses the Maven Wrapper (`mvnw`/`mvnw.cmd`), which
  downloads the right Maven version automatically.
- PostgreSQL 14+ — only required if you want persistence (historical observations, prediction
  snapshots, accuracy evaluation). The app runs perfectly well without it, using in-memory mock
  providers.
- A [RailRadar](https://railradar.in) API key — only required for real live-train data. Without
  it, live prediction requests fail fast with a clear "no API key configured" error (never a fake
  fallback).

## Quick start (no database, no API key — smallest possible run)

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` using mock weather/historical data and the "unavailable"
disruption provider. Real live predictions will fail (no RailRadar key), but Swagger UI, the health
endpoint, and the evaluation endpoint (reporting "disabled") all work.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

## Full run (real RailRadar data, PostgreSQL persistence)

1. Create a database: `createdb railpredictor` (or via `psql`/pgAdmin).
2. Copy [`.env.example`](.env.example) and fill in your own real values — **never commit real
   secrets**.
3. Set the environment variables from your copy (PowerShell shown; see `.env.example` for the full
   list and what each one does):

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "your-real-postgres-password"
$env:RAILRADAR_API_KEY = "your-real-railradar-key"
$env:SPRING_PROFILES_ACTIVE = "postgres"
.\mvnw.cmd spring-boot:run
```

Flyway applies all migrations automatically on startup (see `src/main/resources/db/migration`).
Hibernate never generates or alters schema (`ddl-auto=validate`) — Flyway is the only source of
truth for the schema.

### Making real data accumulate on its own

By default the app only records data when you call the prediction endpoint yourself. To let it
accumulate autonomously (needed before real accuracy evaluation means anything), also set:

```powershell
$env:PREDICTION_EVALUATION_ENABLED = "true"
$env:HISTORICAL_COLLECTION_ENABLED = "true"
$env:HISTORICAL_COLLECTION_TRAIN_NUMBERS = "12951,12301,12009"   # real 5-digit train numbers you want tracked
$env:PREDICTION_COLLECTION_ENABLED = "true"
$env:HISTORICAL_PROVIDER = "postgres"
$env:HISTORICAL_SECTION_PROVIDER = "postgres"
```

See [docs/evaluation-methodology.md](docs/evaluation-methodology.md) for exactly what this does,
why every one of these defaults to `false`/`mock`, and how much real evidence currently exists.

## Build and test

```bash
./mvnw clean verify
```

On Windows:

```powershell
.\mvnw.cmd clean verify
```

Tests never require PostgreSQL or a real RailRadar key — repository tests use an embedded H2
database, and every external call is mocked (`mockwebserver`).

## Docker (optional — for local PostgreSQL only)

You do **not** need Docker to run this project. If you'd rather not install PostgreSQL directly:

```bash
docker compose up -d postgres
```

This starts a local PostgreSQL 16 container matching the schema this project expects
(see [docker-compose.yml](docker-compose.yml)). Then run the app as in "Full run" above, pointing
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` at it (defaults in `docker-compose.yml` match the
`application-postgres.properties` defaults, so no extra config is needed if you use the compose
file as-is with `DB_PASSWORD` set to match).

A `Dockerfile` is also provided to build the application itself as a container image, for
deployment environments that require it — see comments in that file.

## Architecture at a glance

```
RailRadar (live position/delay) ─┐
Open-Meteo (weather)             ├─▶ PredictionService ─▶ SimulationEngine ─▶ PredictionEngine ─▶ PredictionResult (JSON)
PostgreSQL (historical delay)    ┘         │
                                            └─▶ PredictionSnapshotRecorder (opt-in, for later evaluation)
```

Independent, opt-in background schedulers (all default `false`):
`HistoricalObservationCollectionScheduler` (records real observations),
`PredictionCollectionScheduler` (autonomously calls the prediction pipeline for configured trains),
`PredictionEvaluationRefreshScheduler` (matches matured predictions against real outcomes),
`HistoricalDelayProfileRefreshScheduler` (always on when a database exists — aggregates raw
observations into reusable profiles).

## Key documentation

| Topic | Where |
|---|---|
| Full architecture, disruption/cascade/recovery models | [docs/architecture.md](docs/architecture.md) |
| Exact prediction formula, worked example | [docs/prediction-model.md](docs/prediction-model.md) |
| Every configuration property and env var | [docs/configuration.md](docs/configuration.md) |
| Historical-data design, provider selection, integrity fixes | [docs/historical-data-design.md](docs/historical-data-design.md) |
| REST API contract | [docs/api-contract.md](docs/api-contract.md) |
| JSON response shape reference | [docs/json-output.md](docs/json-output.md) |
| **How accuracy is measured, real vs. synthetic evidence, current limitations** | [docs/evaluation-methodology.md](docs/evaluation-methodology.md) |

## Known limitations (as of this writing — see docs/evaluation-methodology.md for current numbers)

- **Real-world accuracy is not yet established.** Only a small number of real evaluated samples
  exist so far — far below the statistical threshold this codebase itself enforces before trusting
  any accuracy number. No production coefficient has been empirically calibrated against real
  data; every simulation/confidence/historical-weight value is a documented, provisional heuristic.
- **Weather evidence is currently blocked**: RailRadar's live-status response does not supply
  station coordinates for the trains observed so far, so real weather is never fetched for them.
  This is an external data limitation, not a bug.
- **Railway disruption data is intentionally unavailable** — no reliable real-time public source
  was ever found. Simulated disruption remains a heuristic stand-in, never presented as real.
- **A true historical backtest cannot be built today** — Open-Meteo and disruption data have no
  historical archive to replay against; only station/section historical profiles are point-in-time
  reproducible.
- These are documented limitations, not implementation gaps — the software itself is complete and
  correctly degrades in every one of these cases (see `ControlledEvaluationSuiteTest` for the
  verified safety/degradation behavior).

## Security

- No credential (database password, RailRadar API key) is ever hardcoded, logged, or committed —
  every one is read from an environment variable with an empty/safe default.
- `.env.example` contains placeholders only.
- The `/actuator` surface exposes only `health` (no `env`, `beans`, `configprops`, or anything else
  that could leak internals).
