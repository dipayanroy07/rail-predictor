# Architecture

Status: Phase 1 (Project Foundation) complete. This document will grow as later
phases add real components; right now it describes the target shape, not
finished functionality.

## Goal

A rule-based, simulation-driven system that estimates train ETAs by combining
live train data with modelled disruptions (weather, congestion, engineering
work, signal halts), cascading delay effects, and delay recovery. No machine
learning is used unless explicitly requested later.

## Layering / data flow

```
RailRadar API
  -> RailRadar DTO            (railradar.dto)      raw external shape
  -> RailRadar Mapper         (railradar.mapper)   DTO -> domain
  -> Internal Domain Model    (model.domain)        clean-architecture core
  -> Route / Section Analysis (route)
  -> Prediction Context       (prediction / simulation)
  -> Simulation Engine        (simulation)          disruptions, cascade, recovery
  -> Prediction Engine        (prediction)          ETA, delay, confidence
  -> Prediction Result        (model.domain)
  -> JSON / REST API          (controller, model.dto)
```

Dependencies only ever point *down* this list. In particular:

- `model.domain` must never import anything from `railradar`, Spring Web,
  Jakarta Persistence, or `controller`. It is plain Java (records/enums)
  describing the business concepts, independent of how they arrive or how
  they're exposed.
- External integrations (RailRadar, weather, historical data) are accessed
  only through an interface (`TrainDataProvider`, `WeatherProvider`,
  `HistoricalDelayProvider`, ...). The rest of the application depends on the
  interface, never the concrete adapter, so a mock or a real implementation
  can be swapped in without touching callers.

## Package structure

| Package                        | Responsibility |
|---------------------------------|----------------|
| `config`                        | Spring `@Configuration` beans (HTTP clients, property bindings, etc.) |
| `controller`                    | REST endpoints: validate input, call a service, return a response. No business logic. |
| `service`                       | Orchestrates domain/prediction components for controllers. |
| `model.domain`                  | Internal domain model (records/enums), framework-free. |
| `model.dto`                     | External-facing request/response shapes, kept separate from domain objects. |
| `model.enums`                   | Shared enums: `TrainStatus`, `SectionType`, `DisruptionType`, `ConfidenceLevel`, `WeatherCondition`. |
| `railradar`                     | `TrainDataProvider` interface + the RailRadar-backed implementation. |
| `railradar.dto`                 | Raw RailRadar response shapes. Never leaves this subtree. |
| `railradar.mapper`              | Converts RailRadar DTOs into `model.domain` objects. |
| `route`                         | Estimated section-condition analysis (CLEAR / NORMAL / BUSY). |
| `weather`                       | `WeatherProvider` interface - `MockWeatherProvider`/`OpenMeteoWeatherProvider` (Phase 17). |
| `weather.openmeteo`             | Open-Meteo adapter: HTTP client, DTOs (own subpackage), mapper. Never leaves this subtree. |
| `historical`                    | `HistoricalDelayProvider` interface; mock now, PostgreSQL-backed later. |
| `railwaydisruption`             | `RailwayDisruptionProvider` interface - provider foundation only (Phase 18); nothing consumes it yet. See "Phase 18" below. |
| `simulation`                    | Disruption models, cascade engine, recovery model. |
| `prediction`                    | Travel time, delay, historical adjustment, confidence, and the overall prediction engine. |
| `repository`                    | Spring Data JPA repositories (added with PostgreSQL, later phase). |
| `exception`                     | Domain/application exceptions and centralized error handling. |

## Data classification

Every value produced by the system falls into exactly one category, and the
final `PredictionResult` must make clear which is which (field naming,
warnings, or documentation) rather than presenting everything as equally
authoritative:

- **Real data** — received as-is from an external source (e.g. RailRadar's
  reported delay).
- **Derived data** — calculated from real inputs (e.g. remaining distance /
  speed -> base travel time).
- **Simulated data** — produced by a disruption model (e.g. an assumed rain
  delay). This is a modelling exercise, not a measurement.
- **Mock data** — stand-ins used only during development/testing (e.g. the
  mock weather provider), replaced by real integrations in later phases.
- **Assumed data** — configuration/engineering assumptions (thresholds,
  weights) that haven't been empirically validated yet.

**Observed vs. simulated disruptions (Phase 18):** `RailwayDisruption` (a
reported operational fact - a TSR, engineering block, etc.) and
`DisruptionResult` (a `DisruptionModel`'s simulated hypothesis about delay)
are deliberately separate concepts, even though both eventually relate to
"why might this train be delayed." An observed disruption is not
automatically a delay estimate, and nothing in this application currently
converts one into the other - see the Phase 18 notes below.

## Current state (end of Phase 15)

- **Phase 2** added the framework-free internal domain model (`model.domain`,
  `model.enums`).
- **Phase 3** added the RailRadar adapter: `TrainDataProvider` (the only
  public entry point), `RailRadarClient` (WebClient-based HTTP call),
  `railradar.dto` (raw response shapes), and `LiveTrainDataMapper`
  (DTO → domain). This implements the real, documented
  `GET /v1/trains/{number}/live` endpoint from
  [railradar.in/docs/live-train-status](https://railradar.in/docs/live-train-status) —
  not a mock, since the real contract turned out to be publicly documented.

- **Phase 4** added `route`: `SectionAnalyzer` (`DelayBasedSectionAnalyzer` -
  estimates CLEAR/NORMAL/BUSY from the train's own delay against configurable
  thresholds) and `RouteProvider` (`LiveDataRouteProvider` - derives the
  current `RouteSection` from a train's current/next station; its
  `distanceKm` is left null since that single segment's length isn't
  derivable from `LiveTrainData` alone).

- **Phase 5** added `weather`: `WeatherProvider` and `MockWeatherProvider`,
  which returns one fixed, configurable reading tagged
  `source = "mock-provider"` for every location, until a real weather API is
  wired in (Phase 17).

- **Phase 6** added `historical`: `HistoricalDelayProvider` and
  `MockHistoricalDelayProvider`, which returns one fixed, configurable set of
  statistics (dated to "today" via an injected `Clock`) for every
  train/section, until PostgreSQL-backed historical data exists (Phase 16).
  Defaults to `sampleCount = 0` ("no historical data"), the honest default.

- **Phase 7** added `simulation`'s six `DisruptionModel` implementations:
  `HeavyRainModel` and `DenseFogModel` (certain if the actual `WeatherData`
  corroborates, otherwise a probability roll), `CongestionModel`,
  `EngineeringBlockModel`, `SignalHaltModel` (pure probability roll - no real
  corroborating data source exists yet for these three), and
  `SpeedRestrictionModel` (deterministic: computes delay from
  distance/speed when both are known, else a flat assumed fallback).
  Randomness is derived per-model from `SimulationContext.randomSeed()`
  (`DisruptionRandom`) so results are reproducible, never `Math.random()`.
  Orchestrating these together into one `SimulationResult` is
  `SimulationEngine`'s job - Phase 8, not this one.

- **Phase 8** added `SimulationEngine`: runs every registered `DisruptionModel`
  bean against a `SimulationContext` (Spring supplies the list automatically -
  the engine never names a concrete model), sums delay from whichever
  triggered, and produces one `SimulationResult`. Its cascade and recovery
  steps are explicit placeholders (zero effects, zero recovery) with
  `// TODO(Phase 9)` / `// TODO(Phase 10)` markers at the exact call sites
  those phases will fill in - not a claim that no cascading or recovery
  happens, just an honest gap pending those phases.

- **Phase 9** added `CascadeEngine`: given the total primary disruption delay,
  simulates a decaying chain of secondary effects (increased section
  occupancy propagating to a following train, then the next, each level
  inheriting a configured fraction of the previous level's delay), bounded by
  configurable max depth, max total cascade delay, and max affected entities.
  `SimulationEngine` now delegates to it for real, instead of the Phase 8
  placeholder. Necessarily a simulation - there's no real network-wide
  occupancy/headway feed to model this from.

- **Phase 10** added `RecoveryModel`: estimates how much of a train's
  accumulated delay (direct + cascade) can realistically be made up, as a
  configurable base fraction, halved (by default) under adverse weather
  (heavy rain/dense fog), and capped by remaining distance (or a flat
  fallback when distance is unknown). Bounded so recovery never exceeds the
  delay it's recovering from - the post-recovery delay can never go
  negative. `SimulationEngine` now delegates to it for real; with this,
  `SimulationEngine`'s full 7-step pipeline (disruptions → cascade →
  recovery → result) is complete.

- **Phase 11** added `prediction`: `TravelTimeCalculator`, `DelayCalculator`,
  `HistoricalDelayCalculator`, and `PredictionEngine`, which assembles the
  final `PredictionResult` from an already-computed `LiveTrainData`,
  `SectionType`, `SimulationResult`, and `HistoricalDelay`. The exact formula
  - and why it deliberately does *not* add current delay to the ETA, or
  subtract recovery twice - is documented with a worked example in
  [docs/prediction-model.md](prediction-model.md). `PredictionEngine` does not
  itself fetch live data or run the simulation - wiring the full pipeline
  together is Phase 13's job. Its `confidence` field is a labelled placeholder
  pending Phase 12's `ConfidenceCalculator`.

- **Phase 12** added `ConfidenceCalculator`: scores how much of a
  prediction's input data was actually available (live train data
  completeness, weather, historical data, route information, speed) plus
  whether the simulation was stable (few enough simultaneous disruptions),
  each a configurable weight; the score is the achieved weight as a
  percentage of total weight, bucketed into `ConfidenceLevel` via
  configurable thresholds. Section-condition reliability is a *structural*
  half-credit, not a per-request check - the analyzer (Phase 4) is delay-based
  only, never real occupancy data, so it's never fully trusted regardless of
  input. `PredictionEngine` now uses this for real; its `warnings()` are
  merged into the final `PredictionResult.warnings()`. This is explicitly
  **not** a statistically calibrated probability of accuracy - just a measure
  of input/model quality.

- **Phase 13** added `service.PredictionService` - the complete pipeline
  wiring every prior phase together: `TrainDataProvider` → `RouteProvider` →
  `WeatherProvider`/`HistoricalDelayProvider` → `SimulationEngine` +
  `SectionAnalyzer` → `PredictionEngine`. Live train data is essential (a
  `TrainDataProvider` failure propagates unchanged); weather and historical
  data are optional (a failure there is logged and degrades to "unavailable"
  rather than failing the request). A train with no next station raises
  `PredictionNotApplicableException` rather than fabricating a route section.
  The simulation's random seed is derived from the train's own observable
  state (train number, current station, current delay) - not a fixed
  constant, so the same real-world snapshot always simulates the same
  outcome, but the outcome changes as the journey progresses.

  Getting here required a real schema fix: `WeatherProvider` needs
  coordinates, but nothing before this phase carried any. `Station` gained
  optional `latitude`/`longitude` (backward-compatible - a 2-arg constructor
  still works, defaulting both to null), and `LiveTrainDataMapper` (Phase 3)
  now populates them from RailRadar's route data, which had the fields all
  along but they were previously discarded during mapping.

- **Phase 14** added `model.dto`'s output side: `PredictionOutput` and its
  nested `StationResponse`/`DisruptionResponse`/`CascadeEffectResponse`/
  `PredictionBreakdownResponse`/`SimulationResponse`/`ConfidenceResponse`,
  plus `PredictionOutputMapper` (`PredictionResult` → `PredictionOutput`) -
  a dedicated, stable JSON contract instead of serializing the domain model
  directly. Field-by-field data classification (real/derived/simulated/
  mocked/assumed) is documented in
  [docs/json-output.md](json-output.md). Getting `distanceFromOriginKm` into
  the output required one small, non-mathematical addition to
  `PredictionResult` itself (a passthrough field from `LiveTrainData`, not a
  new calculation).
  `PredictionOutputGenerationTest` wires the real pipeline (mock weather/
  historical providers, a fixed in-memory train) end to end and writes an
  example `prediction_output.json` to the project root - both this phase's
  integration test and its "generate an example" mechanism, since nothing
  else needed inventing to satisfy both at once.

- **Phase 15** added `controller.PredictionController` -
  `GET /api/v1/trains/{trainNumber}/prediction`, reusing `PredictionService`
  and `PredictionOutputMapper` unchanged. HTTP-level concerns only:
  `@Pattern`-validates the train number against RailRadar's own documented
  5-digit format before it can reach any provider, and
  `exception.GlobalExceptionHandler` (a `@RestControllerAdvice`, consistent
  with that package's purpose since Phase 1) maps every exception to one
  consistent error envelope (`timestamp`/`status`/`error`/`message`/`path`)
  with appropriate HTTP status codes - never a raw exception message or
  stack trace. OpenAPI/Swagger docs (springdoc) are generated from the
  controller's own annotations - see
  [docs/api-contract.md](api-contract.md) for the full request/response/error
  contract.

- **Post-Phase-15 audit fix**: an architecture audit found that
  `HistoricalDelay`, unlike `WeatherData`, carried no provenance/source tag -
  mock historical statistics were indistinguishable from real ones once a
  real `HistoricalDelayProvider` existed alongside the mock. Fixed minimally:
  `HistoricalDelay` gained a `source` field (mirroring `WeatherData.source()`
  exactly), `MockHistoricalDelayProvider` tags itself `DataProvenance.MOCK`,
  `PredictionService`'s degraded-mode fallback tags itself
  `DataProvenance.UNAVAILABLE`, and `ConfidenceCalculator` adds an explicit
  warning when historical data's source is mock (merged into
  `PredictionResult.warnings()`/`PredictionOutput.warnings` through the
  existing mechanism - no REST contract change, no ETA math change). A new
  `model.domain.DataProvenance` holds the shared `"mock-provider"`/
  `"unavailable"` constants so both mock providers and the calculator agree
  on the same vocabulary. See `docs/json-output.md` for the full behaviour.

- **Phase 16A** added PostgreSQL + Flyway - but only for a new, parallel
  concern: persisting raw historical station observations extracted from
  RailRadar's own `route[]` data (previously fetched and discarded, since
  Phase 3). It does **not** touch the prediction pipeline: `HistoricalDelay`/
  `HistoricalDelayProvider`/`MockHistoricalDelayProvider` are completely
  unchanged, ETA mathematics are unchanged, and the REST contract is
  unchanged.

  New domain type `model.domain.HistoricalObservation` (one raw fact: a
  train's arrival/departure at one station on one journey). New
  `railradar.mapper.HistoricalObservationMapper` extracts these from the
  same `LiveTrainStatusData` `RailRadarTrainDataProvider` already fetches for
  `LiveTrainDataMapper` - no second RailRadar call. New
  `repository.HistoricalObservationEntity`/`HistoricalObservationRepository`/
  `HistoricalObservationEntityMapper` handle JPA persistence, kept strictly
  behind explicit mapping (the domain record never touches JPA; the entity
  never leaks past the `repository` package). New
  `historical.HistoricalObservationRecorder` persists idempotently - one
  canonical row per (train number, journey date, station code, station
  sequence), found-then-updated rather than duplicated on repeated polling -
  and is entirely optional: `RailRadarTrainDataProvider` calls it best-effort
  (a failure is logged, never propagated), and it itself no-ops when no
  repository bean exists.

  **The database is opt-in, not on by default.** Adding `spring-boot-starter-
  data-jpa`/`postgresql`/`flyway-core` to the classpath would normally make
  Spring Boot try to establish a real DataSource at every startup; instead,
  `application.properties` excludes that auto-configuration outright, and a
  `postgres` Spring profile (`application-postgres.properties`) re-enables
  it. This means the application and its entire test suite behave
  identically whether or not PostgreSQL exists anywhere reachable - verified
  in this environment, which has neither Docker nor a local PostgreSQL
  server available. See docs/configuration.md for the full mechanism and
  docs/historical-data-design.md for the persistence/idempotency design.

- **Phase 16B** added `model.domain.HistoricalDelayProfile` - aggregated
  arrival-delay statistics (sample count, average, median, standard
  deviation, provenance) for one train at one **station** (not a full
  section - see docs/historical-data-design.md for why the raw data
  available couldn't yet reliably support a section-level delay-change
  statistic, and what was built instead). `historical.HistoricalDelayProfileAggregator`
  is pure statistics over `HistoricalObservation`s (no JPA, no RailRadar
  types); `historical.HistoricalDelayProfileRefresher` bridges it to a new
  `historical_delay_profiles` table (Flyway `V2`,
  `repository.HistoricalDelayProfileEntity`/`Repository`/`EntityMapper`,
  mirroring Phase 16A's pattern exactly) - a reproducible cache, never a
  second source of truth; raw observations remain authoritative.

  `historical.PostgresHistoricalDelayProvider` implements the **existing,
  unchanged** `HistoricalDelayProvider` interface - no interface change was
  needed, since it already took a `RouteSection`, which already carries the
  station the profile is keyed on. Selection between it and
  `MockHistoricalDelayProvider` is deterministic and explicit
  (`historical.provider=mock`, the default and today's active behaviour, or
  `postgres`), via `@ConditionalOnProperty` on both - exactly one
  `HistoricalDelayProvider` bean ever exists, and asking for `postgres`
  without the database configured fails startup loudly rather than
  silently serving mock data. **The prediction pipeline was not changed to
  depend on the new provider** - `PredictionService` is untouched, and
  production still runs on `MockHistoricalDelayProvider` unless someone
  explicitly opts in.

  A documented, not yet fixed, limitation: profile aggregation has no
  point-in-time cutoff (it uses every observation ever recorded), so
  today's profiles are only valid for "history as of right now" - not yet
  safe for backtesting a past prediction. See docs/historical-data-design.md
  for this and the full list of deliberately deferred scope (day-of-week/
  time buckets, probability-of-additional-delay, recovery-rate modelling,
  cross-train aggregation, recency weighting, ML).

- **Phase 16C** made the 16A/16B pipeline actually accumulate and refresh
  real data in a controlled way, without touching ETA math, the REST
  contract, or the station-level-only limitation above. Collection stays a
  best-effort side effect of the live-prediction RailRadar call (unchanged -
  a genuinely independent poller would need a "trains to track" registry
  that doesn't exist, and would burn extra RailRadar quota for no benefit).
  What's newly separated is **profile refresh**: a new
  `historical.HistoricalDelayProfileRefreshScheduler` (`@Scheduled`, gated
  by `historical.profile-refresh.interval-ms`/`initial-delay-ms` - see
  docs/configuration.md) is the only caller of
  `HistoricalDelayProfileRefresher` now, discovering which
  (trainNumber, stationCode) pairs to refresh via a new repository query,
  `HistoricalObservationRepository.findDistinctTrainStationKeys()`
  (`repository.TrainStationKey` projection). No messaging infrastructure was
  introduced - `@EnableScheduling` (`config.SchedulingConfig`) is the only
  new piece of plumbing.

  Re-inspecting `HistoricalObservationMapper` for this phase surfaced a real
  bug (not previously reported): a single malformed `RouteStop` could throw
  mid-`.stream().map()` and silently discard an entire batch of otherwise-
  good observations. Fixed by processing rows in an explicit loop with
  per-row validation (blank station code, impossible station sequence,
  implausible delay magnitude - configurable via
  `historical.observation-validation.max-plausible-delay-minutes`) plus a
  defense-in-depth try/catch around construction itself - one bad row can
  now only ever cost that one row. A new `historical.HistoricalDataMetrics`
  (plain `AtomicLong` counters, no Micrometer/Actuator - deliberately not a
  metrics stack) tracks observations received/rejected/inserted/updated,
  persistence failures, and profiles generated/refresh-failures.

  Re-inspecting the RailRadar DTOs also confirmed there is still no
  reliable *whole-journey* completion signal (the only candidate,
  `LiveTrainStatusData.status == "terminated"`, was always an unconfirmed
  Phase 3 guess) - this is documented as an open limitation rather than
  fabricated; Phase 16C only ever relied on the already-reliable
  station-level signal (`RouteStop.actualArrival() != null`).

  See docs/historical-data-design.md's "Phase 16C implementation notes" for
  the full validation-rules table, the collection/refresh separation
  reasoning, and the remaining limitations carried into Phase 16D.

- **Phase 16D** was a design-only investigation (no code changed) into
  whether `HistoricalObservation` could honestly support a section-level
  model. It found three concrete raw-data problems: `journeyDate` is
  assignment-time, not a verified journey identity (risking midnight-crossing
  fragmentation); origin stations were never persisted (the mapper required
  an arrival, which an origin never has); and `station_sequence` is nullable,
  making ordering ambiguous. **Phase 16E** below fixes/bounds all three.

- **Phase 16E** strengthens the raw-observation layer only - no section
  profile, aggregation, or ETA/prediction change. `HistoricalObservationMapper`'s
  inclusion filter changed from requiring an arrival to requiring *either* an
  arrival or a departure (`actualArrival() != null || actualDeparture() != null`),
  so an origin stop (departure only) is now eligible - nothing is fabricated
  to fill in its still-absent arrival side. A new
  `config.HistoricalJourneyDateProperties`
  (`historical.journey-date.operating-day-start-hour`, default `0` = no
  change) lets `journeyDate` follow an "operating day" convention (like
  GTFS's allowance for times past 24:00:00) so an observation just after
  local midnight can be attributed to the previous calendar date - reducing,
  not eliminating, the risk of one physical journey being split by polling
  that happens to straddle midnight. Re-inspection confirmed no RailRadar DTO
  carries a trustworthy journey-date field at all, so nothing is parsed or
  invented - `journeyDate` remains, and is now documented explicitly as, an
  observation-date. The natural key
  `(trainNumber, journeyDate, stationCode, stationSequence)` was reviewed
  against origin rows, null sequences, and repeated station codes and found
  **not** to need a schema change - no Flyway migration was added in this
  phase. One narrow, accepted limitation was found and documented rather than
  closed: two null-`stationSequence` rows for the same
  `(trainNumber, journeyDate, stationCode)` aren't rejected by a database
  unique constraint (SQL `NULL` is never equal to `NULL`), though the
  application-level idempotent upsert already handles the common,
  non-concurrent case correctly. See docs/historical-data-design.md's "Phase
  16D" and "Phase 16E implementation notes" for the full reasoning, the
  section-delay-change formula now documented for a future phase
  (`TO.arrivalDelayMinutes - FROM.departureDelayMinutes`, never mixing
  arrival and departure sides), and the remaining limitations carried into
  Phase 16F.

- **Phase 16F** implements that section-delay-change formula as a real,
  persisted profile - `model.domain.HistoricalSectionDelayProfile`
  (trainNumber, fromStationCode, toStationCode, sampleCount,
  average/median/standard-deviation of the delay *change*, source,
  computedAt) - without integrating it anywhere:
  `HistoricalDelayProvider`/`PredictionService`/`PredictionEngine`/ETA math
  are all untouched. `historical.HistoricalSectionDelayProfileAggregator` is
  a pure, framework-free component (no JPA, no RailRadar types, no
  `PredictionEngine` dependency, mirroring
  `HistoricalDelayProfileAggregator`'s own isolation) that takes every
  observation recorded for one train and an `Instant` cutoff, groups by
  `(trainNumber, journeyDate)` - the strongest journey identity the schema
  offers, explicitly *not* claimed to be a verified physical-journey
  identifier - sorts each group by `stationSequence` (never by station code,
  name, or list order), and pairs only genuinely adjacent, non-null-sequence
  observations; two observations sharing an identical sequence value make
  their whole journey group's ordering untrustworthy, so that group is
  excluded from pairing entirely rather than guessed at. A discovered
  adjacency with no usable delay figures still yields a
  `sampleCount = 0`/`DataProvenance.UNAVAILABLE` profile; one never
  discovered at all yields no profile. Provenance combines both endpoints'
  sources per sample, then combines every sample's source again for the
  profile as a whole - never silently collapsing a real/mock mix.

  Persistence mirrors the station-level profile exactly: new table
  `historical_section_delay_profiles` (Flyway `V3`), new
  `repository.HistoricalSectionDelayProfileEntity`/`Repository`/
  `EntityMapper`, natural key `(trainNumber, fromStationCode, toStationCode)`
  - raw section samples are never stored as a second copy of
  `HistoricalObservation`. `historical.HistoricalSectionDelayProfileRefresher`
  loads one train's full observation history
  (`HistoricalObservationRepository.findByTrainNumber`, new) and upserts
  every profile the aggregator discovers.
  `historical.HistoricalDelayProfileRefreshScheduler` (Phase 16C's existing
  `@Scheduled` component) was extended, not duplicated, to also iterate every
  distinct train number (`findDistinctTrainNumbers`, new) and refresh its
  section profiles, each in its own try/catch. No minimum-sample-count policy
  was introduced for section profiles - deliberately deferred until an actual
  consumer exists to need one (none does yet); reusing the station-level
  threshold's value was explicitly avoided, since a delay-*change* statistic
  is a difference of two independently-noisy quantities and is inherently
  noisier than either single station-level average for the same sample
  count. See docs/historical-data-design.md's "Phase 16F implementation
  notes" for the full pairing rules, temporal-cutoff semantics, and remaining
  limitations carried into Phase 16G (which is expected to decide how, if at
  all, section profiles should reach `HistoricalDelayProvider`).

- **Phase 16G** makes section profiles reachable through a clean domain
  abstraction, `historical.HistoricalSectionDelayProvider` - a **separate**
  interface from `HistoricalDelayProvider`, not an extension of it, because
  `HistoricalDelay` requires non-negative average/median delay (a constraint
  a delay-*change* statistic, which must allow negative values for
  recovery, cannot honour) and because `HistoricalDelayCalculator` (today's
  only `HistoricalDelayProvider` consumer) has no reason to understand
  section semantics. The contract
  (`getSectionDelay(trainNumber, RouteSection, Instant referenceInstant)`)
  returns a new `model.domain.SectionHistoricalDelayResult` wrapping a
  `model.domain.SectionHistoricalDelayStatus` (`AVAILABLE`,
  `INSUFFICIENT_SAMPLES`, `NOT_FOUND`) alongside the existing
  `HistoricalSectionDelayProfile` - never a JPA entity, RailRadar DTO, or
  bare/ambiguous null. A new, separately-configured
  `historical.section.minimum-sample-count` (default `10` - reasoned as
  roughly double the station-level default, since a delay-change statistic's
  variance is the *sum* of its two contributing quantities' variances, not
  reused from `prediction.historical-adjustment.minimum-sample-count`)
  decides `AVAILABLE` vs `INSUFFICIENT_SAMPLES`.

  The phase's central design question was point-in-time correctness:
  `historical_section_delay_profiles` is a single, continuously-overwritten
  row per section with no version history, so it can only safely answer a
  "now or later" request (at worst stale, never leaky). `PostgresSectionHistoricalDelayProvider`
  therefore reads that cache only for a "now or later" `referenceInstant`;
  for anything genuinely in the past, it bypasses the cache entirely and
  re-aggregates directly from `historical_observations` (via
  `HistoricalObservationRepository.findByTrainNumber` and
  `HistoricalSectionDelayProfileAggregator`, both already built in Phase
  16F) using the exact requested cutoff - proven by tests that plant a
  deliberately misleading cached row and confirm it is never used for a
  past-dated request. No schema change was needed - this was resolved
  entirely at the provider layer, deliberately preferred over profile
  versioning or a separate snapshot table. `MockSectionHistoricalDelayProvider`
  and `PostgresSectionHistoricalDelayProvider` are selected by a new,
  independent property, `historical.section-provider` (default `mock`),
  unrelated to the station-level `historical.provider` switch.

  **Nothing consumes `HistoricalSectionDelayProvider` yet** -
  `PredictionEngine`, `PredictionService`, `HistoricalDelayCalculator`, and
  the ETA formula are all untouched by this phase, deliberately. See
  docs/historical-data-design.md's "Phase 16G implementation notes" for the
  full contract, provenance policy, and the open questions left for Phase
  16H (how to combine a section-level adjustment with the existing
  station-level one without double-counting, how `RouteProvider`'s
  single-current-section limitation should be addressed, and what
  provenance-based acceptability policy prediction should apply).

- **Phase 16H-1** solves the first of those open questions:
  `route.RouteProvider` gained one new method,
  `remainingRoute(LiveTrainData)`, returning a new small domain type,
  `model.domain.RemainingRoute` (current station, destination station, an
  ordered list of `RemainingRouteSection`s, and a `RouteCompleteness` -
  `COMPLETE`/`PARTIAL`/`UNAVAILABLE`). This required `LiveTrainData` itself
  to carry two new fields (`destinationStation`,
  `List<RemainingRouteStop> remainingRouteStops`), since
  `TrainDataProvider.getLiveTrainData(...)` is the only seam through which
  RailRadar-derived facts reach the rest of the app - added via a second,
  backward-compatible constructor (mirroring `Station`'s own two-constructor
  shape) so every pre-existing caller is unaffected.
  `railradar.mapper.LiveTrainDataMapper` populates both fields by walking
  RailRadar's own `route[]` array from the current station onward, in the
  array's *own* already-trusted order (the same trust assumption it already
  made treating the array's last entry as the destination) - the walk
  **stops** at the first station whose code/name isn't usable, rather than
  skipping the gap and reconnecting past it, so no section is ever
  fabricated across an unconfirmed adjacency.

  A new, framework-free component,
  `historical.RemainingRouteHistoricalAggregator`, queries
  `HistoricalSectionDelayProvider` once per remaining section and produces a
  `model.domain.RemainingRouteHistoricalSummary`: every section's own result
  preserved individually, an explicit `RemainingRouteHistoricalStatus`
  (`NO_REMAINING_SECTIONS`/`ALL_SECTIONS_AVAILABLE`/`PARTIAL_SECTIONS_AVAILABLE`/
  `NO_SECTIONS_AVAILABLE`), and a `totalDelayChangeMinutes` that is the plain
  signed **sum** (never an average, never clamped, never scaled by an
  arbitrary weight) of only the `AVAILABLE` sections' own average delay
  change - `null`, not `0.0`, when nothing is available, so a genuine
  "historically stable" zero is never confused with "no data." Provenance is
  combined only across contributing sections and never upgraded/downgraded.

  **`PredictionEngine`, `PredictionService`, `HistoricalDelayCalculator`, and
  the ETA formula remain completely untouched.** The aggregate this phase
  produces is deliberately shaped so that Phase 16H-2 can make it the
  *replacement* for the existing station-level historical adjustment, not an
  *addition* alongside it - there must ultimately be exactly one historical
  contribution to a prediction. See docs/historical-data-design.md's "Phase
  16H-1 implementation notes" for the full reasoning and remaining
  limitations.

- **Phase 16H-2** performs that replacement. `PredictionEngine.predict(...)`
  gained one new parameter, an already-resolved
  `RemainingRouteHistoricalSummary` - it still never discovers route
  topology or queries a historical provider itself, only combines
  already-computed pieces, exactly as before. `PredictionService` gained the
  orchestration: it calls `RouteProvider.remainingRoute(train)` and
  `RemainingRouteHistoricalAggregator.summarize(...)` in a new private
  method, wrapped in the same try/catch-and-degrade pattern already used for
  weather and station-level history.

  A new `prediction.SectionHistoricalDelayCalculator` turns the summary into
  a candidate adjustment (`round(totalDelayChangeMinutes × weight)`, reusing
  the *same* `prediction.historical-adjustment.weight` - not a second,
  section-specific one) or `null` when nothing is usable.
  `PredictionEngine` uses that candidate as `historicalAdjustmentMinutes`
  whenever it's non-null (`ALL_SECTIONS_AVAILABLE` or, deliberately,
  `PARTIAL_SECTIONS_AVAILABLE` too - a partial, route-specific signal was
  judged more correct than a different, less relevant station-level
  statistic) and falls back to the existing, unchanged
  `HistoricalDelayCalculator` only when it's `null`
  (`NO_REMAINING_SECTIONS`/`NO_SECTIONS_AVAILABLE`) - never both summed
  together. The station-level historical path was deliberately *retained*
  as a fallback, not removed: section profiles require accumulated real
  observations to ever become `AVAILABLE`, so "section history unusable" is
  the common case under today's still-default mock configuration, and
  removing the fallback would have silently zeroed historical input for
  most predictions. New warnings disclose partial section coverage, a
  mock/mixed section provenance, and an explicit station-level fallback
  when sections existed but none were usable. A six-argument overload of
  `predict(...)` preserves exact pre-16H-2 behaviour for any caller that
  supplies no section summary.

  **Not yet touched, deliberately**: `ConfidenceCalculator` (at the time -
  fixed in Phase 16H-3 below), `PredictionResult`/`PredictionOutput`/the
  REST contract (likewise), and simulation (`SimulationEngine`,
  `CascadeEngine`, `RecoveryModel`, every disruption model - all untouched;
  `SimulationContext.historicalDelay()` remains unread by simulation itself,
  confirmed by inspection before this phase changed anything, and still
  true after 16H-3). See docs/historical-data-design.md's "Phase 16H-2
  implementation notes" and docs/prediction-model.md's own "Phase 16H-2"
  section for the complete replacement rule and warning matrix.

- **Phase 16H-3** closes the gap 16H-2 left: which of the two historical
  strategies actually won is now an explicit, machine-readable signal, not
  something a consumer had to infer from warning text. Two small,
  deliberately separate domain types -
  `model.domain.HistoricalAdjustmentSource` (`SECTION`/`STATION_FALLBACK`/
  `NONE` - *which strategy* won) and the existing `DataProvenance`
  vocabulary (*where that strategy's data* came from, unchanged, reused
  as-is) - are packaged as one small value object,
  `model.domain.HistoricalAdjustmentResolution(source, provenance)`, added
  to `PredictionResult` (a 21st field, via the same backward-compatible
  secondary-constructor pattern used for `LiveTrainData` in Phase 16H-1) and
  surfaced on the REST/JSON output as two new fields on
  `PredictionBreakdownResponse` - `historicalAdjustmentSource` and
  `historicalAdjustmentProvenance` - sitting directly alongside the existing
  `historicalAdjustmentMinutes`.

  `HistoricalDelayCalculator.historicalAdjustmentMinutes(...)` changed from
  returning `int` to `Integer` (`null` below the minimum sample count,
  mirroring `SectionHistoricalDelayCalculator`'s own contract) - without
  this, "insufficient samples" and "the real station-level average happens
  to be exactly zero" were indistinguishable (both `0`), making it
  impossible to correctly tell `STATION_FALLBACK` apart from `NONE`.
  `PredictionEngine`'s replacement policy and arithmetic are otherwise
  byte-for-byte unchanged - `resolveHistoricalAdjustment(...)` is a direct
  restatement of 16H-2's own `section → station → none` policy, now also
  returning which branch was taken.

  `ConfidenceCalculator.calculate(...)` now takes the resolved
  `HistoricalAdjustmentResolution` in place of the raw station-level
  `HistoricalDelay` (otherwise unused inside that method once this change
  was made) - its `historicalDataAvailability` factor now correctly
  recognises real section-sourced data even when the station-level lookup
  happens to be empty. This is a factual-accuracy correction to *which
  existing signal* decides an already-existing factor, not new confidence
  weights, thresholds, or scoring mathematics - both are unchanged and
  verified via full regression of the pre-existing `ConfidenceCalculatorTest`
  suite (rebuilt against the new parameter type, same assertions).

  See docs/historical-data-design.md's "Phase 16H-3 implementation notes"
  and docs/prediction-model.md's own "Phase 16H-3" section for the exact
  selection semantics and the full backward-compatibility story.

- **Phase 16H-4** answers the question 16H-3 left open: `SECTION` and
  `STATION_FALLBACK` now **provably score identically** in
  `ConfidenceCalculator` - a deliberate policy decision, not an omission.
  The reasoning: a real asymmetry between the two already exists and is
  already fully spent by the time either reaches this calculator - section
  data requires a higher minimum sample count to become usable at all
  (`historical.section.minimum-sample-count`, default `10`, vs.
  `prediction.historical-adjustment.minimum-sample-count`, default `5`,
  per Phase 16G's variance-of-a-difference reasoning) - so discounting
  `SECTION` again here would double-count a threshold already enforced
  upstream. No accuracy-tracking/backtesting data exists in this codebase
  from which any specific multiplier could be derived, so none was
  invented. Partial section coverage (`PARTIAL_SECTIONS_AVAILABLE`) is
  likewise left unscored, deliberately - `RemainingRouteHistoricalSummary`
  already exposes exactly how much of the route contributed, for a future
  phase to calibrate against once real evidence exists. Sample counts
  remain pure operational (usable/not-usable) gates, never an additional
  confidence input.

  The one genuine code change: the mock-provenance warning check widened
  from an exact `DataProvenance.MOCK` match to
  `provenance.contains(DataProvenance.MOCK)`, so a `mixed(...)` composite
  (partially mock-derived) is correctly flagged instead of silently passing
  as fully real - a factual-accuracy fix with **no effect on the score**;
  every pre-existing `ConfidenceCalculatorTest` assertion continued to pass
  completely unchanged, proving the weights/thresholds/formula are
  byte-for-byte identical to before this phase. See
  docs/historical-data-design.md's "Phase 16H-4 implementation notes" and
  docs/prediction-model.md's own "Phase 16H-4" section for the full policy
  table and what evidence would be required to revisit it.

- **Phase 16H-5** builds the foundation for actually collecting that
  evidence - a prediction-accuracy-tracking/backtesting system, disabled by
  default (`prediction.evaluation.enabled=false`) and entirely offline. It
  changes no prediction mathematics whatsoever: this phase *measures* the
  existing model, it does not *optimize* it.

  **Prediction target**: the train's *next station*, not the destination -
  RailRadar exposes no reliable whole-journey-completion signal (Phase
  16H-1), so only the next-station arrival (the same completion signal
  `HistoricalObservationRecorder` already trusts) can be evaluated honestly.
  The evaluated predicted value,
  `predictedNextStationDelayMinutes = currentDelayMinutes + predictedExtraDelayMinutes`,
  is deliberately *not* `predictedTotalDelayMinutes` (destination-scoped,
  includes a historical adjustment whose spatial scope may exceed the next
  station). The actual outcome is RailRadar's own `arrivalDelayMinutes` -
  never a derived "actual ETA," since the raw schedule/actual-time strings
  remain unparsed opaque strings (unchanged, standing limitation since
  Phase 16A).

  New: `model.domain.PredictionSnapshot` (a minimal, audit-sized capture of
  one prediction - not a copy of the full live payload), `PredictionEvaluationStatus`
  (`PENDING`/`EVALUATED_EXACT`/`EVALUATED_APPROXIMATE`/`NOT_EVALUABLE` -
  journey-identity uncertainty is represented explicitly, never hidden),
  `PredictionAccuracyMetrics` (MAE/RMSE/bias) and
  `PredictionAccuracyComparison` (current model vs. a zero-infrastructure
  "current-delay-only" baseline). New persistence: `prediction_snapshots`
  (Flyway `V4`) plus its entity/repository/mapper. New
  `com.railpredictor.evaluation` package: `PredictionSnapshotRecorder`
  (best-effort, mirrors `HistoricalObservationRecorder`),
  `PredictionOutcomeMatcher` (pure - leakage prevented by requiring
  `observedAt` strictly after the prediction moment, proven directly by
  tests), `PredictionEvaluationRefresher`/`PredictionEvaluationRefreshScheduler`
  (offline, `@Scheduled`, mirroring `HistoricalDelayProfileRefresher`'s own
  split between pure logic and persistence-aware orchestration), and
  `PredictionAccuracyCalculator`/`PredictionAccuracyReportBuilder`.

  `PredictionService` gained one best-effort call
  (`predictionSnapshotRecorder.recordSafely(result)`) after building the
  result - never inside the critical path's error handling, never affecting
  what's returned. See docs/historical-data-design.md's "Phase 16H-5
  implementation notes" and docs/prediction-model.md's own "Phase 16H-5"
  section for the complete design, including the documented asymmetry
  between `SECTION` and `STATION_FALLBACK` history's own retrospective-
  reconstruction readiness.

- **Phase 16H-6** makes the Phase 16H-5 accuracy-tracking foundation
  observable: a read-only `GET /api/v1/evaluation/accuracy` endpoint (new
  `EvaluationController`) reporting MAE/RMSE/bias for the model vs. the
  current-delay-only baseline, broken down by exact/approximate evaluation,
  by `HistoricalAdjustmentSource`, and by data provenance. It changes no
  prediction mathematics, evaluation logic, or the offline
  refresh/recording pipeline - it only reads already-evaluated snapshots.

  New: `model.domain.PredictionAccuracyReport`/`PredictionAccuracySlice`
  (each slice carries `sampleCount`/`exactCount`/`approximateCount` together
  with its own `PredictionAccuracyComparison`, so combined metrics are never
  shown without also showing how much of the sample was only an
  approximate journey match) and `PredictionAccuracyReportFilter` (a plain,
  framework-free predicate over train number / station code / prediction
  time range / historical-adjustment source / evaluation status).
  `PredictionAccuracyReportBuilder` (Phase 16H-5) gained one additive method,
  `buildReport(...)`, reusing its existing `build(...)`/
  `PredictionAccuracyCalculator` unchanged for every slice. New
  `evaluation.PredictionAccuracyReportService` loads snapshots via the
  existing `findByEvaluationStatusIn` query (no new repository method or
  Flyway migration - the evaluated-snapshot volume this phase expects is
  small enough that in-memory filtering is clearly sufficient, a decision
  documented in docs/prediction-model.md's own "Phase 16H-6" section) and
  applies the filter in memory; it returns `Optional.empty()` when
  `prediction.evaluation.enabled=false`, an explicit disabled state the new
  `model.dto.AccuracyReportMapper`/`AccuracyReportResponse` render as
  `evaluationEnabled: false` with every other field `null` - `200 OK`, never
  a fabricated empty dataset and never a 500. Two small additions to
  `exception.GlobalExceptionHandler`: a handler for
  `InvalidAccuracyFilterException` (new - an unsatisfiable filter, e.g. an
  inverted time range) and one for Spring's
  `MethodArgumentTypeMismatchException` (an unparseable enum/timestamp query
  parameter), both mapping to `400 INVALID_FILTER` rather than the generic
  500 they'd otherwise fall through to.

  See docs/api-contract.md's "Prediction Accuracy Report" section for the
  full request/response contract and docs/prediction-model.md's own "Phase
  16H-6" section for the exact/approximate and filtering-scope design
  reasoning.

- **Phase 16H-7** hardens the Phase 16H-5/16H-6 evaluation infrastructure
  itself and answers the question those phases left open: is this system
  ready for retrospective backtesting? It changes no prediction
  mathematics.

  New `model.domain.PredictionEvaluationMode` (`LIVE_EVALUATION` /
  `HISTORICAL_BACKTEST`) makes explicit which process produced a
  `PredictionSnapshot` - every snapshot any code path currently creates is
  `LIVE_EVALUATION` (the only value `PredictionSnapshotRecorder` ever
  stamps); `HISTORICAL_BACKTEST` is reserved, not fabricated. Added as
  `PredictionSnapshot`'s 17th field via this codebase's standard
  backward-compatible-secondary-constructor pattern, with a matching
  `evaluation_mode` column on `PredictionSnapshotEntity` (Flyway `V5`,
  `NOT NULL DEFAULT 'LIVE_EVALUATION'`) added the same way - no existing
  call site needed to change. `PredictionOutcomeMatcher` now explicitly
  carries `evaluationMode` through unchanged when it reconstructs a
  snapshot (a real bug risk caught and fixed this phase: the old
  constructor shape would have silently reset it to the default).
  `PredictionAccuracyReportBuilder.buildReport(...)` gained a
  `byEvaluationMode` breakdown (always both keys) so live-evaluation and
  any future backtest evidence can never be silently blended.

  `HistoricalDelayProfileAggregator` gained a cutoff-aware
  `aggregate(trainNumber, stationCode, observations, referenceInstant)`
  overload, mirroring `HistoricalSectionDelayProfileAggregator`'s own
  inclusive-cutoff convention exactly (Phase 16F) - closing the specific
  "no cutoff parameter at all" gap Phase 16H-5 documented for
  `STATION_FALLBACK`. The pre-existing 3-argument overload is unchanged,
  delegating to the new one with "now" - `HistoricalDelayProfileRefresher`
  needed no changes. This closes the gap at the pure-aggregation layer
  only: `PostgresHistoricalDelayProvider` still has no on-demand
  past-cutoff re-aggregation path the way
  `PostgresSectionHistoricalDelayProvider` already does (Phase 16G) -
  extending that would touch `HistoricalDelayProvider`'s interface and its
  live caller, `PredictionService`, deliberately out of this phase's scope.

  **Backtest readiness assessment: `NOT_READY`.** Even with both historical
  aggregators now cutoff-capable, true historical replay would additionally
  require reconstructing a train's live position/delay/route/weather as of
  an arbitrary past instant - none of which this application archives
  (only current live state is ever fetched). This phase explicitly
  concludes "live evaluation is production-capable; full historical replay
  remains unavailable" rather than fabricating a partial backtest
  capability - see docs/historical-data-design.md's and
  docs/prediction-model.md's own Phase 16H-7 sections for the full
  input-by-input reasoning.

  `PredictionEvaluationRefresher.evaluatePendingSnapshots()`'s idempotency
  (already true by construction since Phase 16H-5 - it only ever touches
  rows the repository reports as `PENDING`) is now proven by a direct
  two-call test rather than left as an inference. No new configuration was
  added (reviewed `prediction.evaluation.*`/`historical.profile-refresh.*`
  - no lookback/retention setting is genuinely needed yet). One migration
  (`V5`), one new column, no schema redesign.

No live weather or model-formula changes exist yet from any of this
accuracy-tracking/reporting work — those remain future phases.

- **Phase 17** replaces `MockWeatherProvider` as the default-capable
  weather source with a real integration - **Open-Meteo**
  (https://open-meteo.com), chosen after checking current provider
  capabilities: no API key needed for non-commercial use, `precipitation`/
  `visibility`/`temperature_2m` already in this application's own domain
  units, a genuine per-reading observation timestamp, and a plain HTTP
  GET/JSON contract compatible with the existing `WebClient` pattern.
  `HeavyRainModel`/`DenseFogModel`/every other disruption model, the ETA
  formula, and confidence scoring are byte-for-byte unchanged - this phase
  integrates a data source, it does not touch the model.

  New `weather.openmeteo` package, mirroring `railradar`'s own structure:
  `OpenMeteoClient` (package-private, WebClient-based, one configured
  timeout bounding the whole request, every failure mapped to a single new
  `WeatherUnavailableException` - `PredictionService`'s existing weather
  try/catch already handles it identically to a mock failure, no changes
  needed there), `OpenMeteoWeatherMapper` (external DTOs, in their own
  `dto` subpackage, never escape this package), and
  `OpenMeteoWeatherProvider` (`@ConditionalOnProperty(weather.provider=
  openmeteo)`, mirroring `MockHistoricalDelayProvider`/
  `PostgresHistoricalDelayProvider`'s exact deterministic-selection
  pattern - `MockWeatherProvider` gained the matching
  `@ConditionalOnProperty(weather.provider=mock, matchIfMissing=true)` it
  never needed before, since it was previously the only
  `WeatherProvider` bean).

  Classifying a raw reading into `WeatherCondition` is new (Phase 17) -
  primarily from Open-Meteo's own WMO weather code (an international
  standard); the one genuinely new threshold,
  `weather.openmeteo.dense-fog-visibility-meters` (default 200m, the India
  Meteorological Department's own "dense fog" definition), only exists
  because the WMO fog code itself carries no light/dense distinction. Unit
  conversion: none needed - Open-Meteo's default units already match this
  application's, verified directly by test.

  `model.domain.WeatherData` gained `observedAt` (the reading's own valid-
  for instant, `null` for mock - never fabricated) via the same
  backward-compatible-secondary-constructor pattern used throughout this
  codebase. `model.domain.PredictionResult`/`PredictionSnapshot` each
  gained `weatherProvenance` (the weather source at prediction time, or
  `null`) for audit only - deliberately not exposed in the REST output
  (`confidence.contributingFactors` already surfaces weather availability
  unchanged) and not yet broken out in the accuracy report (no weather-
  provenance experiment framework was built - explicitly out of scope).
  `PredictionOutcomeMatcher` now also carries `weatherProvenance` through
  evaluation unchanged, alongside `evaluationMode` (Phase 16H-7) - the same
  "the snapshot is the audit record" principle. One migration (`V6`), one
  new nullable column (`weather_provenance`) - no schema redesign.

  Location was already solved: `Station.latitude()`/`longitude()` have
  carried real RailRadar-derived coordinates since Phase 13 specifically
  for this purpose - no new location-resolution capability was needed.

  See docs/prediction-model.md's and docs/configuration.md's own Phase 17
  sections for the full provider-selection, classification, freshness, and
  provenance design.

- **Phase 18** establishes a provider foundation for *real* railway
  operational disruption data (temporary speed restrictions, engineering
  blocks, signal failures, congestion, diversions) - evidence-first, per
  this phase's own instructions. It changes no ETA mathematics, no
  historical weighting, no confidence formula, and does not wire anything
  into `PredictionEngine`.

  **Source research (the actual work of this phase).** Investigated, in
  order: (1) NTES (`enquiry.indianrail.gov.in`) - the official real-time
  train-status system, but a website/app, not a documented public API with
  structured causal fields (only end-user-facing delay/cancellation/
  diversion text); (2) CRIS's own API infrastructure ("Project Pravah",
  `crisapis.indianrail.gov.in`) - confirms TSR data genuinely exists
  digitally inside Indian Railways' internal systems (COA/FOIS/ICMS), but
  flows through a controlled, partner-only gateway with no public
  self-service registration, published terms, pricing, or rate limits
  found - inaccessible to an independent project without a formal
  partnership; (3) data.gov.in's railways sector - hosts static/historical
  reference datasets (timetables, station lists, statistics), not a live
  operational-disruption feed; (4) RailRadar (already integrated for live
  train status) - its own documentation exposes delay minutes and
  "diversion alerts" only, no structured TSR/engineering-block/
  signal-failure fields; (5) community GitHub/Kaggle datasets - static
  historical delay/timetable data, unofficial and not real-time.
  **Conclusion: no reliable, publicly accessible, machine-readable,
  appropriately-licensed source of real-time Indian Railways operational
  disruption data was found.** No provider was selected; no data was
  fabricated.

  New `model.domain.RailwayDisruption` (an *observed* operational fact -
  deliberately no `delayMinutes` field, since an observation is not
  automatically a delay estimate) with its own `RailwayDisruptionType`
  vocabulary (TSR/engineering-block/signal-failure/congestion/diversion/
  maintenance-block/other) and `RailwayDisruptionStatus`
  (ACTIVE/FUTURE_EFFECTIVE/EXPIRED/UNKNOWN_VALIDITY - an undated disruption
  is never assumed active), computed by the pure
  `railwaydisruption.RailwayDisruptionStatusClassifier`.
  `RailwayDisruptionQueryResult` distinguishes
  `RailwayDisruptionAvailability.AVAILABLE` (queried successfully - an
  empty list is a real "nothing currently reported" fact) from
  `UNAVAILABLE` (couldn't determine - never to be read as "clear route"),
  enforced by its own constructor (an `UNAVAILABLE` result can never carry
  disruptions).

  New `railwaydisruption.RailwayDisruptionProvider` interface
  (`getDisruptions(trainNumber, RouteSection, referenceInstant)`, mirroring
  `HistoricalSectionDelayProvider`'s exact shape and (train, station-pair)
  matching key - no new section-identity concept invented). Two
  implementations, deterministically selected via
  `railway-disruption.provider` (mirroring every other provider-selection
  switch in this codebase): `UnavailableRailwayDisruptionProvider` (the
  default - the honest reflection of this phase's research finding) and
  `MockRailwayDisruptionProvider` (opt-in only, for local testing/
  demonstration - deliberately **not** the default the way
  `MockWeatherProvider`/`MockHistoricalDelayProvider` are for their own
  domains, since there is no real provider to eventually default away
  from).

  **Nothing consumed this yet as of Phase 18** - mirroring
  `HistoricalSectionDelayProvider`'s own Phase 16G precedent of a
  fully-built, fully-tested provider abstraction with zero prediction-path
  wiring for one full phase before anything reads from it. Phase 19 (below)
  is that next phase, once the disruption-to-delay policy itself existed
  and was tested in isolation first.

- **Phase 19** builds the policy boundary Phase 18 deliberately deferred:
  translating a real/mock `RailwayDisruption` into a predicted delay
  contribution, and wires the result into the live pipeline - still without
  touching the ETA formula, historical weighting, confidence mathematics,
  or any of the six existing simulation models' own logic.

  **Evidence check, first.** This codebase has zero real evaluated
  disruption-to-delay outcomes (evaluation has never been enabled in a real
  deployment - see Phase 16H-5/16H-7/17's own notes) - there is no data to
  derive a calibrated coefficient from for any disruption type. New
  `model.domain.CalibrationStatus` (`INSUFFICIENT_DATA`/`CALIBRATED`,
  mirroring `PredictionEvaluationMode`'s "one value always produced today,
  one reserved" precedent) makes this an explicit, machine-readable fact
  rather than an implicit assumption - every `DisruptionImpactPolicy`
  result this phase produces reports `INSUFFICIENT_DATA`.

  New `disruptionimpact` package - the policy boundary itself, deliberately
  separate from both `railwaydisruption` (data access, Phase 18) and
  `simulation` (the six probabilistic models): `DisruptionImpactPolicy`
  (`evaluate(RailwayDisruption, RouteSection, LiveTrainData)` →
  `model.domain.DisruptionImpact` - a per-disruption result, never a bare
  int), `HeuristicDisruptionImpactPolicy` (the only implementation - its
  name says what it is), and `DisruptionImpactAggregator` (temporal
  filtering via `RailwayDisruptionStatusClassifier`, de-duplication, and
  bounded summation into one `model.domain.DisruptionImpactAssessment` per
  section).

  **Per-type evaluation, not one shared formula.**
  `TEMPORARY_SPEED_RESTRICTION` is the only type with a physically-grounded
  (not fabricated) computation - basic kinematics
  (`distance/restrictedSpeed - distance/normalSpeed`) using the
  disruption's own reported speed, the train's own current speed, and the
  section's own distance, mirroring `SpeedRestrictionModel`'s existing
  formula for the identical physical relationship; any required input
  missing, zero, or non-restrictive yields
  `DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE`, never a fabricated
  number. `ENGINEERING_BLOCK`/`SIGNAL_FAILURE`/`CONGESTION` each use their
  own configurable flat default (`railway-disruption-impact.*-default-
  delay-minutes`) - explicit, provisional heuristics, not derived from
  evidence; `severity` is captured on `RailwayDisruption` for audit but
  deliberately not used to scale these defaults (no reliable
  source-guaranteed vocabulary to interpret). `ROUTE_DIVERSION`/
  `MAINTENANCE_BLOCK`/`OTHER` have no impact rule at all yet -
  always `PRESENT_BUT_NOT_ESTIMABLE`.

  **Real disruptions and simulation are mutually exclusive per type, never
  additive for the same cause.** New `disruptionimpact.SimulationSuppression`
  maps each active real `RailwayDisruptionType` to its simulated
  `DisruptionType` counterpart (`TEMPORARY_SPEED_RESTRICTION`→
  `SPEED_RESTRICTION`, `ENGINEERING_BLOCK`→`ENGINEERING_BLOCK`,
  `SIGNAL_FAILURE`→`SIGNAL_HALT`, `CONGESTION`→`HIGH_CONGESTION`) and
  `PredictionService` disables the matching `SimulationContext.*Enabled`
  flag for that run - regardless of whether this policy could actually
  estimate a delay figure for the real one (a confirmed real signal failure
  makes the simulated "what if" meaningless either way). `HEAVY_RAIN`/
  `DENSE_FOG` (weather-driven) and the three unrecognized types are never
  suppressed. With the default configuration
  (`railway-disruption.provider=unavailable`), no type is ever suppressed
  and every simulated model behaves exactly as it did before this phase -
  proven by the full pre-existing regression suite passing unchanged.

  **Arithmetic**: `DisruptionImpactAssessment.additionalDelayMinutes()`
  (non-null only for `NO_KNOWN_DISRUPTION`, always `0`, and `ESTIMATED`) is
  added to `predictedTotalDelayMinutes`/`predictedEta` as its own fourth
  term in `PredictionEngine`, alongside `historicalAdjustmentMinutes` -
  never routed through `predictedExtraDelayMinutes` (the simulated
  contribution, already netted against recovery inside
  `SimulationEngine`) or `recoveryMinutes`. `DATA_UNAVAILABLE`/
  `PRESENT_BUT_NOT_ESTIMABLE` contribute `0` to the arithmetic (never
  fabricated) but remain visible via `PredictionResult.warnings()` and
  the full `disruptionImpactAssessment` audit field.

  **Aggregation** (`DisruptionImpactAggregator`): only
  `RailwayDisruptionStatusClassifier`-confirmed `ACTIVE` disruptions are
  ever evaluated (future/expired/undated ones are treated as
  `NO_KNOWN_DISRUPTION`); disruptions sharing the same (type, trainNumber,
  fromStationCode, toStationCode) identity are de-duplicated to the
  most-recently-observed one; distinct types/estimates are summed, capped
  at `railway-disruption-impact.max-aggregate-delay-minutes`
  (`cappedByMaximumAggregate` surfaces whenever this actually clamps
  something).

  **Calibration foundation**: `PredictionResult`/`PredictionSnapshot` each
  gained `disruptionImpactMinutes`/`disruptionImpactAssessment` (Phase 19) -
  the minutes actually added, or `null` when unavailable/not-estimable -
  via the same backward-compatible-secondary-constructor pattern used
  throughout this codebase. `PredictionOutcomeMatcher` carries it through
  evaluation unchanged, alongside `evaluationMode`/`weatherProvenance`. No
  accuracy-report breakdown by this field exists yet - the column only
  makes a future one possible without a backfill. One migration (`V7`),
  one new nullable column - no schema redesign.

  New configuration: `railway-disruption-impact.*` (see
  docs/configuration.md) - every value documented as heuristic, with
  validated bounds (`max-aggregate-delay-minutes` must not be less than
  `max-single-disruption-delay-minutes`). Two config-registration classes
  (`RailwayDisruptionConfig`, `DisruptionImpactConfig`) were added this
  phase - a genuine Phase 18 gap (its own `RailwayDisruptionMockProperties`
  was never registered via `@EnableConfigurationProperties`, harmless only
  because nothing forced that bean's creation until this phase's own
  `HeuristicDisruptionImpactPolicy` unconditionally needed
  `DisruptionImpactProperties`) fixed in passing.

  See docs/prediction-model.md's own Phase 19 section for the full
  evidence-vs-heuristic accounting, and docs/configuration.md for the new
  properties.

- **Phase 20** adds an empirical calibration/ablation evaluation framework
  (new `evaluation` package classes: `DataQualityAssessor`,
  `ErrorDistributionCalculator`, `WeatherContributionEvaluator`,
  `SimulationContributionEvaluator`, `ConfidenceCalibrationAuditor`,
  `ChronologicalSplitter`, `HistoricalWeightCalibrationAssessor`,
  `DisruptionImpactCalibrationAssessor`, `CalibrationEvaluationReportBuilder`,
  `CalibrationEvaluationService`) - deliberately separate from, and built
  entirely on top of, the existing Phase 16H-5/16H-6 accuracy-reporting
  classes, which needed no changes. No ML, no auto-optimization, no schema
  redesign, no new migration.

  **Central finding**: tracing `PredictionSnapshot`'s own arithmetic shows
  `historicalAdjustmentMinutes`/`disruptionImpactMinutes` (Phase 19) only
  ever feed `predictedTotalDelayMinutes`, never the evaluated
  `predictedNextStationDelayMinutes` - so calibrating either one against
  this codebase's evaluated metric is *structurally* impossible, not merely
  data-starved. New enum `CalibrationBlockerReason` (`NONE`/
  `INSUFFICIENT_SAMPLE_SIZE`/`METRIC_STRUCTURALLY_UNAFFECTED`/
  `MOCK_DATA_ONLY`/`TRAINING_VALIDATION_OVERLAP`) makes this an explicit,
  reportable fact rather than an implicit assumption. `CalibrationStatus`
  (Phase 19's 2-value enum) is extended to `NOT_CALIBRATED`/
  `INSUFFICIENT_DATA`/`PROVISIONALLY_CALIBRATED`/`VALIDATED`.

  Weather's and simulation's own contributions *are* measurable against the
  evaluated metric (indirectly via `weatherProvenance`, directly via the
  derived `predictedExtraDelayMinutes`) - `WeatherContributionEvaluator`/
  `SimulationContributionEvaluator` build the two real ablation breakdowns
  this phase produces, always as a paired comparison on one population,
  never two differently-sourced ones.

  `GET /api/v1/evaluation/accuracy` gains one new nullable field,
  `calibrationEvaluation`, populated only when both
  `prediction.evaluation.enabled` and `evaluation.calibration.enabled` are
  `true`. New config: `evaluation.calibration.*` (4 keys - see
  docs/configuration.md).

  See docs/prediction-model.md's own Phase 20 section for the full
  methodology, and the Phase 20 completion report for the actual real-data
  sample sizes this environment had available (zero - `prediction.evaluation.enabled=false`
  by default, no `postgres` profile active in any verification run this
  phase performed).

- **Phase 21** corrects the exact structural gap Phase 20 found:
  `predictedNextStationDelayMinutes` (the only evaluated metric) never
  included historical adjustment or disruption impact, and the existing
  section-level historical adjustment (Phase 16H-2) is itself scoped to the
  whole remaining route (a correct input for the destination-scoped
  `predictedTotalDelayMinutes`, but wrong for a next-station value). Fix:
  `PredictionEngine` now computes a *separate*, correctly next-station-
  scoped `nextStationHistoricalAdjustmentMinutes` (using only
  `sectionResults().get(0)`, the immediate section, never the whole-route
  sum) and folds it - plus the already-correctly-scoped
  `disruptionImpactMinutes` - into a new, authoritative
  `predictedNextStationDelayMinutes` field on `PredictionResult`.
  `PredictionSnapshotRecorder` now reads this field directly rather than
  recomputing it as `currentDelayMinutes + predictedExtraDelayMinutes`
  (the old formula, and the exact reason historical/disruption could never
  enter the evaluated metric before this phase).

  Migration `V8` adds three columns to `prediction_snapshots`:
  `next_station_historical_adjustment_minutes`/`_source`/`_provenance`
  (the new next-station-scoped historical figure) and
  `predicted_extra_delay_minutes` (simulation's own raw contribution,
  persisted directly for unambiguous future ablation). Phase 20's
  `HistoricalWeightCalibrationAssessor`/`DisruptionImpactCalibrationAssessor`
  no longer report `METRIC_STRUCTURALLY_UNAFFECTED` - both parameters now
  genuinely affect the evaluated metric - but still report
  `INSUFFICIENT_DATA` (via `INSUFFICIENT_SAMPLE_SIZE`/`MOCK_DATA_ONLY`)
  since zero real snapshots exist and no candidate-search algorithm is
  implemented yet. `PredictionBreakdownResponse` (REST) gains a nullable-
  compatible `predictedNextStationDelayMinutes` field.

  See docs/prediction-model.md's own Phase 21 section for the full
  before/after arithmetic and exactly why disruption impact needed only a
  wiring fix while historical adjustment needed a genuine scope correction.
