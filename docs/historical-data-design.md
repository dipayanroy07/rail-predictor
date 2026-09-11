# Historical Data Design (Phase 16A/16B/16C/16E/16F/16G/16H-1 through 16H-5 implemented; 16D was design-only)

**Update - Phases 16A, 16B, 16C, 16E, 16F, 16G, 16H-1, 16H-2, 16H-3, 16H-4 and 16H-5 are now
implemented; Phase 16D was a design-only investigation whose findings drove all of them.**
This document originally described a full
proposal before any of it was built. The raw-observation layer (§1-4, §6-7's "essential" column)
was implemented in Phase 16A: `historical_observations` is a real table, real `RouteStop` data
flows into it. Phase 16B then implemented the **station-level** aggregated profile
(`HistoricalDelayProfile`) and a real, opt-in `HistoricalDelayProvider` - but deliberately
**not** the richer section-level/day-of-week/time-bucket/probability/recovery design originally
sketched in §2-3 below; see "Phase 16B implementation notes" for exactly what was built instead
and why. `MockHistoricalDelayProvider` remains the *default*-active provider - the prediction
pipeline does not depend on real historical data unless `historical.provider=postgres` is
explicitly set.

## Phase 16A implementation notes (what's actually built)

- **Table name**: `historical_observations`, not `train_journey_observations` as originally
  sketched in §3 - named to match the domain type (`HistoricalObservation`) introduced for it.
  Columns match §3's raw table exactly otherwise (see
  `db/migration/V1__create_historical_observations.sql`).
- **Raw observation domain type**: `com.railpredictor.model.domain.HistoricalObservation`, matching
  §2's sketch, with one deliberate simplification: `scheduledArrival`/`actualArrival`/
  `scheduledDeparture`/`actualDeparture` are kept as RailRadar's own raw `String`s, not parsed into
  `Instant`. Their exact format/timezone was never confirmed against a real response (no API key
  available), and inventing a parsing scheme for an unconfirmed format risked silently wrong data
  more than it helped - deferred until the format is confirmed.
- **Extraction point**: `railradar.mapper.HistoricalObservationMapper`, fed the exact same
  `LiveTrainStatusData` already fetched by `RailRadarTrainDataProvider` for `LiveTrainDataMapper` -
  confirming §4's finding that no second RailRadar call is needed.
- **Idempotency**: implemented as application-level "find by natural key, then update-in-place or
  insert" (`HistoricalObservationRecorder`), backed by a DB `UNIQUE(train_number, journey_date,
  station_code, station_sequence)` constraint as a concurrency backstop - not the native
  `INSERT ... ON CONFLICT` originally sketched in §4's SQL example. The portable JPA approach was
  chosen deliberately: it needs no Postgres-specific SQL, which is also what makes the H2-based
  repository test suite (see docs/configuration.md) viable without Docker.
- **`journey_date`** = "today" per the app's own `Clock` at observation time, exactly as §4
  anticipated (RailRadar's optional `date` query parameter is never sent by `RailRadarClient`, so
  its response always means "today" server-side).
- **Persistence is entirely optional and best-effort**: gated behind the `postgres` Spring
  profile (see docs/configuration.md); when inactive, `HistoricalObservationRecorder` receives no
  repository and no-ops. Even when active, a recording failure is caught in
  `RailRadarTrainDataProvider` and logged - it can never break a live prediction request.

## Phase 16B implementation notes (what's actually built)

### The sample-unit decision: station-level, not section-level

The original proposal's `HistoricalDelayProfile` (§2) was section-keyed (`fromStation` +
`toStation`) with a `delayChangeMinutes` statistic (arrival delay at `to` minus departure delay
at `from`). **This was not implemented.** Two things this proposal glossed over, on closer
inspection of the actual Phase 16A data:

1. `HistoricalObservationMapper` only persists a row when `actualArrival != null` - it never
   filters on `delayArrival`/`delayDeparture` being non-null too. Real RailRadar responses may
   well leave those unset even for a station the train has passed; without a real API key to
   check, assuming both endpoints of a section reliably carry usable delay figures would be
   guessing.
2. A section-level statistic requires joining two observation rows for the *same*
   (trainNumber, journeyDate) - straightforward in principle, but building and trusting that join
   without having ever seen real RailRadar delay data behind it risked exactly the "do not
   fabricate a section-level number" outcome the brief warned against.

So, per the brief's own explicit fallback: **the implemented profile is station-level.** One
sample = one `HistoricalObservation` row for a given (trainNumber, stationCode) with a non-null
`arrivalDelayMinutes`. The profile answers "how delayed has this train historically been by the
time it reaches this station" - not "how much extra delay does this specific section add".

This maps onto the *existing, unchanged* `HistoricalDelayProvider.getHistoricalDelay(trainNumber,
RouteSection)` cleanly and without any interface change: the implementation
(`PostgresHistoricalDelayProvider`) looks up the profile keyed on `section.toStation().code()` -
for ETA purposes, "how delayed has this train historically been by the time it reaches the
section's destination" is a reasonable, honest stand-in for a true section delta, and is exactly
what the data available can actually support today.

### What was built

```java
// model.domain.HistoricalDelayProfile
public record HistoricalDelayProfile(
        String trainNumber,
        String stationCode,           // not a full Station - see below
        int sampleCount,
        double averageArrivalDelayMinutes,   // unclamped - can be negative (early-running)
        double medianArrivalDelayMinutes,    // unclamped
        double standardDeviationMinutes,     // always >= 0
        String source,                       // single value, or DataProvenance.combine(...) if mixed
        Instant computedAt) { ... }
```

- **`stationCode`, not a full `Station`.** The persisted profile only ever tracked a code (that's
  all its identity/grouping needs); reconstructing a display name from just a code on read-back
  would mean fabricating data this type has no way to know. `fromStation`/full section identity
  was dropped from the profile entirely (see the sample-unit decision above) - `RouteSection`
  (with its own `fromStation`/`toStation`) is still what `HistoricalDelayProvider` receives and
  returns via `HistoricalDelay.section()`; the profile itself just doesn't carry a second copy of
  that shape when it isn't the actual aggregation key.
- **Average/median deliberately unclamped**, unlike `HistoricalDelay`'s own fields (which predate
  any real, possibly-negative data and still require non-negativity). Clamping happens at exactly
  one place - `PostgresHistoricalDelayProvider`'s adapter into `HistoricalDelay` - mirroring
  `LiveTrainDataMapper`'s identical clamp for `currentDelayMinutes`. `HistoricalDelayProfile`
  itself, and everything that persists/aggregates it, keeps the honest, possibly-negative value.
- **Standard deviation**: sample (not population) standard deviation, Bessel's corrected (`n-1`
  denominator) - estimating variability of an ongoing process from a limited number of observed
  journeys is the textbook case for this choice. For exactly one observation (`n-1=0`, undefined),
  this returns `0.0` rather than `NaN`.
- **Median**: the standard convention - the middle value for an odd count, the average of the two
  middle values for an even count.
- **Minimum sample policy**: none enforced by the aggregator. Zero usable observations produces a
  `sampleCount = 0` profile (matching `HistoricalDelay`'s own "no data" convention), never a
  fabricated statistic. Deciding whether a given sample count is large enough to actually *trust*
  remains `HistoricalDelayCalculator`'s job (its existing `minimumSampleCount`, unchanged) - the
  aggregator's only job is to report the data honestly, however much or little exists.
- **Provenance mixing rule** (item 5): `DataProvenance.combine(Set<String>)` - a single
  contributing source passes through unchanged; more than one produces an explicit
  `"mixed(mock-provider,railradar)"`-style label (alphabetically sorted, so it's deterministic),
  never silently collapsed to one source. In practice, every observation persisted today comes
  from `HistoricalObservationMapper` tagging `DataProvenance.RAILRADAR`, so a real "mixed" profile
  can't occur yet from the app's own normal operation - this rule exists defensively, for if/when
  a second ingestion path (e.g. a manually-seeded or migrated dataset) is ever added.
- **Components**: `HistoricalDelayProfileAggregator` (pure statistics - `List<HistoricalObservation>
  → HistoricalDelayProfile`, no JPA, no RailRadar types) is invoked by
  `HistoricalDelayProfileRefresher` (loads observations via `HistoricalObservationRepository`,
  aggregates, upserts via `HistoricalDelayProfileRepository` - the JPA-aware orchestrator). New
  table `historical_delay_profiles` (`db/migration/V2__create_historical_delay_profiles.sql`),
  entity/repository/mapper mirroring Phase 16A's observation persistence exactly.

### Reproducibility and source of truth (item 4)

`historical_delay_profiles` is a cache of a computation, nothing more: `HistoricalDelayProfileRefresher`
always re-derives a profile from `historical_observations` and upserts (never appends/versions) -
the raw table is never deleted, altered, or bypassed. Re-running the refresher for the same
(trainNumber, stationCode) is idempotent and always reproducible from the raw data.

### Provider selection (items 7-9)

`HistoricalDelayProvider`'s interface needed **no change** - confirmed above. Two implementations
now exist, selected deterministically by `historical.provider` (`mock`, the default, or
`postgres`), each `@ConditionalOnProperty`-gated so **exactly one** `HistoricalDelayProvider` bean
is ever created:

- `historical.provider` unset or `mock` → `MockHistoricalDelayProvider` (today's default and
  active behaviour - **unchanged**, `PredictionService` still only ever sees mock historical data
  unless this property is explicitly overridden).
- `historical.provider=postgres` → `PostgresHistoricalDelayProvider`. This one takes a *required*
  (not `Optional`) `HistoricalDelayProfileRepository` dependency, deliberately: if someone sets
  this property without the `postgres` Spring profile also being active (so no repository bean
  exists), Spring fails application startup outright (`UnsatisfiedDependencyException`) rather
  than silently falling back to mock data under a real-sounding configuration. This is the "no
  silent fallback" rule from item 8, achieved for free by relying on Spring's own dependency
  resolution rather than writing bespoke fallback logic.
- An unrecognised value (e.g. a typo) selects *neither* bean - also fails fast, rather than
  guessing which one was meant.

**The prediction pipeline was not changed to depend on the new provider** (item 9): `PredictionService`
still only ever injects "whichever single `HistoricalDelayProvider` bean exists," and with the
default configuration that's still `MockHistoricalDelayProvider`. Nothing autonomously switches
this - a deliberate operator action (`historical.provider=postgres`) is required, and even then,
`HistoricalDelayProfileRefresher` must have been run at least once for a given (train, station)
for any real profile to exist to serve.

### Temporal/future-data leakage (item 11) - documented, not fixed

`HistoricalObservationRepository.findByTrainNumberAndStationCode` returns *every* observation ever
recorded for that (train, station), with no point-in-time cutoff, and
`HistoricalDelayProfileRefresher` aggregates all of them unconditionally. This is fine for "what
does history look like as of right now" (the only use case this phase supports), but a profile
computed this way must **not** be used to predict a *past* journey for backtesting/accuracy
validation (a stated future goal, Phase 18) - doing so would let observations from *after* the
predicted moment leak into the statistic, which is a classic look-ahead bias. Fixing this
properly needs a cutoff parameter (e.g. "only observations with `observedAt`/`journeyDate` before
T") threaded through the repository query, the aggregator, and the refresher - not implemented
here, and any future backtesting work must not treat today's profiles as suitable for that
purpose without it.

### Testing (item 10)

H2 again, for the same reason as Phase 16A (no Docker in this environment - see
`HistoricalObservationRepositoryTest`'s own Javadoc). `HistoricalDelayProfileRepositoryTest`
follows the identical pattern (Flyway disabled, Hibernate `create-drop`, only entity mapping and
query semantics verified against a real relational engine - `V2__create_historical_delay_profiles
.sql` itself is not exercised against real PostgreSQL). `HistoricalDelayProviderSelectionTest` uses
Spring Boot's `ApplicationContextRunner` to verify the conditional-bean selection rule directly
(default/mock/postgres/unrecognised-value cases) without needing a full application context.

## Phase 16C implementation notes (what's actually built)

Phase 16C's goal was to make the pipeline built in 16A/16B actually accumulate and refresh real
data in a controlled way - it deliberately does **not** touch ETA mathematics, the REST contract,
or the station-level-only limitation documented above.

### Collection stays in the live-prediction path (item 1/2)

Re-inspecting the code confirmed `RailRadarTrainDataProvider` already records observations as a
best-effort side effect of the live-prediction RailRadar call (`recordHistoricalObservationsSafely`,
wrapped in try/catch) - this was **not changed**. A truly independent collection job (polling
RailRadar on its own schedule, unrelated to prediction requests) was considered and rejected: it
would need a registry of "which trains to track" that doesn't exist anywhere in this app, and it
would consume additional RailRadar API quota (the documented free tier is 1,000 requests/month) for
no benefit over piggybacking on calls the app is making anyway. This is the "smallest safe approach"
the brief invited if full separation wasn't practical.

What Phase 16C actually separates is **profile refresh**. `HistoricalDelayProfileRefresher`
(Phase 16B) was already decoupled from prediction in code, but unreachable - nothing ever called
it. Phase 16C adds `HistoricalDelayProfileRefreshScheduler`, a `@Scheduled` component
(`historical.profile-refresh.interval-ms`/`initial-delay-ms`, default 1 hour / 1 minute - see
docs/configuration.md) that is the *only* thing that calls it, on its own timer, independent of any
prediction request:

```
RailRadar data collection → HistoricalObservationMapper → HistoricalObservationRecorder → PostgreSQL
(separately, on a timer)
HistoricalObservationRepository.findDistinctTrainStationKeys() → HistoricalDelayProfileRefresher → HistoricalDelayProfileRepository
```

The scheduler discovers *which* (trainNumber, stationCode) pairs to refresh via a new repository
query, `findDistinctTrainStationKeys()` (a `SELECT DISTINCT ... new TrainStationKey(...)` JPQL
projection) - there being no other "trains to track" registry. Each pair is refreshed in its own
try/catch so one failure (a transient DB error, for example) never prevents the rest from
refreshing. The scheduler checks for the observation repository's presence directly (rather than
calling the refresher and catching its `IllegalStateException`) so that "no database configured at
all" - the default, expected case - is a quiet no-op rather than a logged failure on every tick.
No Kafka, Redis, or other messaging infrastructure was introduced, per the brief; `@EnableScheduling`
(`config/SchedulingConfig`) is the only new infrastructure.

### Journey completion: no reliable signal exists (item 3) - documented limitation, not fixed

Re-inspecting `RouteStop`, `CurrentLocation`, and `LiveTrainStatusData` confirmed there is still no
RailRadar signal that can reliably establish *whole-journey* completion. The only candidate,
`LiveTrainStatusData.status == "terminated"` (mapped to `TrainStatus.TERMINATED` by
`LiveTrainDataMapper.mapStatus()`), has always been a best guess from Phase 3 - its actual value
vocabulary was never confirmed against a real RailRadar response (no API key available in this
development environment). Phase 16C does not fabricate a completion rule on top of this unconfirmed
signal. The granularity Phase 16C actually needs - and already has reliably - is **station-level**
completion: `RouteStop.actualArrival() != null` means the train has reached that station, and this
has been true and unchanged since Phase 16A. Whole-journey completion remains an open question for
a future phase, once the `status` vocabulary can be confirmed against real data.

### Data-quality validation (item 4/5)

A bug was found while re-inspecting `HistoricalObservationMapper` for this phase (not previously
reported): it built each `HistoricalObservation` inside a `.stream().map(...)` chain, so a single
malformed `RouteStop` (e.g. a non-null-but-blank station code, which the domain record's own
constructor guard rejects) would throw mid-stream and discard the **entire** batch for that
response, not just the one bad row. This is now fixed: rows are processed in an explicit loop, each
validated independently before construction, and construction itself is further wrapped in a
try/catch as defense-in-depth - one bad row can now only ever cost that one row.

Validation rules applied to each reached station (`actualArrival != null`) before it is persisted:

| Condition | Outcome | Reason |
|---|---|---|
| `stationCode` is blank | **Rejected** | not a usable grouping key |
| `stationSequence` present and `<= 0` | **Rejected** | impossible station ordering |
| `arrivalDelayMinutes`/`departureDelayMinutes` magnitude `>` `historical.observation-validation.max-plausible-delay-minutes` (default 4320 = 72h) | **Rejected** | treated as corrupt/nonsensical, not a genuinely extreme delay - an assumed sanity bound, not derived from real RailRadar data |
| `actualArrival == null` | **Not a rejection** | the normal, expected "train hasn't reached this station yet" case |
| train-number format | **Not validated here** | the REST controller's `@Pattern` (5-digit format) already owns this; the mapper only requires non-blank, via the domain record's own guard |
| timestamp ordering (e.g. departure before arrival) | **Not validated - deferred, documented limitation** | RailRadar's raw scheduled/actual time strings are deliberately kept as opaque `String`s (their format/timezone was never confirmed against a real response), so no structured ordering comparison is possible without first resolving that longer-standing limitation |
| invalid journey date | **Not applicable by construction** | `journeyDate` is always derived from the injected `Clock`, never parsed from external data |

Rejections are logged at debug level with a reason and counted (see metrics below); they never
throw out of `toObservations()`.

### Operational visibility (item 6)

A new `HistoricalDataMetrics` (plain `AtomicLong` counters, no Micrometer/Actuator registration -
deliberately not a metrics stack) tracks: observations received, rejected, inserted, updated,
persistence failures, profiles generated, profile refresh failures. Wired into
`HistoricalObservationMapper` (received/rejected), `HistoricalObservationRecorder`
(inserted/updated/persistence-failures), and `HistoricalDelayProfileRefreshScheduler`
(profiles-generated/refresh-failures). Getters exist primarily for test assertions; the existing
SLF4J log lines each component already emits remain the actual operational visibility mechanism.

### Preventing unsafe profile usage (item 7) - already satisfied, unchanged

Re-confirmed: `historical.provider` still defaults to `mock`; nothing in Phase 16C changes that
default or auto-switches production to PostgreSQL merely because the provider class exists.
`PostgresHistoricalDelayProvider` already passed through a low/insufficient `sampleCount` unmodified
before this phase (no production change needed) - `HistoricalDelayCalculator`'s existing
`minimumSampleCount` gate remains the sole trust decision. A new test
(`passesThroughAnInsufficientSampleCountUnmodifiedRatherThanFabricatingTrust`) confirms this
explicitly.

### Testing (item 9)

New/extended: `HistoricalObservationMapperTest` (blank station code / impossible sequence /
implausible delay each rejected without discarding the rest of the batch; received/rejected
counters), `HistoricalObservationRecorderTest` (insert/update/persistence-failure counters),
`HistoricalDataMetricsTest`, `HistoricalObservationValidationPropertiesTest`,
`HistoricalObservationRepositoryTest` (`findDistinctTrainStationKeys` - collapses repeats, empty
when no data), `HistoricalDelayProfileRefreshSchedulerTest` (no-op with no repository, refreshes
every discovered pair, one pair failing doesn't block the others), and one new
`PostgresHistoricalDelayProviderTest` case for insufficient samples. Repeated polling / idempotent
recording / refresh-with-no-data / refresh-with-real-observations / mixed-provenance /
deterministic-provider-selection were already covered by Phase 16A/16B's own tests and were
re-verified, not re-written. All still run against H2 (no Docker/Testcontainers in this
environment - unchanged tradeoff from 16A/16B).

### Remaining limitations after Phase 16C

- Still station-level only, not a true section delay-change model (unchanged from 16B - see above).
- No whole-journey completion signal (see above) - only station-level completion is used.
- Timestamp-ordering validation is not possible until the raw time-string format is confirmed.
- Temporal/future-data leakage (documented in the 16B notes above) is still unresolved - the
  scheduler makes profiles refresh automatically, which makes this *more* relevant for any future
  backtesting use, not less.
- **Phase 16D** is expected to investigate a genuine section-level historical model; nothing in
  16C should be read as already providing one.

## Phase 16D: design and data-feasibility findings (no code changed)

Phase 16D was a design-only investigation (report delivered in-session, not persisted here in
detail) into whether the raw `HistoricalObservation` data could honestly support a section-level
model. It found three concrete, previously-undocumented raw-data problems - not hypothetical ones,
each confirmed by reading the actual code:

1. **`journeyDate` is assignment-time (per poll), not a verified physical-journey identifier.** A
   journey whose stations are observed on both sides of local midnight gets its observations split
   across two different `journeyDate` values.
2. **The origin station was never persisted at all.** `HistoricalObservationMapper`'s inclusion
   filter required `actualArrival() != null`, and an origin station never has an arrival (only a
   departure) - so the first leg of every journey had no usable FROM-side raw data.
3. **`station_sequence` is nullable**, and RailRadar may omit it, making ordering ambiguous for the
   affected row.

These three problems had to be fixed (or explicitly, honestly bounded) before any section-level
aggregation could be attempted without fabricating relationships the raw data doesn't actually
support - hence Phase 16E below.

## Phase 16E implementation notes (what's actually built)

Phase 16E fixes/strengthens the raw-observation layer only, per Phase 16D's findings. It does
**not** implement section profiles, section aggregation, or any prediction/ETA change - `historical_observations`
remains the sole raw source of truth, unchanged in shape (no Flyway migration was needed - see
below).

### 1. Journey identity - re-confirmed there is no trustworthy journey-date field, then mitigated

Re-inspecting every DTO in the response (`LiveTrainStatusData`, `RouteStop`, `CurrentLocation`,
`NextHalt`, `ResponseMeta`) confirmed **no structured, trustworthy journey-start-date or
journey-identifier field exists anywhere in RailRadar's live-status response.** The only
date-adjacent fields are `LiveTrainStatusData.lastUpdatedAt` and `ResponseMeta.timestamp` - both
describe when the *response* was generated, not when the *journey* started, and both remain
opaque, unconfirmed-format strings (the same standing limitation from Phase 16A). Parsing either
speculatively to derive a "real" journey date was therefore rejected, per the brief's own
instruction not to do so.

`journeyDate` (`model.domain.HistoricalObservation.journeyDate`) is documented, unambiguously, as
an **observation-date**: the calendar date, per the application's own `Clock`, on which a given
station's fact was captured - never a verified physical-journey identifier. This was already true
before Phase 16E; what changed is that it is now stated explicitly everywhere relevant (the
domain record's Javadoc, the new properties class below, this document) rather than left implicit.

**Mitigation, not a fix** (`config.HistoricalJourneyDateProperties`, `historical.journey-date.operating-day-start-hour`,
default `0`): the mapper no longer blindly uses `LocalDate.now(clock)`. It uses an "operating day"
convention - the same one many transit systems use (e.g. GTFS's own allowance for times past
24:00:00 to still mean the previous service day) - where an observation captured before the
configured start hour is attributed to the *previous* calendar date instead of the literal one.
With the default (`0` = midnight), this is byte-for-byte identical to the pre-16E behaviour - no
change unless an operator explicitly configures a later hour for a specific overnight service they
know about. This uses **only** the application's own already-trusted clock; it derives nothing
from RailRadar and invents no journey identifier. It reduces, but does not eliminate, the
midnight-crossing fragmentation risk - a journey genuinely spanning more than one operating-day
boundary (a very long-haul train) is still an accepted, documented limitation. See
`HistoricalObservationMapper`'s `journeyDateIsUnaffectedByOpaqueResponseTimestampFields` test for
the explicit proof that no opaque response field ever influences this computation.

### 2. Origin observations are now persisted

`HistoricalObservationMapper`'s inclusion filter changed from `actualArrival() != null` to
`actualArrival() != null || actualDeparture() != null`. This directly fixes Phase 16D's finding:
an origin stop (departure known, arrival never applicable) is now eligible, and its
`HistoricalObservation` carries `actualArrival = null`, `scheduledArrival = null`,
`arrivalDelayMinutes = null` exactly as RailRadar reported (or rather, didn't report) - **nothing
is fabricated** to fill in the missing arrival side. The same broadened rule also correctly admits
an intermediate station that currently has only one of the two events (e.g. a train currently
halted, with an arrival recorded but no departure yet) - each event stays independently present or
absent, exactly as before, never synthesized. A stop with **neither** event (still ahead of the
train) remains excluded, unchanged.

Validation (`rejectionReason`) required no change - it was already independent of arrival/departure
presence (it only ever checked station code, sequence, and delay plausibility), so origin rows are
validated by the same rules as every other row, not exempted from anything.

### 3. Event semantics preserved; the future section formula is now documented in one place

No generic "delay" field was introduced. `HistoricalObservation` continues to carry
`scheduledArrival`/`actualArrival`/`arrivalDelayMinutes` and
`scheduledDeparture`/`actualDeparture`/`departureDelayMinutes` as fully independent, nullable
fields. The record's Javadoc now states explicitly, in one place, the section formula a *future*
aggregation phase must use (first proposed in Phase 16D): for consecutive stops FROM and TO,

```
delayChangeMinutes = TO.arrivalDelayMinutes - FROM.departureDelayMinutes
```

— never `TO.arrivalDelayMinutes - FROM.arrivalDelayMinutes` (which would misattribute dwell-time
recovery at FROM to the FROM→TO section itself). This is documentation only; no aggregation of any
kind happens in this raw layer, in Phase 16E or otherwise.

### 4. Station ordering - `RouteStop.sequence()` confirmed as the only ordinal, null retained

Re-inspecting the full response confirmed `RouteStop.sequence()` is the only per-stop ordinal
anywhere in the schema - `CurrentLocation.sequence()` and `NextHalt.sequence()` describe the
current/next stop specifically, not a usable position for every stop in `route[]`, so neither is a
substitute. No fallback ordinal exists. The mapper already retained (never rejected) a null
`stationSequence` before Phase 16E; this is unchanged and now has explicit test coverage
(`aStopWithNullSequenceIsRetainedNotRejected`, `anOriginStopWithNullSequenceIsRetained`). Per the
brief, no substitute/invented sequence value is ever written - a future section-reconstruction
phase (16F+) **must** exclude null-sequence rows from adjacency pairing; this mapper can only
guarantee the value is never fabricated, it has no aggregation logic to enforce that exclusion
itself.

### 5. Natural key - reviewed, kept unchanged, one residual limitation documented

`(trainNumber, journeyDate, stationCode, stationSequence)` was re-examined against all of Phase
16E's changes and against the six scenarios the brief asked to test explicitly (same train,
different dates; overnight; repeated station code with a valid sequence; repeated station code
with a null sequence; duplicate polling; origin observation; null sequence) - see
`HistoricalObservationRepositoryTest` and `HistoricalObservationRecorderTest` for each. **The key
does not need to change** - origin rows and null-sequence rows both fit the existing shape without
any migration.

One residual, accepted limitation was found and is deliberately *not* fixed in this phase: SQL
`NULL` is never equal to `NULL` in a unique constraint, so two rows sharing `(trainNumber,
journeyDate, stationCode)` with a **null** `stationSequence` are *not* rejected by the database the
way two identical rows with a *non-null* sequence would be
(`duplicateNaturalKeyInsertViolatesTheUniqueConstraint` demonstrates the non-null case still throws;
`repeatedStationCodeWithoutSequenceIsNotProtectedByADatabaseUniqueConstraint` demonstrates the
null-sequence case does not). In the common, non-concurrent case this is harmless: the
application-level idempotent upsert (`HistoricalObservationRecorder`) finds the existing
null-sequence row first via Spring Data JPA's `IS NULL` translation of a null argument
(`findByNaturalKeyLocatesAnExistingRowWhenStationSequenceIsNull` proves this against a real,
H2-backed engine) and updates it in place. Only a genuine *concurrent* race between two polls of
the same null-sequence station could still produce a duplicate row - a narrow window, accepted as
a documented limitation rather than closed with a partial-index migration, which would be a larger
change than this phase's "smallest correct migration" instruction invites for a risk this narrow.

### 6. `observed_at` semantics - unchanged, documented more prominently

`observed_at` continues to mean "when the application observed/persisted this external fact" (set
from the injected `Clock` at mapping time) - **not** "when the railway event occurred" (that would
require parsing the still-unconfirmed opaque time strings). This was already true; it is now
stated explicitly in `HistoricalObservation`'s own Javadoc, alongside its intended future use as an
approximate (polling-latency-bounded, not exact) point-in-time cutoff for temporal-leakage-safe
historical queries - the same idea Phase 16D identified as already-usable without any schema
change.

### 7. Database changes

**None.** Every nullable column the origin fix and the null-sequence retention rely on
(`station_sequence`, `scheduled_arrival`, `actual_arrival`, `scheduled_departure`,
`actual_departure`, `arrival_delay_minutes`, `departure_delay_minutes`) was already nullable in
`V1__create_historical_observations.sql` and in `HistoricalObservation`'s own constructor guard,
which requires only `trainNumber`, `journeyDate`, `stationCode`, `observedAt`, and `source` to be
non-null. No Flyway migration was needed or added in this phase.

### Testing (item 11)

New/extended: `HistoricalObservationMapperTest` (origin with departure-only, with/without
departure delay, intermediate arrival-only, intermediate departure-only, neither-event exclusion,
null-sequence retention for both a normal and an origin stop, plain vs. operating-day-adjusted
journey dates on both sides of a configured cutoff, independence from opaque response timestamp
fields), `HistoricalJourneyDatePropertiesTest` (valid range, rejects out-of-range hours),
`HistoricalObservationRecorderTest` (origin persistence, repeated polling of a null-sequence
station updates in place, two same-journeyDate observations after the overnight mitigation
collapse into one row), `HistoricalObservationRepositoryTest` (origin persistence at the entity
level, null-sequence natural-key lookup against a real H2 engine, repeated station code with
distinct sequences persists as two rows, repeated station code without a sequence is *not*
DB-constraint-protected - the one documented residual limitation above). Full regression: all
pre-existing tests re-run unchanged and pass.

### Remaining limitations after Phase 16E

- Still station-level aggregation only - Phase 16E touches the raw layer exclusively; no section
  profile, aggregation, or ETA change was made (unchanged from 16C/16D).
- The operating-day mitigation reduces, but does not eliminate, midnight-crossing journey
  fragmentation - a journey spanning more than one operating-day boundary is unsolved, and remains
  so until (if ever) RailRadar exposes a genuine journey identifier.
- The null-sequence natural-key race (§5 above) is an accepted, narrow, documented limitation, not
  a closed gap.
- Timestamp-ordering validation (e.g. departure before arrival) remains impossible until the raw
  time-string format/timezone is confirmed against a real response - unchanged since Phase 16A.
- A future section-reconstruction phase must still explicitly exclude null-sequence rows from
  adjacency pairing - nothing in this raw layer enforces that for it.

## Phase 16F implementation notes (what's actually built)

Phase 16F implements the section-level historical model designed in Phase 16D and unblocked by
Phase 16E's raw-data fixes. It does **not** integrate section profiles into
`HistoricalDelayProvider`, `PredictionService`, `PredictionEngine`, or ETA math in any way - those
remain entirely as they were before this phase.

### Exact section-sample definition

For two observations FROM and TO to form one section sample, **all** of the following must hold:

1. Same `trainNumber` and same `journeyDate` (see "Journey grouping" below on why this is the
   strongest identity available, not a verified physical-journey guarantee).
2. Both have a non-null `stationSequence`.
3. Once every non-null-sequence observation in that (trainNumber, journeyDate) group is sorted
   ascending by `stationSequence`, FROM and TO are adjacent in that sorted list.
4. `FROM.departureDelayMinutes() != null`.
5. `TO.arrivalDelayMinutes() != null`.

```
delayChangeMinutes = TO.arrivalDelayMinutes - FROM.departureDelayMinutes
```

preserved unclamped - a negative value (recovery) is a real fact, never floored to zero, never
discarded.

An adjacency that is structurally discovered (conditions 1-3) but never satisfies conditions 4-5
for any of its occurrences still produces a profile - with `sampleCount = 0` and
`DataProvenance.UNAVAILABLE` - because the adjacency itself is real even without a usable
statistic. An adjacency never structurally discovered at all produces no profile.

### Pairing and ordering rules - never fabricated

Sorting is **only** ever by `stationSequence` ascending, via `Comparator.comparingInt` - never by
station code, station name, database insertion order, or polling order (`HistoricalSectionDelayProfileAggregator`
never touches list order as a signal). A null-sequence observation never participates in a pair on
either side - it is filtered out entirely before sorting. If two observations in the same journey
group share the exact same non-null sequence value (an anomaly the raw table's own unique
constraint shouldn't normally allow, but the aggregator does not assume it can't happen), their
relative order is untrustworthy - the **entire journey group** is excluded from pairing rather than
guessing which one comes first.

A "section" produced this way means **two consecutively-persisted observations**, not necessarily
one indivisible physical track segment: a sequence gap (a genuinely skipped/cancelled station, or
simply one this application never recorded) is never distinguished from any other cause and is
never inferred or corrected - see `HistoricalSectionDelayProfile`'s own Javadoc.

### Journey grouping

`(trainNumber, journeyDate)` is used as the grouping boundary - it is the strongest journey
identity the current schema actually offers, **not** a verified physical-journey identifier (see
Phase 16E's own findings). Two observations under different `journeyDate` values are never pooled
together, even if they might plausibly belong to the same real overnight journey that happened to
cross a `journeyDate` boundary - if the raw data doesn't already agree they're the same journey,
this aggregator does not guess. This is deliberate, safe, conservative behaviour: a
midnight-crossing split (Phase 16D/16E's own concern) simply results in that adjacency never being
discovered, rather than being fabricated across the boundary.

### Statistics

Sample count, arithmetic mean, and sample (Bessel-corrected, `n-1`) standard deviation, computed
directly from the list of individual `delayChangeMinutes` values for a given (trainNumber,
fromStationCode, toStationCode) - **never** by subtracting two already-aggregated station-level
`HistoricalDelayProfile` averages (that would average-then-subtract instead of subtract-then-
average, which are not equivalent and would misrepresent variance). One sample yields
`standardDeviationMinutes = 0.0` (not `NaN`); zero usable samples for a discovered adjacency yields
`sampleCount = 0`, all numeric statistics `0.0`, and `source = DataProvenance.UNAVAILABLE` - the
same conventions `HistoricalDelayProfileAggregator` already established. Median follows the
standard convention (middle value for odd counts, average of the two middle values for even
counts).

### Provenance

Each individual section sample's provenance is `DataProvenance.combine(...)` of its FROM and TO
endpoints' own sources (e.g. `RAILRADAR + RAILRADAR → RAILRADAR`, `RAILRADAR + MOCK →
mixed(mock-provider,railradar)`). The profile's overall `source` is then `DataProvenance.combine(...)`
over every contributing sample's already-combined source - so a profile built entirely from
same-source samples reports that one source, and any mix at any level is always an explicit
`mixed(...)` label, never silently collapsed to look like a single trusted source.

### Temporal cutoff

`HistoricalSectionDelayProfileAggregator.aggregate(trainNumber, observations, referenceInstant)`
compares `referenceInstant` against each observation's own `observedAt` (never against the opaque,
unparsed schedule/actual-time strings) - an observation is in scope when
`!observedAt.isAfter(referenceInstant)`, i.e. **the cutoff instant is inclusive**: "the profile as
it stood at or before this moment." Anything recorded strictly after is excluded entirely - if
excluding a later observation breaks an adjacency's continuity, that adjacency is simply not
discovered for that call, not partially fabricated. `HistoricalSectionDelayProfileRefresher`
exposes both an unrestricted overload (`refreshSectionProfilesForTrain(trainNumber)`, using "now"
as the cutoff - equivalent to an unrestricted rebuild in practice, since no persisted observation
can have an `observedAt` in the future) and an explicit-cutoff overload
(`refreshSectionProfilesForTrain(trainNumber, referenceInstant)`) for a future caller (backtesting,
Phase 16G+) that needs a real point-in-time restriction.

### Minimum sample policy - deliberately not introduced

No minimum-sample threshold is hard-coded into `HistoricalSectionDelayProfileAggregator`, and none
was added as a new configuration property in this phase. Item 10's own instruction was to
introduce one "only where it is actually needed by the consumer/provider" - Phase 16F has no
consumer yet (nothing reads `HistoricalSectionDelayProfile` for prediction purposes), so there is
nothing to configure a threshold *for*. Whoever wires this into `HistoricalDelayProvider` (Phase
16G) must decide, and separately configure, its own minimum-sample-count - and should not simply
reuse the existing station-level `prediction.historical-adjustment.minimum-sample-count`: a
delay-change sample is a *difference* of two independently-noisy quantities (TO's arrival delay,
FROM's departure delay), and variance of a difference is the sum of the two contributing variances
- a section-level statistic is inherently noisier than either station-level average it's built
from, for the same sample count, and likely deserves a *higher* minimum, though the actual right
number should come from real observed variance, not a guess made now.

### Persistence

New table `historical_section_delay_profiles` (Flyway `V3`), new
`repository.HistoricalSectionDelayProfileEntity`/`Repository`/`EntityMapper`, mirroring Phase
16B's station-level pattern exactly. Natural key `(trainNumber, fromStationCode, toStationCode)` -
computed statistics are never part of the identity. Raw section samples are **not** stored as a
second copy of `HistoricalObservation` - the table stores only the aggregate. Re-aggregating a
given (train, from, to) key updates its existing row in place (`updateFrom`), it never versions or
appends - the table is fully reproducible from `historical_observations`, which remains the sole
source of truth.

### Profile refresh

`HistoricalSectionDelayProfileRefresher` loads every observation ever recorded for one train
(`HistoricalObservationRepository.findByTrainNumber`, a new query), aggregates every
(fromStationCode, toStationCode) adjacency it discovers via `HistoricalSectionDelayProfileAggregator`,
and upserts each resulting profile. `HistoricalDelayProfileRefreshScheduler` (Phase 16C's existing
`@Scheduled` component) was **extended**, not duplicated - after its existing station-level refresh
step, it now also calls `HistoricalObservationRepository.findDistinctTrainNumbers()` (a new query)
and refreshes each train's section profiles, each inside its own try/catch so one train's failure
never blocks another's - the same fault-isolation pattern the station-level refresh already used.
No prediction request refreshes anything, unchanged. New `HistoricalDataMetrics` counters
(`sectionProfilesGenerated`, `sectionProfileRefreshFailures`) mirror the existing station-level
ones.

### HistoricalDelayProvider - deliberately unchanged

Per item 13, the interface was not touched, and nothing wires section profiles into
`PredictionService`/`PredictionEngine`. **Phase 16G** is the recommended next step for that
integration - see "Remaining limitations" below.

### Testing

`HistoricalSectionDelayProfileAggregatorTest` (chaining A→B→C, multiple journeys accumulating into
one profile, different-date journeys never cross-paired, repeated station code with valid
sequences, null-sequence exclusion, sequence gaps, unsorted input, ambiguous duplicate sequences,
missing-endpoint-delay zero-sample profiles, origin-departure-to-next-arrival, positive/negative/
zero delay change, mean/median/stddev over multiple samples including even-count median, one
sample, zero observations, all three provenance-combination cases plus a multi-sample mixed case,
and all three temporal-cutoff cases), `HistoricalSectionDelayProfileRefresherTest` (missing
repositories throw, aggregation-and-insert, re-refresh-updates-in-place, no observations produce no
profiles, the explicit-cutoff overload is genuinely honoured),
`HistoricalSectionDelayProfileRepositoryTest` (save/retrieve, natural-key lookup, unique-constraint
violation, reversed from/to is a distinct row, replace-in-place, zero-sample persistence),
`HistoricalObservationRepositoryTest` (new `findByTrainNumber`/`findDistinctTrainNumbers` queries),
and an extended `HistoricalDelayProfileRefreshSchedulerTest` (section refresh runs for every
discovered train, one train's failure doesn't block the others). Full regression: all pre-existing
tests re-run unchanged and pass.

### Remaining limitations after Phase 16F

- Section profiles are computed and persisted but consumed by nothing yet - `MockHistoricalDelayProvider`
  remains the default, `HistoricalDelayProvider`/`PredictionService`/`PredictionEngine` are
  untouched, exactly as required.
- "Section" means two consecutively-persisted observations, not a guaranteed single physical track
  segment - a sequence gap's cause is never determined.
- Journey grouping is still bounded by `journeyDate`'s own limitations (Phase 16E) - a genuine
  midnight-crossing split still prevents an otherwise-real adjacency from being discovered, it is
  simply never fabricated across the boundary.
- No minimum-sample-count policy exists yet for section profiles - deliberately deferred to
  whichever phase actually consumes them.
- The temporal cutoff is bounded by `observed_at`'s own approximate nature (polling-latency-bounded,
  not the true railway event time) - unchanged, standing limitation since Phase 16C/16D.

## Phase 16G implementation notes (what's actually built)

Phase 16G makes section-level historical information reachable through a clean domain
abstraction - it does **not** wire that abstraction into `PredictionEngine`, `PredictionService`,
`HistoricalDelayCalculator`, or the ETA formula; none of those files were touched.

### Chosen abstraction: a separate interface, not an extension of `HistoricalDelayProvider`

A new interface, `historical.HistoricalSectionDelayProvider`, was introduced rather than adding a
method (or a new field) to the existing `HistoricalDelayProvider`/`HistoricalDelay`. Reasons,
concrete rather than "for extensibility":

- `HistoricalDelay` requires its `averageDelayMinutes`/`medianDelayMinutes` to be non-negative
  (`Guard.requireNonNegative`) - a constraint that predates any real, possibly-negative data and
  is fundamentally incompatible with a delay-**change** statistic, which must be allowed negative
  (recovery). Loosening that guard to accommodate section data would weaken an invariant every
  existing consumer of `HistoricalDelay` already relies on.
- `HistoricalDelayProvider` has exactly one real consumer today (`HistoricalDelayCalculator`, via
  `PredictionService`), which only understands "average arrival delay at a station." Adding
  section semantics to the same interface would force that consumer (and any future one) to
  understand a second, unrelated statistic it doesn't need, merely because it lives on the same
  type.
- The two capabilities already have cleanly separate implementations upstream
  (`HistoricalDelayProfile` vs `HistoricalSectionDelayProfile`, `HistoricalDelayProfileRefresher`
  vs `HistoricalSectionDelayProfileRefresher`) - mirroring that separation at the provider layer is
  consistent, not novel.

### The contract

```java
public interface HistoricalSectionDelayProvider {
    SectionHistoricalDelayResult getSectionDelay(String trainNumber, RouteSection section, Instant referenceInstant);
}
```

- `section` - only `fromStation().code()`/`toStation().code()` are used (the section profile is
  keyed on plain codes, matching `HistoricalSectionDelayProfile`).
- `referenceInstant` is a **required** parameter, not an optional overload - every caller must be
  explicit about the point in time a lookup means (see "Point-in-time semantics" below for why this
  matters).
- Return type is a new domain record, `model.domain.SectionHistoricalDelayResult`
  (`trainNumber`, `fromStationCode`, `toStationCode`, `status`, `profile`), where `status` is a new
  enum `model.domain.SectionHistoricalDelayStatus { AVAILABLE, INSUFFICIENT_SAMPLES, NOT_FOUND }`
  and `profile` is the existing `HistoricalSectionDelayProfile` (average/median/standard-deviation
  of the delay change, sample count, provenance, `computedAt`) - `null` only when `status` is
  `NOT_FOUND`, enforced by the record's own constructor guard. No JPA entity, RailRadar DTO, or any
  database-specific type is ever exposed - proven by
  `domainResultNeverExposesAJpaEntityOrRailRadarType`.

### Minimum sample policy

New, **separate** property: `historical.section.minimum-sample-count` (default `10`), bound by a
new `config.HistoricalSectionProperties` - `prediction.historical-adjustment.minimum-sample-count`
(the station-level threshold, default `5`) is never read by anything section-related. The default
is reasoned, not proven: a delay-change sample is a *difference* of two independently noisy
quantities (a station's arrival delay, another station's departure delay), and variance of a
difference of independent variables is the *sum* of their variances - so, for comparable
per-quantity variance, a section-level average needs roughly double the samples of a single
station-level average for the same statistical confidence. `10` is therefore double the
station-level default - an **initial operational threshold**, explicitly documented as not
statistically derived from real data, to be revisited once real section observations exist in
enough volume to measure actual variance.

The four states item 4 asked to distinguish map onto `SectionHistoricalDelayStatus` plus
`profile.source()` as follows: "exists and usable" → `AVAILABLE`; "exists but insufficient
samples" → `INSUFFICIENT_SAMPLES`; "does not exist" → `NOT_FOUND`; "exists but provenance is
mock/mixed" → **not a separate status** - deliberately. The provider makes no provenance-based
*acceptability* decision (it never turns a mock- or mixed-sourced profile into `NOT_FOUND` or a
special status); `profile.source()` is always present and inspectable so a *future* consumer can
build that trust policy (this is squarely a prediction/confidence decision, and Phase 16G does not
touch prediction at all - see Phase 16H recommendation below).

### Provenance policy

`RAILRADAR`, `MOCK`, `UNAVAILABLE` (the aggregator's own zero-sample sentinel - see Phase 16F), and
any `mixed(...)` composite are all passed through completely unmodified from the persisted/derived
`HistoricalSectionDelayProfile` into the result - never hidden, never collapsed, never silently
treated as more trustworthy than they are (`mockOrMixedProvenanceIsPassedThroughUnhiddenAndUnrejected`
proves this for the Postgres provider). `MockSectionHistoricalDelayProvider` always tags its output
`DataProvenance.MOCK`, exactly like every other mock provider in this codebase.

### Point-in-time semantics - the central design question

**The materialized `historical_section_delay_profiles` cache can only safely answer a "now or
later" request; it cannot safely answer a genuinely retrospective one.** This was determined, not
assumed:

- The cache is a single row per `(trainNumber, fromStationCode, toStationCode)`, overwritten in
  place on every scheduled refresh (Phase 16F's own design - no version history is retained). Its
  `computedAt` records only *when* it was last recomputed, not *which* raw observations
  contributed beyond "everything that existed by then."
- For a request whose `referenceInstant` is "now" or later, reading the cache is safe: it can, at
  worst, be slightly *stale* (missing something recorded since the last scheduled refresh) - it can
  never be *leaky*, since no observation can ever have an `observedAt` later than genuine
  wall-clock "now."
- For a request whose `referenceInstant` is genuinely in the past (e.g. "what did we know as of
  2026-09-01", for a future backtesting use case), the cache **cannot** be trusted, even if a
  cached row happens to exist - it very likely reflects observations recorded well after the
  requested moment, which is exactly the future-data leakage the cutoff exists to prevent.

**Chosen solution**: `PostgresSectionHistoricalDelayProvider` compares `referenceInstant` against
"now" (via its injected `Clock`). "Now or later" reads the cache directly (one indexed lookup, the
fast path, and the one every realistic near-term caller needs). Anything strictly in the past
bypasses the cache **entirely** and instead reloads every raw observation for the train
(`HistoricalObservationRepository.findByTrainNumber`) and re-runs
`HistoricalSectionDelayProfileAggregator.aggregate(...)` directly with the exact requested cutoff -
the same aggregator Phase 16F already built, tested, and proved correct for exactly this, just
invoked on demand instead of through the cache. This was chosen over the two schema-driven
alternatives the phase's own brief raised (profile versioning; a separate historical-snapshot
table) because the aggregator already solves the problem correctly without either - no schema
change was needed (see below). The tradeoff is speed, not correctness: a retrospective query is
slower (loads and re-aggregates raw data) than a "now" query (one indexed read) - an acceptable
cost for a code path nothing currently calls in production.

`aReferenceInstantStrictlyInThePastBypassesTheCacheEntirelyAndReAggregatesFromRawObservations` and
`demonstratesTheMaterializedCacheCannotAloneAnswerAGenuinePointInTimeQuery` prove this behaviour
directly: a deliberately misleading cached row (sample count 999, average 999.0) is present in
both tests specifically so a naive "just read the cache" implementation would fail them - the
provider ignores it entirely for a past `referenceInstant` and returns the honestly re-derived
value instead.

### Provider implementation and selection

`MockSectionHistoricalDelayProvider` and `PostgresSectionHistoricalDelayProvider` both implement
`HistoricalSectionDelayProvider`, selected by a **new, separate** property,
`historical.section-provider` (`mock`, the default, or `postgres`) - deliberately independent from
`historical.provider` (the station-level provider's own switch), since nothing yet requires the two
to move together. Exactly one bean of either type ever exists
(`@ConditionalOnProperty`-gated identically to the station-level pair); asking for `postgres`
without the "postgres" Spring profile fails startup loudly, the same "no silent fallback" rule as
every other provider pair in this codebase - `HistoricalSectionDelayProviderSelectionTest` proves
this, plus that `historical.provider` has no bearing on `historical.section-provider` selection.

### Database

**No migration.** `historical_section_delay_profiles` (Phase 16F's `V3`) is read as-is; the
on-demand path reuses `historical_observations` and `HistoricalObservationRepository.findByTrainNumber`
(already added in Phase 16F) exactly as they stand. The point-in-time design question was resolved
entirely at the provider/aggregation layer - see above for why that was preferred over profile
versioning or a snapshot table.

### Testing

`SectionHistoricalDelayResultTest` (the record's own NOT_FOUND-must-have-no-profile /
otherwise-must-have-a-profile invariants), `HistoricalSectionPropertiesTest`/
`HistoricalSectionMockPropertiesTest` (config validation, including that a negative average/median
delay change is explicitly *allowed* for the mock properties, unlike the station-level mock's),
`MockSectionHistoricalDelayProviderTest`, `PostgresSectionHistoricalDelayProviderTest` (correct
pair lookup, wrong train/from/to station all produce `NOT_FOUND`, insufficient-samples reporting,
negative/zero delay change preserved, mock/mixed provenance passed through unrejected, domain
isolation, and the two temporal-semantics tests described above), and
`HistoricalSectionDelayProviderSelectionTest` (mirroring the station-level selection test exactly,
plus proving independence from `historical.provider`). Full regression: all pre-existing tests
re-run unchanged and pass.

### Why `PredictionEngine` is not yet integrated

Per the phase's explicit scope: making section data reachable through a clean, testable
abstraction is a distinct concern from deciding *how* prediction should use it (which remaining
sections to look up, how to combine a section-level adjustment with the existing station-level one
without double-counting, how the `RouteSection`-only "current section" limitation
(`RouteProvider`/`LiveDataRouteProvider` still only ever resolve one section, not a full remaining
route) should be addressed, and how a `MOCK`/`mixed(...)`-sourced section profile should affect
confidence). None of those questions were answered or guessed at in this phase - they are Phase
16H's job.

### Remaining limitations after Phase 16G

- Nothing consumes `HistoricalSectionDelayProvider` yet - it exists and is tested in isolation.
- Point-in-time correctness for a past `referenceInstant` is real but slow (on-demand
  re-aggregation, no caching of retrospective results) - acceptable while nothing calls it in
  production; would need revisiting if a future backtesting workload calls it at volume.
- The minimum-sample-count default (`10`) is a reasoned starting point, not a validated one.
- `RouteProvider` still only resolves the single *current* section, not a train's full remaining
  route - a real gap for any future phase wanting to sum section-level adjustments across an
  entire remaining journey (see Phase 16D's own finding, still unresolved).
- Provenance-based acceptability policy (whether a mock/mixed-sourced section profile should ever
  be trusted for a real prediction) is undecided - deliberately left to Phase 16H.

## Phase 16H-1 implementation notes (what's actually built)

Phase 16H-1 solves the prerequisite problem Phase 16G's own "remaining limitations" flagged:
enumerating a train's *remaining* route (not just its current section) and aggregating section
history across it. `PredictionEngine`, `PredictionService`, `HistoricalDelayCalculator`, and the
ETA formula remain completely untouched - nothing here is wired into prediction yet.

### The remaining-route abstraction

Three small domain types, deliberately not a generic routing framework:

- `model.domain.RemainingRouteStop(Station station, Integer sequence, Double distanceFromOriginKm)`
  - one station on the remaining route, exactly as RailRadar reported it (both `sequence` and
    `distanceFromOriginKm` may be `null` - never invented).
- `model.domain.RemainingRouteSection(RouteSection section, Integer fromStationSequence, Integer toStationSequence)`
  - a plain `RouteSection` (the same type `HistoricalSectionDelayProvider` already consumes) plus
    the originating stations' sequence numbers, kept for traceability only - never used to
    determine ordering (see below).
- `model.domain.RemainingRoute(Station currentStation, Station destinationStation, List<RemainingRouteSection> sections, RouteCompleteness completeness)`
  - the ordered remaining sections, plus an explicit completeness signal (see next section).
    `destinationStation` is `null` only when `completeness` is `UNAVAILABLE`; the record's own
    constructor guard enforces this invariant.

`model.domain.RouteCompleteness` is a three-value enum - `COMPLETE`, `PARTIAL`, `UNAVAILABLE` -
exactly the representation the brief's own item 4 suggested, chosen because it distinguishes "we
know the whole remaining route" from "we know some of it" from "we know none of it" without
inventing a fourth ambiguous state.

### Route topology source: `RouteProvider`, extended minimally

`RouteProvider` already had exactly one implementation (`LiveDataRouteProvider`) and exactly one
real consumer (`PredictionService`, unchanged). Rather than introducing a new "route topology
provider" component, `RouteProvider` gained one new method,
`RemainingRoute remainingRoute(LiveTrainData train)` - the smallest correct extension, per the
brief's own instruction to prefer extending an existing capability over inventing a new one when
the existing one can be minimally given enough information.

That "minimal extension" required `LiveTrainData` itself to carry two new fields -
`destinationStation` and `remainingRouteStops` - since `TrainDataProvider.getLiveTrainData(...)`
is the *only* seam through which RailRadar-derived facts reach the rest of the application; there
is no other channel for this information to flow through. To avoid breaking every existing
constructor call site, `LiveTrainData` gained a second, backward-compatible constructor (the exact
same pattern `Station`'s own two-constructor shape already uses) that defaults both new fields to
"unavailable" (`null`/empty) - every pre-existing caller compiles and behaves identically,
unchanged.

`LiveTrainDataMapper` (which already had the only license to touch RailRadar's raw `RouteStop`
data) populates both new fields:

- `destinationStation` - RailRadar's own route array's *last* entry, reusing the exact same trust
  assumption `remainingDistance()` already made before this phase (this is not a new assumption).
  `null` only when that last entry's own station code/name isn't usable.
- `remainingRouteStops` - walks the route array from the current station (inclusive) onward, *in
  the array's own order*, appending one `RemainingRouteStop` per entry until either the array ends
  or an entry with a blank/missing station code or name is reached - at which point the walk
  **stops**, it does not skip the bad entry and continue. Skipping and reconnecting past a gap
  would silently manufacture an adjacency RailRadar never actually confirmed - exactly what the
  brief forbids ("do not infer skipped stations without evidence"). This list is always non-empty
  when `destinationStation` is non-null, since the current station itself is already guaranteed
  present and valid in the route array (an existing, pre-16H-1 invariant:
  `LiveTrainDataMapper` already throws before this point if the current station can't be found).

**A note on trusting array order here versus not trusting it in Phase 16F**: this is not a
contradiction. Phase 16F's raw `historical_observations` rows are *persisted, independently
retrieved* database records with no guaranteed read-back order, so their own `station_sequence`
column is the only trustworthy ordering signal. `route[]` here is a *single RailRadar response*,
already ordered by RailRadar itself before this application ever sees it - the same order
`LiveTrainDataMapper` already relied on (treating the last entry as the destination) before this
phase. Different data, different trust model - documented explicitly so it doesn't read as
inconsistent.

`LiveDataRouteProvider.remainingRoute(train)` then derives `RouteCompleteness` purely from what's
already on `LiveTrainData` - no RailRadar type or new dependency involved:

- `destinationStation == null` → `UNAVAILABLE` (nothing to enumerate at all).
- exactly one captured stop (the current station) and it is *not* the destination → `UNAVAILABLE`
  (a destination is known, but not even one real step toward it could be confirmed).
- the captured stops' *last* entry's station code matches the destination's → `COMPLETE` (this
  also covers the trivial "already arrived" case: one captured stop, which *is* the destination,
  zero sections).
- otherwise → `PARTIAL` - real, honest sections exist (built by pairing consecutive captured
  stops), they just don't reach the confirmed destination.

`HistoricalSectionDelayProvider` was **not** made responsible for any of this - it is only ever
called once a `RouteSection` already exists, exactly as the brief required.

### Historical section aggregation

`historical.RemainingRouteHistoricalAggregator` (a new, plain `@Component`, no JPA/RailRadar
dependency) accepts a train number, a `RemainingRoute`, and a `referenceInstant`, calls
`HistoricalSectionDelayProvider.getSectionDelay(...)` once per remaining section, and combines the
results into one `model.domain.RemainingRouteHistoricalSummary`:

- `sectionResults` - every individual `SectionHistoricalDelayResult`, in route order, always fully
  preserved regardless of status - nothing is discarded or substituted with station-level data.
- `status` (`model.domain.RemainingRouteHistoricalStatus`) - `NO_REMAINING_SECTIONS` (the route
  itself had zero sections - already arrived, or the route was `UNAVAILABLE`),
  `ALL_SECTIONS_AVAILABLE`, `PARTIAL_SECTIONS_AVAILABLE`, or `NO_SECTIONS_AVAILABLE` (sections
  exist, none are `AVAILABLE`) - four explicit states rather than one ambiguous "no data" signal,
  deliberately orthogonal to `RouteCompleteness` (which describes the *topology*, not whether
  *historical data* exists for it).
- `totalDelayChangeMinutes` - the plain signed **sum** (not average) of `averageDelayChangeMinutes`
  over only the `AVAILABLE` sections. Summing was chosen, not averaging, because each section's
  delay change is an independent, additive contribution to how much more (or less) delayed the
  train becomes crossing it - the brief's own worked example (+4, +2, -1 → the signed values must
  be preserved) reads naturally as a running total, not a per-section average. Never clamped,
  never scaled by an arbitrary weight/percentage - both explicitly forbidden by the brief, and
  correctly so: introducing a weighting factor here would be exactly the kind of prediction-layer
  policy decision Phase 16H-1 must not make. **`null`, not `0.0`, when zero sections contributed** -
  a real, computed zero (historically stable) must never be confused with "we have no idea",
  matching the brief's own explicit warning not to silently treat missing data as zero delay.
- `provenance` - `DataProvenance.combine(...)` over only the contributing (`AVAILABLE`) sections'
  own sources; `DataProvenance.UNAVAILABLE` when none contributed. A mock or mixed source among the
  contributors is never hidden, upgraded, or downgraded - proven by dedicated tests using
  `MOCK`-only, `RAILRADAR`-only, and mixed-source fixtures.

`INSUFFICIENT_SAMPLES` and `NOT_FOUND` sections are preserved in `sectionResults` but never
contribute to the sum or the provenance combination - station-level historical data is never
substituted in their place.

### Double-counting: the boundary for Phase 16H-2

This phase does not touch the existing station-level `historicalAdjustmentMinutes` calculation at
all - `HistoricalDelayCalculator`/`PredictionEngine` are unchanged. But the aggregate this phase
produces is deliberately shaped so that Phase 16H-2 can make section history the **replacement**
for the existing station-level historical adjustment, not an **addition** alongside it: there must
ultimately be exactly one historical contribution to a prediction. `RemainingRouteHistoricalSummary`
does not itself decide this - it is Phase 16H-2's explicit job to retire (or gate) the existing
single-section station-level lookup once section-level data is judged trustworthy enough to take
its place, never to layer both as two independent additive corrections.

### Testing

`LiveTrainDataMapperTest` (destination-from-last-entry, full walk when every entry is usable, the
walk stopping at the first unidentifiable entry rather than skipping it, destination itself
unusable, null sequence preserved, a terminated/arrived train's single-element stop list),
`LiveDataRouteProviderTest` (one section, multiple sections, already-at-destination,
partial/incomplete routes, missing sequence preserved, missing distance leaves the section's own
distance `null`, both `UNAVAILABLE` cases, and an explicit "no section is ever fabricated across a
gap" case), `RemainingRouteTest`/`RemainingRouteHistoricalSummaryTest` (constructor invariants),
and `RemainingRouteHistoricalAggregatorTest` (positive/negative/zero/mixed-sign delay changes
summed correctly, all/some/none-available section combinations, `INSUFFICIENT_SAMPLES`/`NOT_FOUND`
preserved but non-contributing, provenance preserved across mock-only/real-only/mixed
combinations, route completeness carried through unchanged). Full regression: all 378 pre-existing
tests re-run unchanged and pass.

### Remaining limitations after Phase 16H-1

- Nothing consumes `RemainingRouteHistoricalSummary` yet - `PredictionEngine`/`PredictionService`
  are completely unaware of it, deliberately.
- A single unidentifiable station anywhere in the remaining route permanently truncates everything
  captured beyond it (the conservative "stop, don't skip" walk) - a real, accepted tradeoff against
  ever fabricating an adjacency, not a bug.
- The double-counting boundary is documented, not enforced by any code yet - Phase 16H-2 must
  actually retire (or gate) the station-level adjustment when it wires section history in.
- No schema/database change was made or needed - this phase is entirely an in-memory/live-data
  concern.

## Phase 16H-2 implementation notes (what's actually built)

Phase 16H-2 is the actual integration: `PredictionEngine`'s `historicalAdjustmentMinutes` is now
sourced from remaining-route section history when usable, and from station-level history only as
an explicit fallback - never both. See docs/prediction-model.md's own "Phase 16H-2" section for the
full formula, the exact warning matrix, and the worked-example-level detail; this section covers
the architectural decisions.

### Baseline re-confirmed before changing anything (item 2)

Re-inspected (not assumed) before any code changed: `HistoricalDelayCalculator.
historicalAdjustmentMinutes(HistoricalDelay)` - `sampleCount < minimumSampleCount ? 0 :
round(averageDelayMinutes * weight)` - entered `PredictionEngine.predict(...)` exactly twice:
once into `predictedTotalDelayMinutes` (summed with current delay and simulated extra delay,
floored at 0) and once into `predictedEta` (added as a duration). Confirmed via `grep` that no
`SimulationContext.historicalDelay()` field is ever read by any disruption model, cascade engine,
or recovery model - it is carried through `SimulationContext` unused by simulation itself, so there
was (and remains) exactly one real historical contribution before this phase, not a hidden second
one inside the simulation. `ConfidenceCalculator` separately reads the same `HistoricalDelay` for
its own scoring factor/warnings, unchanged by this phase (see "Not yet integrated" below).

### The replacement policy (items 3-4)

**Preferred path**: when `RemainingRouteHistoricalSummary.totalDelayChangeMinutes()` is non-null
(`ALL_SECTIONS_AVAILABLE` or `PARTIAL_SECTIONS_AVAILABLE`), the weighted section total *becomes*
`historicalAdjustmentMinutes` - the station-level figure is not computed into the result at all in
that case (it's still fetched, for `ConfidenceCalculator`'s and the fallback path's sake, but never
summed in).

**`PARTIAL_SECTIONS_AVAILABLE` was deliberately accepted as usable**, not treated as a reason to
fall back - a decision made on correctness grounds, not convenience: a partial section-level signal
(computed only from the remaining sections that actually had usable history) is still specific to
*this train's actual remaining route*, whereas the station-level average is a categorically
different statistic (one downstream station's historical arrival delay, not this route's delay
change). Discarding real, if incomplete, route-specific information in favor of a different and
less relevant number would not have been the more "correct" choice. The tradeoff is made visible,
not hidden: a warning states the adjustment covers only N of M remaining sections.

**Fallback path retained, not removed**: station-level history remains the fallback whenever
section history isn't usable (`NO_REMAINING_SECTIONS` or `NO_SECTIONS_AVAILABLE`) -
`HistoricalDelayCalculator`/`HistoricalDelayProvider` were not discarded. This was a deliberate
choice, not an oversight: `MockHistoricalDelayProvider` remains the *default*-active station-level
provider, while section profiles require real accumulated observation data to ever become
`AVAILABLE` - meaning "section history unusable" is the *common* case today. Removing the fallback
entirely would have silently zeroed out historical input for virtually every prediction under
default configuration, a real regression relative to pre-16H-2 behaviour.

### Where the decision is made (item 5)

`PredictionService` orchestrates: it calls `RouteProvider.remainingRoute(train)` and
`RemainingRouteHistoricalAggregator.summarize(trainNumber, remainingRoute, Instant.now(clock))` in
a new private method, wrapped in the same try/catch-and-degrade pattern already used for weather
and station-level history (a failure here degrades to a synthetic "nothing to work with" summary,
never breaks live prediction). `PredictionEngine` receives the already-resolved
`RemainingRouteHistoricalSummary` as a plain parameter - it never discovers route topology, queries
`HistoricalSectionDelayProvider`, or touches a repository/database itself, preserving the existing
architectural boundary (`PredictionEngine`'s own Javadoc: "Does not itself fetch live data, run the
simulation, look up history, or discover route topology"). The actual *replacement decision*
(section vs. station) lives inside `PredictionEngine.predict(...)` - this was a deliberate choice,
not scope creep: choosing which of two already-resolved candidate numbers to use is the same kind
of "combine already-computed pieces" responsibility `PredictionEngine` has always had (it is not
route discovery, provider querying, or database access), and a separate resolver class for a single
`if` would have been over-engineering for what the logic actually needs.

A new `prediction.SectionHistoricalDelayCalculator` component (mirroring
`HistoricalDelayCalculator`'s exact shape) turns the summary into a candidate adjustment, or
`null` when nothing is usable - the null-ness is the signal `PredictionEngine` branches on.

### Simulation architecture untouched (item 6)

No disruption model, `CascadeEngine`, or `RecoveryModel` was touched. `SimulationContext` still
carries the same station-level `HistoricalDelay` it always did (still unread by simulation, exactly
as before). The formula's conceptual shape remains `current delay + simulation/disruption effect +
recovery + ONE historical contribution` - only the *source* of that one contribution now varies.

### Signed semantics preserved exactly (item 7)

`SectionHistoricalDelayCalculator` performs exactly one operation: `round(total * weight)` - no
clamping, no conversion of a negative (recovery) total to zero, no re-averaging of the sections
(they were already summed once, correctly, by `RemainingRouteHistoricalAggregator` in Phase 16H-1),
and no addition of the station-level figure. The existing `prediction.historical-adjustment.weight`
property is reused as-is (see docs/prediction-model.md) - introducing a second, section-specific
weight was considered and rejected, since it would reintroduce two independently-tunable knobs for
what must remain a single historical contribution.

### Testing

`SectionHistoricalDelayCalculatorTest` (null for unusable summaries, weight applied to
positive/negative/zero totals, minimum-sample-count from `HistoricalAdjustmentProperties`
confirmed to have no effect on the section path), an extended `PredictionEngineTest` (section
total replaces, never adds to, the station figure; a negative section total produces a negative
adjustment; partial availability still contributes and warns; mock/mixed section provenance warns;
real provenance does not; `NO_SECTIONS_AVAILABLE` falls back and warns about the fallback;
`NO_REMAINING_SECTIONS` falls back silently; the legacy six-argument overload behaves exactly as
before), and an extended `PredictionServiceTest` (the full station+section orchestration wired
together on the happy path, and section-historical-lookup failure degrading gracefully to the same
synthetic fallback summary `PredictionEngine` itself uses). Full regression: all 412 pre-existing
tests re-run (with only the necessarily-updated constructor/mock-arity churn from the two new
constructor parameters) and pass.

### Not yet integrated - explicit boundary

- `ConfidenceCalculator` is unchanged - its `historicalDataAvailability` factor and related
  warnings still reflect only the station-level `HistoricalDelay`, not whichever source actually
  produced `historicalAdjustmentMinutes`. A prediction whose adjustment came entirely from real
  section data but whose station-level lookup happened to be empty will still show a confidence
  warning ("No historical delay data is available...") that no longer accurately describes what
  was actually used. This is a known, deliberate limitation, not an oversight - changing the
  confidence formula was explicitly out of scope for this phase.
- `PredictionResult`/`PredictionOutput`/the REST contract are unchanged - `historicalAdjustmentMinutes`
  is still one plain `int`, with no field indicating which source produced it. A caller cannot yet
  tell, from the API response alone, whether an adjustment came from section or station history
  (only the warnings array hints at it, informally, in prose).
- Section history remains keyed on plain `RouteSection`s built from whatever
  `RouteProvider.remainingRoute` can reconstruct - the same `RouteCompleteness.PARTIAL`/
  `UNAVAILABLE` limitations from Phase 16H-1 (a single unidentified station truncates everything
  beyond it) apply unchanged to what prediction can actually see.

### Recommended Phase 16H-3

- Surface the *source* of `historicalAdjustmentMinutes` (section vs. station, and its provenance)
  as a first-class field on `PredictionResult`/`PredictionOutput`, so the REST contract stops
  relying on warning-string text for this.
- Feed that same source information into `ConfidenceCalculator`, so a prediction backed by real
  section data is scored differently from one backed by an empty station-level lookup.
- Only after both of the above, consider whether `historical.provider`/`historical.section-provider`
  should default to `postgres` in any deployment profile - today both still default to `mock`,
  unchanged by this phase.

## Phase 16H-3 implementation notes (what's actually built)

Phase 16H-3 closes exactly the gap the previous phase's own recommendation named: both bullets
above ("surface the source as a first-class field" and "feed source information into
`ConfidenceCalculator`") are now implemented. See docs/prediction-model.md's own "Phase 16H-3"
section for the full mechanics; this section covers the domain-design decisions.

### The domain model: two small, deliberately separate concepts

`model.domain.HistoricalAdjustmentSource` (`SECTION`/`STATION_FALLBACK`/`NONE`) and the existing
`DataProvenance` vocabulary (`railradar`/`mock-provider`/`unavailable`/`mixed(...)`) are packaged
together as one small value object, `model.domain.HistoricalAdjustmentResolution(source,
provenance)`, rather than as three loose fields directly on `PredictionResult` or a larger
"prediction metadata" object holding unrelated things. Two fields were enough - no separate
"status" enum was introduced, since `source` itself already answers "was historical data used"
(`!= NONE`) and "which strategy" in one value; adding a third, overlapping field would have been
redundant.

**Never conflated with `DataProvenance`**: `source` answers "which calculation strategy won";
`provenance` answers "where that strategy's data came from." A `SECTION`-sourced adjustment can be
`mock-provider`-provenanced exactly as easily as a `STATION_FALLBACK` one (e.g.
`historical.section-provider=mock` while station-level history happens to be real, or vice versa) -
proven directly by `HistoricalAdjustmentResolutionTest`.

### Exact selection semantics (never inferred from "a route exists" or "station was queried")

- **`SECTION`**: the section-level aggregate actually produced the final adjustment - both
  `ALL_SECTIONS_AVAILABLE` and `PARTIAL_SECTIONS_AVAILABLE` count, provided
  `SectionHistoricalDelayCalculator.sectionAdjustmentMinutes(...)` returned non-null (i.e. the
  summary's `totalDelayChangeMinutes()` was non-null) - never reported merely because a
  `RemainingRoute` happened to resolve.
- **`STATION_FALLBACK`**: section history did not provide a usable adjustment, *and* the
  station-level historical adjustment was itself usable (`HistoricalDelayCalculator.
  historicalAdjustmentMinutes(...)` returned non-null) - never reported merely because the
  station-level provider was queried; a query that comes back below the minimum sample count does
  **not** count as `STATION_FALLBACK`.
- **`NONE`**: neither contributed. `historicalAdjustmentMinutes` is `0` in this case, but that `0`
  is fabricated *because there was nothing to use*, not because a real average happened to be
  zero - the distinction Phase 16H-2 already cared about for `STATION_FALLBACK` vs. its own
  station-level `0`, now made precise for `NONE` as well.

This distinction required changing `HistoricalDelayCalculator.historicalAdjustmentMinutes(...)`
from returning `int` to `Integer` (`null` below the minimum sample count) - mirroring
`SectionHistoricalDelayCalculator`'s own contract exactly. Before this change, "insufficient
samples" and "the real station-level average genuinely happens to be zero" were indistinguishable
(both `0`), which made `STATION_FALLBACK` vs. `NONE` impossible to tell apart correctly.

### Provenance is never invented (item 5)

- `SECTION` → `RemainingRouteHistoricalSummary.provenance()`, unchanged, exactly as Phase 16H-1
  computed it (already correctly `DataProvenance.combine(...)`-based, mixed sources never hidden).
- `STATION_FALLBACK` → the station-level `HistoricalDelay.source()`, unchanged.
- `NONE` → `DataProvenance.UNAVAILABLE` - the existing constant, not a new vocabulary term.

No `MOCK` value is ever silently reported as `RAILRADAR`, in either source branch - proven by
dedicated tests covering section+mock, section+mixed, and station-fallback+mock combinations.

### `ConfidenceCalculator` - a factual-accuracy fix, not new confidence mathematics

`ConfidenceCalculator.calculate(...)` now takes `HistoricalAdjustmentResolution
historicalResolution` in place of the raw station-level `HistoricalDelay` (which had become
otherwise-unused inside that method once this change was made). The `historicalDataAvailability`
factor's condition changed from `historicalDelay.sampleCount() > 0` to `historicalResolution.
source() != HistoricalAdjustmentSource.NONE`, and the mock-provenance warning check changed from
`historicalDelay.source()` to `historicalResolution.provenance()` - both are corrections to *which
existing signal* decides an already-existing factor/warning, not new weights, new thresholds, or a
new scoring formula. `prediction.confidence.weights.*`/`prediction.confidence.thresholds.*` and the
`achieved / possible` calculation are byte-for-byte unchanged - verified by re-running the full
pre-existing `ConfidenceCalculatorTest` suite unmodified in its scoring assertions (only the
parameter each test builds changed, from a `HistoricalDelay` to an equivalent
`HistoricalAdjustmentResolution`).

### `PredictionEngine`/`PredictionService` - no change to the replacement policy or arithmetic

`PredictionEngine.resolveHistoricalAdjustment(...)` is a direct, mechanical restatement of Phase
16H-2's own `if section usable → section; else if station usable → station; else → none` policy -
now additionally returning which branch was taken, packaged with the winning adjustment. The
arithmetic downstream (`predictedTotalDelayMinutes`, `predictedEta`) is untouched: both still add
`historicalAdjustmentMinutes` in exactly the same two places, exactly once, regardless of source -
proven by a new worked-example test that deliberately gives station-level history the *same*
numeric result the section aggregate would have produced, confirming the figure is applied exactly
once no matter which branch supplied it. `PredictionService` required no change at all - it already
passed `historicalDelay` and `sectionHistoricalSummary` through to `PredictionEngine`; the
resolution is computed entirely inside `PredictionEngine` from information `PredictionService`
already provided.

### Output contract

`model.dto.PredictionBreakdownResponse` gained two fields, `historicalAdjustmentSource`
(the `HistoricalAdjustmentSource` enum itself, serialized as its name - consistent with how
`TrainStatus`/`SectionType` are already exposed elsewhere in the same output tree) and
`historicalAdjustmentProvenance` (a plain string, consistent with `DataProvenance`'s own type) -
sitting directly alongside the existing `historicalAdjustmentMinutes`, per the brief's own
suggested shape. No existing field was removed, renamed, or restructured; no new nested object was
introduced (a full "historical" sub-object was considered and rejected as unnecessary churn for
two fields that fit naturally into the existing breakdown). No implementation class or provider
name is ever exposed - only the enum name and the existing free-form provenance string.

### Backward compatibility

`PredictionResult` gained a 21st field, `historicalAdjustmentResolution` - via the same
backward-compatible secondary-constructor pattern used for `LiveTrainData` (Phase 16H-1) and
`RemainingRoute`'s siblings: the pre-16H-3, 20-argument constructor is preserved and delegates to
the new canonical one with `HistoricalAdjustmentResolution(NONE, DataProvenance.UNAVAILABLE)` -
every existing call site (`DomainFixtures`, `PredictionServiceTest`, `PredictionControllerTest`,
`PredictionResultTest`) compiles and passes unchanged, except where a test specifically wanted to
exercise the new field.

### Testing

`HistoricalAdjustmentResolutionTest` (source/provenance vary independently, guard rejects
null/blank), extended `PredictionResultTest` (legacy constructor defaults to `NONE`/`unavailable`,
canonical constructor preserves an explicit resolution, `null` resolution rejected), extended
`HistoricalDelayCalculatorTest` (below-minimum now returns `null`, not `0`), extended
`ConfidenceCalculatorTest` (every case rebuilt against `HistoricalAdjustmentResolution`, plus new
`SECTION`-source and `NONE`-source cases), extended `PredictionEngineTest` (source/provenance
asserted for `SECTION` with `ALL_SECTIONS_AVAILABLE`/`PARTIAL_SECTIONS_AVAILABLE`,
`STATION_FALLBACK`, `NONE`, mixed and mock provenance preserved exactly, the arithmetic-regression
worked example, and the legacy six-argument overload's `STATION_FALLBACK` reporting), extended
`PredictionOutputMapperTest`/`PredictionOutputSerializationTest`/`PredictionControllerTest` (the
two new fields map and serialize correctly, and are visible in the actual REST JSON response).
Full regression: all 429 pre-existing tests re-run (with only the necessarily-updated
`ConfidenceCalculator`/`HistoricalDelayCalculator` call-site churn from their signature changes)
and pass.

### Remaining limitations after Phase 16H-3

- `RouteProvider.remainingRoute`'s own `PARTIAL`/`UNAVAILABLE` limitations (Phase 16H-1) are
  unchanged - the source/provenance transparency this phase adds describes *which* strategy won,
  it doesn't improve what either strategy can actually see.
- The confidence *formula* itself (weights, thresholds, the achieved/possible calculation) is
  unchanged - only the historical-availability factor's underlying condition was corrected. Whether
  `SECTION` vs. `STATION_FALLBACK` should themselves carry different confidence weight is an open
  question this phase deliberately did not answer (that would be new confidence mathematics, out
  of scope here).
- `historical.provider`/`historical.section-provider` still both default to `mock` - nothing in
  this phase changes production behaviour by itself.

### Recommended Phase 16H-4

- Now that `HistoricalAdjustmentSource` exists as a first-class signal, consider whether
  `ConfidenceCalculator` should weight `SECTION` differently from `STATION_FALLBACK` (a genuine
  confidence-mathematics question, deliberately deferred here).
- Consider whether `docs/prediction_output.json` (the Phase 14 generated example) and any frontend
  consuming this API should start rendering `historicalAdjustmentSource`/`historicalAdjustmentProvenance`
  directly, now that they exist.
- Revisit whether `historical.provider`/`historical.section-provider` should move off `mock` in any
  real deployment profile, now that the pipeline can explain exactly which source it used.

## Phase 16H-4 implementation notes (what's actually built)

Phase 16H-4 answers the question Phase 16H-3's own recommendation raised: whether
`ConfidenceCalculator` should score `SECTION` differently from `STATION_FALLBACK`. See
docs/prediction-model.md's own "Phase 16H-4" section for the full reasoning; this section
summarizes the decision and what changed in code.

### The policy decision

| Question | Decision |
|---|---|
| SECTION vs. STATION_FALLBACK numeric confidence | **Identical** - no multiplier, no separate weight |
| NONE | Unchanged - no achieved weight, existing "no historical data" warning |
| Partial section coverage (`PARTIAL_SECTIONS_AVAILABLE`) | **Deferred** - not scored numerically; status/counts remain fully visible for future calibration |
| Provenance (RAILRADAR/MOCK/mixed/UNAVAILABLE) | Score unaffected by any of the four; **warning detection corrected** to also catch `mixed(...)` composites, not just an exact `MOCK` match |
| Sample count (station or section minimum) | Remains a pure operational gate (usable vs. not) - never an additional numeric confidence input |

**Why no numeric distinction**: a real asymmetry between the two *does* exist and is already
visible in this codebase - `historical.section.minimum-sample-count` (default `10`) is double
`prediction.historical-adjustment.minimum-sample-count` (default `5`), for the reasoned,
already-documented reason that a delay-*change* statistic (section) has variance equal to the
*sum* of its two contributing quantities' variances, requiring more samples for equivalent
confidence (Phase 16G). But this asymmetry is fully spent by the time either source reaches
`ConfidenceCalculator`: each source only ever reaches this calculator's `!= NONE` branch after
already clearing its *own* (different) minimum-sample bar. Applying a further discount to
`SECTION` here would be double-counting a threshold already enforced upstream, not incorporating
new information. No accuracy-tracking/backtesting infrastructure exists in this codebase (confirmed
absent by inspection, unchanged from every prior phase) from which any specific multiplier could be
derived - so none was invented.

**Why partial coverage isn't scored**: `RemainingRouteHistoricalSummary.availableSectionCount()`
and `.sectionResults()` already let a consumer see exactly how much of the remaining route
contributed, and `PredictionEngine`'s own partial-coverage warning already discloses it in prose.
Turning "N of M sections" into a confidence number would require knowing (or assuming) how
partial coverage relates to actual prediction error - unmeasured, so left unscored rather than
guessed.

### The one real code change: mixed-provenance warning detection

`ConfidenceCalculator`'s mock-warning condition changed from
`DataProvenance.MOCK.equals(historicalResolution.provenance())` to
`historicalResolution.provenance().contains(DataProvenance.MOCK)`. This is a factual-accuracy fix:
a `mixed(mock-provider,railradar)` composite (produced by `DataProvenance.combine(...)` whenever a
section aggregate blends samples from different sources, Phase 16F) is **partially** mock-derived,
and the exact-equality check was silently letting it pass as if fully real. The warning text itself
was also generalized from a hardcoded `"(mock-provider)"` to include the actual provenance value,
so a mixed case reads correctly (e.g. `"...simulated (mixed(mock-provider,railradar))..."`). The
score contribution is completely unaffected either way - available data (mock, mixed, or real)
still satisfies `historicalDataAvailability` identically; only the warning text changed.

No other line in `ConfidenceCalculator` changed. `PredictionEngine`'s own analogous
non-`RAILRADAR` check (used for its separate `SECTION`-specific "simulated or mixed" warning) was
already correct before this phase - it uses `!DataProvenance.RAILRADAR.equals(...)`, an
"is-not-real" check that already covers `mixed(...)` correctly; only `ConfidenceCalculator`'s
narrower "is-exactly-MOCK" check had the gap.

### Testing

Full regression: all 440 pre-existing tests re-run **completely unchanged** and pass - proving the
scoring formula, weights, and thresholds are byte-for-byte identical to before this phase (no test
needed to change its expected score). New tests added:
`sectionAndStationFallbackReceiveIdenticalScoresUnderOtherwiseIdenticalConditions` and
...`WhenBothAreMockSourced` (the core policy proof - same score, same warnings, real-for-real and
mock-for-mock), `mixedProvenanceIsFlaggedAsPartiallySimulatedEvenThoughItIsNotExactlyMock` (proves
the corrected detection), and `unavailableProvenanceUnderNoneDoesNotAlsoTriggerTheMockWarning`
(proves the fix didn't overreach into the `NONE`/`UNAVAILABLE` case).

### Remaining limitations after Phase 16H-4

- No numeric confidence distinction exists between `SECTION` and `STATION_FALLBACK`, or for
  partial section coverage - both remain open until real accuracy-tracking data exists to justify
  specific values.
- `historical.provider`/`historical.section-provider` still both default to `mock` - unchanged by
  this phase.
- No new configuration was added, since no numeric policy required one.

### Recommended next phase

Nothing in the historical-confidence area remains actionable without new evidence (real
accuracy/backtesting data). A natural next direction outside this area: begin the accuracy-tracking
infrastructure itself (recording predicted vs. actual outcomes) - only once that exists would a
future phase have the evidence this one explicitly declined to fabricate.

## Phase 16H-5 implementation notes: accuracy tracking / backtesting foundation

Phase 16H-5 builds exactly the infrastructure Phase 16H-4's own recommendation named: the ability
to record what was predicted and later check it against what actually happened. It does **not**
change the ETA formula, historical weighting, confidence formula, or simulation - this phase
*measures* the existing model, it does not *optimize* it.

### Prediction target: the next station, not the destination

`PredictionResult`/`PredictionEngine` predict to the **destination** (`baseTravelTimeMinutes` is
computed from `remainingDistanceKm`, the distance to the *final* station - unchanged since Phase
11). But Phase 16H-1 already established that RailRadar exposes no reliable
whole-journey-completion signal - there is no trustworthy way to detect "this train has arrived at
its final destination" at all, only station-level completion
(`RouteStop.actualArrival() != null`). Trying to match a destination-level prediction against a
"destination reached" event would therefore be evaluating against a signal this system has never
been able to trust - exactly the "fake destination-level evaluation" the brief warned against.

**Chosen target: the train's own next station** (`train.nextStation()` /
`section.toStation()` at prediction time) - the same station-level completion signal
`HistoricalObservationRecorder` already persists reliably every time RailRadar reports the train
has reached it. This is deliberately a *narrower* evaluation than the full prediction, but it is
the largest target that can currently be evaluated honestly.

**The evaluated predicted value is deliberately NOT `predictedTotalDelayMinutes`.** That figure is
destination-scoped and includes the historical adjustment, whose spatial scope (for a `SECTION`-
sourced adjustment, Phase 16H-2) spans the *entire remaining route*, not just the next station -
using it here would over-attribute a route-wide signal to a single-station outcome. Instead:

```
predictedNextStationDelayMinutes = currentDelayMinutes + predictedExtraDelayMinutes
```

- the only two components of the existing formula whose scope reliably matches "just the current
section, ending at the next station," regardless of which historical strategy was used (the
historical adjustment is excluded from the evaluated quantity entirely, for exactly this reason).
`predictedTotalDelayMinutes`/`predictedEta` are still captured on the snapshot for audit/reference,
but are not evaluated by this phase.

### Actual outcome: `arrivalDelayMinutes`, never absolute clock time

`HistoricalObservation.actualArrival`/`scheduledArrival` remain opaque, unparsed strings (their
format/timezone was never confirmed against a real response - unchanged since Phase 16A). This
means "actual ETA" as an absolute `Instant` **cannot be reliably computed at all** - there is no
honest way to compare `predictedEta` against a real clock time. The actual outcome used instead is
`HistoricalObservation.arrivalDelayMinutes` - a structured integer RailRadar itself pre-computes and
reports, never derived by parsing the opaque strings. This sidesteps the timestamp problem
entirely: **the evaluation is delay-based (minutes), not clock-time-based**, which is both reliable
*and* what the prediction's own evaluated quantity already is.

### Journey identity - `EXACT` vs. `APPROXIMATE`, never fabricated

The standing limitation is unchanged from Phase 16E: `journeyDate` is an observation-date
convention, not a verified physical-journey identifier, and no other field (station sequence,
train number alone) can substitute for one. Rather than pretending a match is always certain,
`PredictionEvaluationStatus` carries the uncertainty explicitly:

| Status | Meaning |
|---|---|
| `PENDING` | No matching future observation exists yet - re-checked on a later pass |
| `EVALUATED_EXACT` | Exactly one candidate observation found - unambiguous |
| `EVALUATED_APPROXIMATE` | More than one candidate found (e.g. the same service ran again before this snapshot was evaluated) - the *earliest* is used as the best guess, but which physical journey it truly belongs to is not certain |
| `NOT_EVALUABLE` | Structurally impossible to evaluate (no target station was known at prediction time) - never assigned merely because no observation has appeared yet |

### Leakage prevention (mandatory, verified by tests)

An observation is only ever considered a candidate outcome when
`observation.observedAt().isAfter(snapshot.predictionMadeAt())` - **strictly** after, so an
observation recorded at the exact same instant as the prediction is conservatively excluded too.
`PredictionOutcomeMatcherTest` proves directly that an observation existing *before* (or at exactly)
the prediction moment can never be used as its outcome, regardless of how well it would otherwise
match. `observedAt` (not the opaque schedule/actual-time strings) is the only timestamp trusted for
this comparison - the same "application observed/persisted this fact at this moment" semantics
`HistoricalObservation` has carried since Phase 16C, applied here for a new purpose.

### Can the existing point-in-time raw-observation aggregation be reused for backtesting?

**This phase does not need to answer this for its own evaluation loop** - comparing a prediction's
already-computed output against a later real observation requires no historical-profile
reconstruction at all; the prediction's own inputs were already computed by the live system using
whatever data existed at prediction time. But the question matters for a *different*, larger
feature this phase deliberately does not build: retrospective simulation ("if we pretend we're back
on 2026-09-01, what would our model have said using only data available then"). For that:

- **`SECTION`**: yes, directly reusable - `HistoricalSectionDelayProfileAggregator.aggregate(trainNumber,
  observations, referenceInstant)` already accepts a cutoff and is already used this way by
  `PostgresSectionHistoricalDelayProvider`'s own on-demand path (Phase 16G).
- **`STATION_FALLBACK`**: only *partially* reusable - `HistoricalDelayProfileAggregator.aggregate(...)`
  has **no cutoff parameter at all**; a caller would have to pre-filter the observation list to
  `observedAt <= referenceInstant` *before* calling it. This is possible without modifying the
  aggregator, but is real, extra work this phase does not implement (changing that aggregator's
  signature would ripple into its existing Phase 16B production callers, and is unrelated
  refactoring for a foundation phase).

This asymmetry is documented, not fixed - a future retrospective-backtesting phase must account
for it explicitly rather than assuming both paths are equally ready.

### Prediction snapshot

New table `prediction_snapshots` (Flyway `V4`), new
`repository.PredictionSnapshotEntity`/`Repository`/`EntityMapper`, and the framework-free
`model.domain.PredictionSnapshot`. Deliberately **not** a copy of the full live train payload -
only the fields needed for evaluation and audit: identity (`trainNumber`, `predictionMadeAt`,
`targetStationCode`), the evaluated prediction (`currentDelayMinutes`,
`predictedNextStationDelayMinutes`), audit-only figures (`predictedTotalDelayMinutes`,
`predictedEta`), the already-existing historical-transparency fields (Phase 16H-3:
`historicalAdjustmentMinutes`/`historicalAdjustmentSource`/`historicalAdjustmentProvenance`),
`confidenceScore`, and the outcome fields (`evaluationStatus`, `actualDelayMinutes`,
`errorMinutes`, `evaluatedAt`) - `null` until evaluated, enforced by the domain record's own
constructor guard. `predictionMadeAt`/`evaluatedAt` are genuine `Instant`s (this application's own
`Clock`-stamped values, never opaque strings) - unlike RailRadar's own timestamps, there is no
format ambiguity here since the application generates them itself.

A snapshot is created once and is otherwise immutable - only `PredictionEvaluationRefresher` ever
mutates a row afterward, filling in the outcome columns via `PredictionSnapshotEntity.applyEvaluation(...)`.

### Recording - best-effort, disabled by default

`com.railpredictor.evaluation.PredictionSnapshotRecorder` mirrors `HistoricalObservationRecorder`'s exact
pattern: optional repository, best-effort (a failure is logged and swallowed, never breaks live
prediction), and gated behind a new property, `prediction.evaluation.enabled` (default `false`) -
recording a snapshot on every live request is a genuinely new persistence side effect, and this
codebase's own convention (e.g. `historical.provider` defaulting to `mock`) is to require explicit
opt-in for that rather than silently starting to write extra rows. No snapshot is recorded at all
when the train has no next station (nothing to evaluate) - the ordinary "already arrived" case, not
a failure.

### Matching and evaluation - offline, never inside the live request

`evaluation.PredictionOutcomeMatcher` is a pure function (`PredictionSnapshot`,
`List<HistoricalObservation>) -> PredictionSnapshot` - no JPA, no repository access, fully unit-
tested in isolation. `evaluation.PredictionEvaluationRefresher` bridges it to persistence (loads
every `PENDING` snapshot, looks up candidates via the *already-existing*
`HistoricalObservationRepository.findByTrainNumberAndStationCode` - no new query needed there),
mirroring `HistoricalSectionDelayProfileRefresher`'s exact split between pure aggregation and
persistence-aware orchestration. `evaluation.PredictionEvaluationRefreshScheduler` is the only
caller, `@Scheduled` on its own configurable interval (`prediction.evaluation.refresh-interval-ms`/
`refresh-initial-delay-ms`, mirroring `historical.profile-refresh.*` exactly) - evaluation never
runs inside the live REST prediction path, keeping that path's latency and failure modes unchanged
regardless of how much evaluation work exists.

### Metrics

`evaluation.PredictionAccuracyCalculator` computes `model.domain.PredictionAccuracyMetrics` from a
plain `List<Integer>` of signed errors - `error = predicted - actual`, never re-derived or
resigned:

- **MAE** = `mean(abs(error))` - typical error magnitude, ignoring direction.
- **RMSE** = `sqrt(mean(error^2))` - like MAE but penalizes large errors more heavily.
- **Bias** = `mean(error)` - systematic over/under-prediction; near zero means errors roughly
  cancel out, not that individual predictions were accurate.

An empty input produces `sampleCount = 0` and `0.0` for every statistic - a placeholder, matching
this codebase's existing empty-data convention, never a fabricated "zero error."

### Baseline

`evaluation.PredictionAccuracyReportBuilder` computes `model.domain.PredictionAccuracyComparison` -
the current model's metrics alongside a **current-delay-only baseline**'s, over the same evaluated
snapshots. The baseline requires no new prediction call or infrastructure: every snapshot already
stores `currentDelayMinutes` and (once evaluated) `actualDelayMinutes`, so the baseline's own error
(`currentDelayMinutes - actualDelayMinutes`) is computed directly from already-persisted fields -
"what accuracy would look like if the system simply assumed the train's delay never changes." A
future model variant (e.g. once weather is added) can be compared the same way, by adding its own
predicted-value field to the snapshot and reusing the same calculator - this comparison's shape
does not need to change for that. Only `EVALUATED_EXACT`/`EVALUATED_APPROXIMATE` snapshots
contribute; `PENDING`/`NOT_EVALUABLE` ones are silently excluded, never treated as zero-error.

### Testing

`PredictionSnapshotTest` (evaluated-vs-pending field invariants), `PredictionOutcomeMatcherTest`
(exact/wrong train/wrong station/leakage-before/leakage-at-exact-instant/after/ambiguous-multiple/
missing/no-delay-figure/already-evaluated/duplicate-observation cases - the leakage tests are the
most load-bearing here), `PredictionAccuracyCalculatorTest` (zero/positive/negative/mixed errors,
MAE, RMSE, bias, empty dataset), `PredictionAccuracyReportBuilderTest` (current-model vs. baseline
computed independently, pending/not-evaluable excluded), `PredictionSnapshotRecorderTest`
(disabled-by-default, no-repository, no-next-station, successful recording, the
next-station-scoped predicted value computed correctly and distinctly from the total, persistence
failure swallowed), `PredictionEvaluationRefresherTest` (missing repositories throw, evaluates a
matching pending snapshot, leaves a genuinely-pending one alone, no observation lookups when
nothing is pending), `PredictionEvaluationRefreshSchedulerTest` (disabled/no-repository no-ops,
calls the refresher when configured, a refresher failure is caught), and
`PredictionSnapshotRepositoryTest` (H2, save/retrieve, status-based queries, in-place evaluation
update). Full regression: all 444 pre-existing tests re-run **completely unchanged** and pass -
`PredictionService`/`PredictionOutputGenerationTest` needed only the mechanical addition of the new
`PredictionSnapshotRecorder` constructor parameter, no behavioural assertion changed.

### Remaining limitations after Phase 16H-5

- Only next-station accuracy can currently be measured - destination-level accuracy remains
  unevaluable until (if ever) a reliable whole-journey-completion signal exists.
- `STATION_FALLBACK`'s own historical inputs cannot yet be reconstructed point-in-time for a true
  retrospective backtest (`HistoricalDelayProfileAggregator` has no cutoff parameter) - `SECTION`'s
  can. This phase's own evaluation loop doesn't need this (see above), but a future retrospective-
  simulation phase would.
- `EVALUATED_APPROXIMATE` results use a heuristic (earliest candidate) that is not provably
  correct - a genuine journey-identity limitation, not a bug, carried over from Phase 16E.
- Evaluation is disabled by default (`prediction.evaluation.enabled=false`) - no snapshots
  accumulate, and therefore no real accuracy data exists, until an operator explicitly opts in.
- No report/dashboard endpoint was added (explicitly out of scope) - `PredictionAccuracyReportBuilder`
  exists and is tested, but nothing yet calls it against real persisted data.

### Recommended next phase

Once evaluation has been enabled in a real deployment for long enough to accumulate a meaningful
sample of `EVALUATED_EXACT`/`EVALUATED_APPROXIMATE` snapshots, the natural next step is to actually
*look at* `PredictionAccuracyReportBuilder`'s output (via a small on-demand tool, not necessarily a
full dashboard) and use it as the evidence Phase 16H-4 explicitly deferred needing - only then would
recalibrating confidence, historical weighting, or comparing `SECTION` against `STATION_FALLBACK`
be grounded in measurement rather than assumption.

## Phase 16H-7 implementation notes: data collection integrity and backtest readiness

Phase 16H-7's goal was **not** to build historical replay, but to (a) close or explicitly bound the
Phase 16H-5 `STATION_FALLBACK` point-in-time gap named above, (b) make every evaluation record
self-describing about which process produced it, and (c) determine honestly whether the system is
actually ready for retrospective backtesting yet. It changes no prediction mathematics.

### Live evaluation vs. historical backtest - not the same thing

These two are easy to conflate and this phase deliberately keeps them distinct:

- **Live evaluation** (everything this application does today, Phase 16H-5 onward): a prediction is
  made *now*, by the real `PredictionService` request path, using whatever live train state,
  historical profiles, weather, and route information genuinely exist at that moment. Its outcome
  is checked later, once a real `HistoricalObservation` appears. The prediction's own inputs were
  never reconstructed - they're simply whatever the live system actually used.
- **Historical backtest**: a prediction *reconstructed* as if it had been made at some earlier
  timestamp T, using only information that would genuinely have been available at T - not just
  T-cutoff historical profiles, but T-cutoff `LiveTrainData` (train position, delay, section),
  T-cutoff route resolution, and T-cutoff weather/disruption context. This is a strictly stronger
  requirement than "the historical profile supports a cutoff" - see the readiness assessment below.

A live evaluation whose historical inputs *happen* to be reconstructable point-in-time (see next
section) is still a live evaluation, not a backtest - cutoff-capable aggregation is a necessary
ingredient for a backtest, never sufficient by itself, and this phase never conflates the two.
`model.domain.PredictionEvaluationMode` (`LIVE_EVALUATION`/`HISTORICAL_BACKTEST`) makes this
explicit on every snapshot - see below.

### Closing the station-level point-in-time gap (partially)

Phase 16H-5 documented: *"`STATION_FALLBACK`'s own historical inputs cannot yet be reconstructed
point-in-time... `HistoricalDelayProfileAggregator` has no cutoff parameter at all."* This phase
adds exactly that capability, at the aggregator level, mirroring
`HistoricalSectionDelayProfileAggregator` precisely:

```java
public HistoricalDelayProfile aggregate(String trainNumber, String stationCode,
        List<HistoricalObservation> observations, Instant referenceInstant)
```

Semantics are identical to the section aggregator's own, proven convention: an observation is
included only when `!observation.observedAt().isAfter(referenceInstant)` - the cutoff instant
itself is inclusive, and nothing recorded strictly after it can ever influence the returned
profile. The pre-existing 3-argument `aggregate(trainNumber, stationCode, observations)` overload
is preserved unchanged (delegating to the new one with `Instant.now(clock)`), so
`HistoricalDelayProfileRefresher` - which only ever wants "as of right now" - needed no changes at
all, and no existing test needed to change.

**This closes the aggregation-level asymmetry, not the whole point-in-time story.** Unlike
`PostgresSectionHistoricalDelayProvider` (Phase 16G), which already re-aggregates raw observations
on demand whenever `referenceInstant` is genuinely in the past,
`PostgresHistoricalDelayProvider` still only ever reads the single always-current materialized
`historical_delay_profiles` row - this phase does **not** add an equivalent on-demand path to it.
Doing so would mean changing `HistoricalDelayProvider`'s interface signature (adding
`referenceInstant`) and its one production caller, `PredictionService` - which is squarely inside
the live prediction request path this phase was explicitly told not to touch ("this phase is about
evaluation integrity, not model improvement"). A caller wanting a genuinely point-in-time
station-level profile today must call `HistoricalDelayProfileAggregator` directly against raw
`HistoricalObservationRepository.findByTrainNumberAndStationCode` results, exactly as
`PostgresSectionHistoricalDelayProvider` already does for sections - nothing wires this up
automatically yet. Extending `HistoricalDelayProvider` itself to support a `referenceInstant`
parameter, mirroring `HistoricalSectionDelayProvider`'s own shape, is the natural next increment,
deferred to a future phase that is explicitly scoped to touch the live provider chain.

### Computed-at is not a substitute for a cutoff

Explicitly documented, since this is easy to get wrong: a profile with `computedAt =
2026-09-10T09:00:00Z` (built from every observation that existed *when it was last refreshed*)
cannot answer "what would the historical station profile have looked like on 2026-09-01" - the
materialized row was built with whatever observations existed at *refresh* time, which includes
everything up to `computedAt`, not a `2026-09-01` cutoff. Only a profile explicitly re-aggregated
with `referenceInstant = 2026-09-01T00:00:00Z` (via the new 4-argument `aggregate(...)` overload,
using raw observations) answers that question correctly. `HistoricalDelayProfile.computedAt()` and
`HistoricalSectionDelayProfile.computedAt()` remain exactly what they always were - "when this row
was last (re)computed" - and neither is ever treated as a proxy for "and therefore reflects
knowledge only up to some earlier date."

### Station vs. section parity - and the one remaining difference

| | Station (`HistoricalDelayProfileAggregator`) | Section (`HistoricalSectionDelayProfileAggregator`) |
|---|---|---|
| Cutoff rule | `!observedAt().isAfter(referenceInstant)` | `!observedAt().isAfter(referenceInstant)` (unchanged, Phase 16F) |
| Cutoff inclusive? | Yes | Yes |
| Cutoff-aware overload exists? | Yes (Phase 16H-7, new) | Yes (Phase 16F) |
| Provider re-aggregates on demand for a past `referenceInstant`? | **No** - `PostgresHistoricalDelayProvider` always reads the cache | **Yes** - `PostgresSectionHistoricalDelayProvider` (Phase 16G) |
| Provider interface even accepts a `referenceInstant`? | **No** - `HistoricalDelayProvider.getHistoricalDelay(trainNumber, section)` has no such parameter | Yes - `HistoricalSectionDelayProvider.getSectionDelay(trainNumber, section, referenceInstant)` |

The remaining difference is entirely at the provider/interface layer, not the aggregation layer -
and it is a deliberate scope boundary of this phase (see above), not an oversight.

### Evaluation mode - identifying which process produced a snapshot

New `model.domain.PredictionEvaluationMode`: `LIVE_EVALUATION` (the only value any code path
currently produces - `PredictionSnapshotRecorder` always stamps it, since it is only ever called
from the live `PredictionService` request path) and `HISTORICAL_BACKTEST` (reserved - defined so a
future phase that implements true historical replay has an explicit, unambiguous way to mark its
output, never fabricated or silently defaulted onto data that isn't actually that). Added to
`PredictionSnapshot` as its 17th field via the same backward-compatible-secondary-constructor
pattern this codebase uses for every widely-referenced record (`PredictionResult`, `LiveTrainData`,
`Station`) - the pre-existing 16-argument constructor defaults it to `LIVE_EVALUATION`, correct for
every snapshot ever created before this phase. `PredictionSnapshotEntity` gained the matching
`evaluation_mode` column (Flyway `V5`, `NOT NULL DEFAULT 'LIVE_EVALUATION'` - correct for every
existing row and, for now, every future one) via the identical backward-compatible-constructor
approach, so no existing test needed to change to keep compiling.

**Preserved, never recomputed, through evaluation.** `PredictionOutcomeMatcher.evaluate(...)`
reconstructs a new `PredictionSnapshot` when an outcome is found - it now explicitly carries
`snapshot.evaluationMode()` through unchanged onto the result, exactly like
`historicalAdjustmentSource`/`historicalAdjustmentProvenance` already were. (This was a real,
fixed-in-this-phase bug risk: naively using the pre-existing 16-argument constructor there would
have silently reset every evaluated snapshot's mode back to the `LIVE_EVALUATION` default,
regardless of what it actually was - caught and corrected before it could ever matter, since no
`HISTORICAL_BACKTEST` snapshot exists yet, but exactly the kind of silent-mislabeling bug this
phase's own "the snapshot is the audit record" principle exists to prevent.)

**Report visibility.** `PredictionAccuracyReportBuilder.buildReport(...)` now also produces
`byEvaluationMode : Map<PredictionEvaluationMode, PredictionAccuracySlice>` - always both keys,
even though `HISTORICAL_BACKTEST` is currently always zero-sample, so live-evaluation evidence and
(if a future phase ever produces it) historical-backtest evidence can never be silently blended
into one number without the split being visible. No new repository query or filter dimension was
added for evaluation mode - with only one mode ever producible, a filter would be dead code; this
is a deliberate, minimal scope decision, easily extended once a second mode genuinely exists.

### Backtest readiness assessment: NOT_READY

Phase 16H-7 explicitly evaluated whether full historical replay - reconstructing everything a
prediction needs as of an arbitrary past timestamp T - is achievable with what this application
currently persists, and concluded **no**:

| Input a backtest would need at T | Currently reconstructable as of an arbitrary past T? |
|---|---|
| Historical station/section delay profiles | **Yes** (this phase + Phase 16F/16G) - both aggregators accept a cutoff |
| `LiveTrainData` (train's position, current delay, section, remaining route) as it stood at T | **No** - only the *current* live snapshot is ever fetched from RailRadar; nothing archives a train's live state over time |
| Route/remaining-route resolution as it stood at T | **No** - depends on the live snapshot above |
| Weather as it stood at T | **No** - `WeatherProvider` is queried live, current-conditions only; nothing is archived |
| Disruption/simulation context as it stood at T | **No** - simulation runs against the current live snapshot; there is no record of what the simulation engine would have produced for an earlier, different live state |

Even with both historical aggregators now genuinely cutoff-capable, a true backtest would require
reconstructing a train's entire live operating picture at an arbitrary past instant - something
this application has never archived and was never asked to build before this phase. Building that
archive (a time-series of `LiveTrainData`/route/weather snapshots) is a substantial new capability,
squarely outside this phase's explicit scope ("do NOT implement... historical weight optimization,
automatic tuning... large-scale performance optimization" and, implicitly, new archival
infrastructure this size). Per this phase's own instructions, this conclusion is reported
explicitly rather than an invalid partial backtest being fabricated:

> **Live evaluation is production-capable (has been since Phase 16H-5, refined here); full
> historical replay remains unavailable pending a future phase that archives point-in-time live
> train/route/weather state.**

### Idempotent evaluation - already true, now proven directly

`PredictionEvaluationRefresher.evaluatePendingSnapshots()` was already idempotent by construction
(Phase 16H-5): it only ever loads snapshots the repository currently reports as `PENDING`, and
`PredictionOutcomeMatcher.evaluate(...)` returns any non-`PENDING` snapshot completely unchanged.
Once a real database has applied the first run's `applyEvaluation(...)` update, that row is no
longer `PENDING` and a second run's own `findByEvaluationStatus("PENDING")` query would not return
it - there was never a re-evaluation path to guard against. This phase adds a direct test
(`aSecondRunNeverReEvaluatesASnapshotAlreadyEvaluatedByTheFirstRun`) proving this end-to-end rather
than leaving it as an inference from separately-tested pieces. No code change was needed to make
this true - it already was.

### Leakage tests (Phase 16H-7 additions)

- `HistoricalDelayProfileAggregatorTest`: observation before/exactly-at/after the cutoff, and an
  empty dataset with a cutoff still supplied - proving the new station-level cutoff overload has
  identical inclusive-boundary semantics to the section aggregator, and that a future observation
  can never leak into a station-level historical profile (its delay figure, chosen to be wildly
  different from the in-scope one, is proven absent from the resulting average).
- `HistoricalSectionDelayProfileAggregatorTest`: unchanged, already had complete before/exactly-at/
  after coverage since Phase 16F - re-run as regression, still passing.
- `PredictionOutcomeMatcherTest`: unchanged leakage coverage (before/at-exactly/after the prediction
  instant) plus the new evaluation-mode-preservation test above.

### Configuration - nothing new added

Reviewed `prediction.evaluation.enabled`/`refresh-interval-ms`/`refresh-initial-delay-ms` and
`historical.profile-refresh.*`: no lookback or retention setting exists, and this phase adds none -
nothing built here needs to bound evaluation lookback or prune old snapshots, and this codebase's
own convention (per this phase's own instructions) is not to add configuration for a theoretical
future need. `prediction.evaluation.enabled` remains `false` by default, unchanged.

### Database

One migration, `V5__add_evaluation_mode_to_prediction_snapshots.sql`: a single
`evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'LIVE_EVALUATION'` column added to
`prediction_snapshots`. No other schema change - `prediction_snapshots` itself is not redesigned,
and no new index was added (nothing added this phase queries by `evaluation_mode`; the accuracy
report's `byEvaluationMode` breakdown is computed in memory over the same
`findByEvaluationStatusIn`-loaded set every other breakdown already uses).

### Testing

New/changed this phase: `HistoricalDelayProfileAggregatorTest` (+5: before/at/after cutoff, empty
dataset with cutoff, 3-arg overload still defaults to "now"), `PredictionSnapshotTest` (+3:
legacy-constructor default, explicit mode via canonical constructor, null-mode rejected),
`PredictionOutcomeMatcherTest` (+1: mode preserved through evaluation),
`PredictionSnapshotRecorderTest` (+1: live recording always stamps `LIVE_EVALUATION`),
`PredictionAccuracyReportBuilderTest` (+2: `byEvaluationMode` always has both keys, only
`LIVE_EVALUATION` carries data today), `PredictionEvaluationRefresherTest` (+1: a second run never
re-touches an already-evaluated row), `PredictionSnapshotRepositoryTest` (+1: the legacy
constructor's default round-trips through the database as `"LIVE_EVALUATION"`). Full regression:
every pre-existing test re-run unchanged and passing.

### Remaining limitations after Phase 16H-7

- `PostgresHistoricalDelayProvider` still has no on-demand, past-`referenceInstant` re-aggregation
  path the way `PostgresSectionHistoricalDelayProvider` does - only the aggregator itself is now
  cutoff-capable at the station level; wiring that into the provider/interface layer is deferred
  (see above for exactly why and what it would require).
- Full historical replay (`HISTORICAL_BACKTEST`) remains genuinely unimplemented and unimplementable
  without a new live-state/route/weather archival capability - `PredictionEvaluationMode` exists so
  this is representable honestly once (if ever) that capability exists, not so it can be claimed
  now.
- `EVALUATED_APPROXIMATE`'s earliest-candidate heuristic (Phase 16H-5) is unchanged - still a
  genuine journey-identity limitation, not addressed by this phase.
- No new filter or repository query was added for `evaluationMode` - with only one mode ever
  producible, this would be dead code; add it once `HISTORICAL_BACKTEST` becomes real.

### Recommended next phase

Two independent directions, either legitimate: (a) actually enable evaluation in a real deployment
and use the now-richer accuracy report (Phase 16H-6/16H-7) as evidence for a future confidence/
historical-weighting recalibration phase (Phase 16H-5's own original recommendation, still valid);
or (b) if true backtesting is genuinely wanted, a dedicated phase to build a point-in-time
live-train/route/weather archive - a materially larger undertaking than this phase's own scope,
correctly deferred rather than attempted partially here.

---

Everything below this point is the **original, superseded** design proposal for what became
Phase 16B - preserved as written (not edited to match what was actually built) so the design
discussion that led to the station-level decision above stays visible. Where it differs from the
implementation notes above, the implementation notes are what's real.

## 1. Current `HistoricalDelay` model

```java
public record HistoricalDelay(
        String trainNumber,
        RouteSection section,
        DayOfWeek dayOfWeek,
        Month month,
        String timePeriod,
        double averageDelayMinutes,
        double medianDelayMinutes,
        double standardDeviationMinutes,
        int sampleCount,
        String source) {           // <- added by the provenance fix just completed
}
```

Consumed via one interface method:
```java
HistoricalDelay getHistoricalDelay(String trainNumber, RouteSection section);
```

This is already an **aggregated profile** shape (an average/median/stddev over some number of
samples) - there is no raw-observation type at all today. `MockHistoricalDelayProvider` returns
one fixed profile for every train/section, dated to "today" via the injected `Clock`.

## 2. Proposed improved model

Two shapes, matching the "raw facts" vs. "aggregated statistics" split (see #6):

```java
// A. Raw observation — one real journey leg, one row per (train, journey date, from, to)
public record HistoricalDelayObservation(
        String trainNumber,
        Station fromStation,
        Station toStation,
        LocalDate journeyDate,
        DayOfWeek dayOfWeek,          // derivable from journeyDate; stored for query performance
        String timeBucket,            // derived from scheduled time; stored for query performance
        Instant scheduledArrival,
        Instant scheduledDeparture,
        Instant actualArrival,
        Instant actualDeparture,
        int arrivalDelayMinutes,      // actualArrival - scheduledArrival (may be negative: early)
        int departureDelayMinutes,    // actualDeparture - scheduledDeparture
        int delayChangeMinutes,       // this leg's arrivalDelay - the previous leg's departureDelay
        String source) {
}

// B. Aggregated profile — what HistoricalDelayCalculator actually consumes (today's HistoricalDelay, extended)
public record HistoricalDelayProfile(
        String trainNumber,
        Station fromStation,
        Station toStation,
        DayOfWeek dayOfWeek,                  // nullable = "any day" bucket
        String timeBucket,                    // nullable = "any time" bucket
        int sampleCount,
        double averageDelayChangeMinutes,
        double medianDelayChangeMinutes,
        double standardDeviationMinutes,
        double probabilityOfAdditionalDelay,  // fraction of samples where delayChangeMinutes > 0
        double averageRecoveryMinutes,        // avg magnitude of delay recovered when it was
        String source) {
}
```

`HistoricalDelayProvider`'s interface **does not need to change** - it already takes
`(trainNumber, RouteSection)` and can keep returning a profile shape. `HistoricalDelayProfile`
would simply replace/extend today's `HistoricalDelay` once real data backs it.

## 3. Proposed PostgreSQL tables (not created yet)

```sql
-- Raw, append-only fact table
CREATE TABLE train_journey_observations (
    id                      BIGSERIAL PRIMARY KEY,
    train_number            VARCHAR(5)   NOT NULL,
    from_station_code       VARCHAR(10)  NOT NULL,
    to_station_code         VARCHAR(10)  NOT NULL,
    journey_date            DATE         NOT NULL,
    scheduled_arrival       TIMESTAMPTZ,
    scheduled_departure     TIMESTAMPTZ,
    actual_arrival          TIMESTAMPTZ,
    actual_departure        TIMESTAMPTZ,
    arrival_delay_minutes   INT,
    departure_delay_minutes INT,
    delay_change_minutes    INT,
    source                  VARCHAR(50)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (train_number, from_station_code, to_station_code, journey_date)
);
CREATE INDEX idx_observations_lookup
    ON train_journey_observations (train_number, from_station_code, to_station_code);

-- Aggregated, periodically (re)computed from the table above
CREATE TABLE historical_delay_profiles (
    id                              BIGSERIAL PRIMARY KEY,
    train_number                    VARCHAR(5)  NOT NULL,
    from_station_code               VARCHAR(10) NOT NULL,
    to_station_code                 VARCHAR(10) NOT NULL,
    day_of_week                     SMALLINT,      -- NULL = "any day" bucket
    time_bucket                     VARCHAR(20),   -- NULL = "any time" bucket
    sample_count                    INT NOT NULL,
    average_delay_change_minutes    DOUBLE PRECISION,
    median_delay_change_minutes     DOUBLE PRECISION,
    standard_deviation_minutes      DOUBLE PRECISION,
    probability_of_additional_delay DOUBLE PRECISION,
    average_recovery_minutes        DOUBLE PRECISION,
    source                          VARCHAR(50) NOT NULL,
    computed_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (train_number, from_station_code, to_station_code, day_of_week, time_bucket)
);
```

`day_of_week`/`time_bucket` nullable so a query can fall back from an exact (day, time) match to a
coarser bucket (any time that day, then any day at all) when the exact bucket has too few samples
- rather than reporting "no data" when a slightly coarser bucket would have plenty.

## 4. Raw observations → historical profiles

**The raw ingestion source already exists and is already being fetched — it's just being
discarded.** `RouteStop` (Phase 3's RailRadar DTO) already carries `scheduledArrival`,
`scheduledDeparture`, `actualArrival`, `actualDeparture`, `delayArrival`, `delayDeparture` for
**every station a train has already passed**, in the same `route[]` array `LiveTrainDataMapper`
already parses for every live-status call. Today the mapper only reads the current/next station's
`distance`/`lat`/`lng` and drops everything else. A background job (or the mapper itself, as a
side effect) could insert one `train_journey_observations` row per already-completed leg, every
time a live-status call happens - no new external integration needed to start accumulating real
data.

Turning raw rows into profiles is then a straightforward aggregation, run periodically (a
scheduled job, or a Postgres materialized view refreshed on a schedule):

```sql
SELECT train_number, from_station_code, to_station_code,
       EXTRACT(DOW FROM journey_date) AS day_of_week,
       -- time_bucket omitted from v1 grouping, see §7
       count(*)                                          AS sample_count,
       avg(delay_change_minutes)                         AS average_delay_change_minutes,
       percentile_cont(0.5) WITHIN GROUP (ORDER BY delay_change_minutes) AS median_delay_change_minutes,
       stddev(delay_change_minutes)                       AS standard_deviation_minutes,
       avg((delay_change_minutes > 0)::int)               AS probability_of_additional_delay,
       avg(GREATEST(-delay_change_minutes, 0))            AS average_recovery_minutes
FROM train_journey_observations
GROUP BY train_number, from_station_code, to_station_code, EXTRACT(DOW FROM journey_date);
```

The real `HistoricalDelayProvider` then queries `historical_delay_profiles` for the current
`(trainNumber, section, today's bucket)`, falling back to coarser buckets, and finally to
`sampleCount = 0` ("no data") if nothing matches at any level - preserving the exact same
"no data" convention `HistoricalDelay` already uses.

## 5. How this would eventually affect ETA prediction

**Nothing needs to change in `HistoricalDelayCalculator` or `PredictionEngine` for this to start
working.** The calculator already just reads `averageDelayMinutes`/`sampleCount`/`source` off
whatever `HistoricalDelayProvider` returns; today's mock happens to return the same flat numbers
regardless of section, but the interface already takes a `RouteSection` - a real, section-aware
implementation is a drop-in replacement. The ETA formula (`docs/prediction-model.md`) is untouched
either way.

Two fields in the *proposed* profile are genuinely new inputs, and neither is needed for Phase 16
- both are flagged as future opportunities, not now:
- **`probabilityOfAdditionalDelay`** could become a new `ConfidenceCalculator` factor (a section
  that's historically volatile should lower confidence even if the point-estimate looks fine).
- **`averageRecoveryMinutes`** could let `RecoveryModel` (Phase 10) use a section-specific recovery
  tendency instead of one global configured fraction.

Both would touch the `simulation`/`prediction` packages' actual math - explicitly out of scope
until you decide to pursue them separately from "add PostgreSQL."

## 6. Raw observations vs. derived statistics

| Raw observation (one row per real journey leg) | Derived statistic (one row per aggregated bucket) |
|---|---|
| train number, from station, to station | (same, as the grouping key) |
| journey date | — (rolled up into "how many samples") |
| day of week, time bucket | *(stored redundantly on the raw row for query performance, even though derivable from journey date/scheduled time - not itself a "new" raw fact)* |
| scheduled arrival/departure | — |
| actual arrival/departure | — |
| arrival delay, departure delay | — |
| delay change between stations | → feeds `averageDelayChangeMinutes`, `probabilityOfAdditionalDelay`, `averageRecoveryMinutes` |
| data source/provenance | data source/provenance *(profile also needs its own: "computed from N mock/real observations")* |
| — | sample count |
| — | average delay, median delay, standard deviation |
| — | probability of additional delay |
| — | recovery behaviour |

## 7. Essential for Phase 16 vs. deferred

**Essential (Phase 16 minimum viable real provider):**
- `train_number`, `from_station_code`, `to_station_code` — the lookup key (already exists via `RouteSection`)
- `sample_count`, `average_delay`, `median_delay`, `standard_deviation` — already exist on `HistoricalDelay` today; just need real data behind them
- `source`/provenance — already added in the fix just completed
- One raw table, minimally: `train_number`, `from_station_code`, `to_station_code`, `journey_date`, `actual_arrival`/`scheduled_arrival` (or the delay directly), `source`
- **Aggregate across all days/times initially (no day-of-week/time-bucket grouping in v1).** Bucketing finely from day one, on the small sample volume Phase 16 launches with, would produce tiny per-bucket sample counts that undermine the whole point of "trust the average more with more samples." Add day/time bucketing once volume justifies it.

**Deferred (design should allow adding later, not needed now):**
- Storing `scheduled_arrival`/`scheduled_departure` as their own columns (vs. just storing the computed delay) - cheap to keep, lets you redefine "delay" later without re-scraping, but not essential to a v1
- `delay_change_minutes` (the per-leg delta, needed for real `probabilityOfAdditionalDelay`/recovery stats) - the raw data for this is already available (see §4), but the *aggregate* fields that consume it are deferred
- `probabilityOfAdditionalDelay`, `averageRecoveryMinutes` on the profile - nice-to-have refinements once enough raw data accumulates
- Day-of-week/time-bucket grouping (see above)
- Cross-train, section-only aggregation ("how do *all* trains behave on this section", independent of train number) - a further-future enhancement

---

**Phase 16C, 16E, 16F, 16G, 16H-1, 16H-2, 16H-3, 16H-4 and 16H-5 status: implemented (see their
implementation notes above); Phase 16D was a design-only data-feasibility investigation (no code
changed).** Still deferred, per every phase's explicit scope so far:
probability-of-additional-delay, recovery-rate modelling, day-of-week profiles, time buckets,
seasonal modelling, cross-train aggregation, recency weighting, and machine learning.
Section-level delay-change statistics feed prediction (Phase 16H-2) as a *replacement* for the
station-level historical adjustment when usable, never an addition alongside it, and which
strategy/provenance actually won is now an explicit, machine-readable field on both
`PredictionResult` and the REST output (Phase 16H-3) - no longer left for a consumer to infer from
warning text. `SECTION` and `STATION_FALLBACK` score identically in `ConfidenceCalculator` (Phase
16H-4) - a deliberate policy, not an oversight, pending real accuracy-tracking evidence. A
prediction-accuracy-tracking foundation now exists (Phase 16H-5, disabled by default via
`prediction.evaluation.enabled`) so that evidence can eventually be collected - measuring the
model, not yet changing it.
`MockHistoricalDelayProvider`/`MockSectionHistoricalDelayProvider` remain the *default*-active
providers (both still default to `mock`, unchanged by Phase 16H-2/16H-3), so production behaviour is
identical until an operator explicitly opts either provider into `postgres`. The ETA *formula's
shape* (`now + baseTravelTime + predictedExtraDelay + historicalAdjustment`, and
`currentDelay + predictedExtraDelay + historicalAdjustment` for total delay) is unchanged since
Phase 11 - what Phase 16H-2 changed is only *which source* supplies `historicalAdjustmentMinutes`,
never the formula around it.

## Phase 22: making the real evaluation-data collection pipeline operational and trustworthy

Phase 21 corrected the evaluated metric's arithmetic. Phase 22 makes the pipeline that *feeds* that
metric with real evidence actually reliable - **not** a calibration phase: no weight was tuned, no
coefficient was fit, no accuracy claim was made. The goal is exactly:

```
REAL LIVE PREDICTION → SNAPSHOT STORED → LATER REAL RAILRADAR OBSERVATION
  → MATCHED TO NEXT-STATION OUTCOME → EVALUATED → REPORTED (still INSUFFICIENT_DATA today)
```

### The one real bug this phase found and fixed

**`PredictionOutcomeMatcher.evaluate()` was silently corrupting every Phase 21 field the instant a
snapshot was evaluated.** It reconstructed the evaluated snapshot via the *pre-Phase-21*
`PredictionSnapshot` constructor - which resets `nextStationHistoricalAdjustmentMinutes`/`_source`/
`_provenance` to `0`/`NONE`/`UNAVAILABLE` and re-derives `predictedExtraDelayMinutes` as
`predictedNextStationDelayMinutes - currentDelayMinutes` (which, once historical/disruption
contributed to that sum, silently misattributes their contribution to "simulation"). This was a
correctness regression introduced by Phase 21 (the constructor was updated, but this one call site
wasn't) - not a data-volume problem, and not something more real snapshots would ever have revealed
on their own, since it corrupted every single evaluated row identically. Fixed by using the full
canonical constructor and copying every Phase 21 field verbatim, exactly like every pre-existing
field the matcher already carried through unchanged. See `PredictionOutcomeMatcherTest
.phase21NextStationFieldsAreCarriedThroughUnchangedNeverResetOrMisderived` for the regression test.

### Everything else the audit confirmed was already correct

- **`predictionMadeAt`** (`PredictionSnapshotRecorder`) is `Instant.now(clock)`, captured at the
  moment the live prediction result is handed to the recorder - never a DB-assigned/insertion
  timestamp. Now covered by a dedicated test using a clock deliberately far from wall-clock "now".
- **Temporal leakage protection** (`PredictionOutcomeMatcher`) - `observation.observedAt().isAfter
  (snapshot.predictionMadeAt())`, strict, so an observation at the exact same instant is
  conservatively excluded too - already exhaustively tested (before/at/after/ambiguous cases) since
  Phase 16H-5/16H-7; unchanged by this phase.
- **Idempotency**: `HistoricalObservationRecorder` upserts on the natural key (trainNumber,
  journeyDate, stationCode, stationSequence) with a `DataIntegrityViolationException` fallback for
  concurrent duplicates (already tested); `PredictionOutcomeMatcher` never re-evaluates a snapshot
  once it leaves `PENDING` (already tested); `prediction_snapshots` deliberately has **no** unique
  constraint on (train, station, time) - each live request legitimately produces a new, distinct
  snapshot, and treating repeats as duplicates would be wrong, not a bug.
- **Provenance**: real RailRadar-derived observations are always tagged `DataProvenance.RAILRADAR`
  (`HistoricalObservationMapper`), never `MOCK`; unavailable data stays `DataProvenance.UNAVAILABLE`
  or a `null` field - confirmed by direct code trace, no change needed.
- **No hardcoded credentials**: `RailRadarProperties.apiKey()` remains environment-only
  (`${RAILRADAR_API_KEY:}`), and `RailRadarClient` still only ever logs `apiKeyConfigured=<bool>`
  (Phase "RailRadar 503 diagnosis"), never the key itself - unchanged.

### Scheduler ordering - already safe, left unchanged

Historical observation collection is **not** on its own schedule - it happens inline, synchronously,
as a side effect of every live RailRadar-backed prediction request (`RailRadarTrainDataProvider` →
`HistoricalObservationRecorder`, piggybacked on the request's own RailRadar call to avoid burning
extra quota - a deliberate Phase 16E decision, unchanged). Two *separate* things run on their own
independent schedules: `HistoricalDelayProfileRefreshScheduler` (aggregates raw observations into
historical *profiles* - prediction *inputs*, unrelated to evaluation) and
`PredictionEvaluationRefreshScheduler` (matches `PENDING` snapshots against observations - this
phase's own concern).

Could the evaluation refresh scheduler ever run "too early" and evaluate against stale/absent data?
**No, by construction, regardless of execution order or timing**: `PredictionOutcomeMatcher`'s
leakage guard is a real timestamp comparison (`observedAt().isAfter(predictionMadeAt())`), not a
trust in *when* the refresh happened to run. If the refresh runs before a genuine future observation
exists, the snapshot simply stays `PENDING` (checked again next tick) - it can never be evaluated
against a nonexistent or too-early observation. This makes the "1. snapshot, 2. later observation,
3. evaluation" ordering safe under *any* scheduling interleaving, so no scheduler changes were made.

### Data-quality visibility - now distinguishes "no snapshots" from "no observations either"

`DataQualityAssessor`/`DataQualityReport` gained `historicalObservationCount` (a real count of the
separate `historical_observations` table). Previously, "no snapshot database configured", "database
configured but genuinely empty", and "observations are being collected but no snapshots exist yet"
(e.g. `prediction.evaluation.enabled=false` while live requests still populate observations) all
collapsed into the same empty report. Now the explanatory note differs for each, and the real
observation count is always visible even when `totalSnapshots` is `0`.

### What Phase 22 deliberately did NOT do

No candidate historical weight was searched. No disruption coefficient was optimized. No regression
was fit. No confidence weight was changed. The evaluation target is still next-station arrival
delay. `overallCalibrationStatus` remains `INSUFFICIENT_DATA` - this phase makes the pipeline that
*feeds* real evidence trustworthy; it does not manufacture evidence or a premature calibration
result.

### Enabling real, operational data collection

No new configuration property was introduced - every property below already existed from earlier
phases; this is the first place they're documented together as one deliberate operational bundle:

```properties
SPRING_PROFILES_ACTIVE=postgres        # activates the DataSource/JPA/Flyway autoconfiguration
                                        # (see application-postgres.properties) and requires a
                                        # real, reachable PostgreSQL instance - the app now fails
                                        # loudly at startup if it can't connect, rather than
                                        # silently falling back to no persistence.
HISTORICAL_PROVIDER=postgres           # historical.provider - station-level historical reads
HISTORICAL_SECTION_PROVIDER=postgres   # historical.section-provider - section-level historical reads
PREDICTION_EVALUATION_ENABLED=true     # prediction.evaluation.enabled - records a snapshot on
                                        # every live prediction with a next station, and enables
                                        # the evaluation-refresh scheduler
WEATHER_PROVIDER=openmeteo             # weather.provider - real Open-Meteo weather instead of the
                                        # fixed mock reading (mutually exclusive with mock; see
                                        # docs/configuration.md)
RAILRADAR_API_KEY=<your-real-key>      # required for any real live prediction at all (see
                                        # docs/configuration.md's RailRadar section)
```

`railway-disruption.provider` is deliberately **not** listed above with a recommended value: no
reliable real-time public source of Indian Railways operational disruptions exists (Phase 18's own
finding, unchanged) - leave it at whatever is already explicitly configured (`mock` for controlled
testing, or the default `unavailable`). Never silently substitute `mock` for a genuinely unavailable
real source.

The existing developer default (no profile active, everything mocked, evaluation disabled) is
**unchanged** - none of the above is required to run or test the application locally.

### Current known limitations (Phase 22)

- Historical observation collection remains tied to live prediction request volume, not an
  independent poll - a train nobody requests a prediction for again after passing the target
  station will never get an observation recorded for it. Documented, not solved, in this phase (a
  standalone RailRadar poller would need its own quota/rate-limit design - out of this phase's
  scope).
- No PostgreSQL instance was available in this environment to integration-test the real persistence
  path end-to-end; behavior was verified as thoroughly as possible without it (unit/mocked-repository
  tests, `Optional`-empty-repository code paths, and the full non-DB-dependent test suite).
- Zero real evaluation snapshots exist in this environment - `CALIBRATION_STATUS` remains
  `INSUFFICIENT_DATA`, honestly, per this phase's own restriction against manufacturing evidence.

## Phase 22B: an independent, scheduled real-data collection path

Phase 22A's own limitation: historical observation collection was tied entirely to live prediction
request volume (piggybacked inside `RailRadarTrainDataProvider.getLiveTrainData`) - a train nobody
requests a prediction for again after passing a station never gets an observation recorded for it.
Phase 22B adds a second, **independent** collection path that runs on its own schedule, reusing the
exact same underlying pipeline rather than duplicating it.

### Architecture

```
HistoricalObservationCollectionScheduler   (@Scheduled, own interval, overlap-guarded)
        ↓ calls
HistoricalObservationCollectionService.collectAll()
        ↓ for each explicitly configured train number
TrainDataProvider.getLiveTrainData(trainNumber)   -- the SAME interface PredictionService uses
        ↓ (RailRadarTrainDataProvider's own existing side effect, unchanged)
HistoricalObservationMapper → HistoricalObservationRecorder → historical_observations
```

`HistoricalObservationCollectionService` has exactly one collaborator: `TrainDataProvider`. It does
not know about `RailRadarClient`, does not know about `PredictionEngine`/`PredictionService`, and
does not construct a `PredictionResult` or `PredictionSnapshot` - **collection cannot create a fake
prediction by construction**, not merely by convention. The fetched `LiveTrainData` return value is
deliberately discarded; only the historical-observation side effect (already proven correct by the
pre-existing `RailRadarTrainDataProviderTest`) matters here.

### Train selection - explicit, never discovered

`historical.collection.train-numbers` is a comma-separated, explicitly operator-configured list -
empty by default. There is still no reliable "list every currently-running train" RailRadar
endpoint this codebase has ever verified (Phase 18's own finding), so trains are never scraped,
discovered, or algorithmically generated. `HistoricalCollectionProperties`' compact constructor
validates every entry (`\d{5}`, mirroring the existing `PredictionController`/`EvaluationController`
pattern) and deduplicates (order-preserving) - a repeated entry must never double a train's real
polling frequency/API cost.

### Scheduling and rate-limit safety

`historical.collection.enabled=false` by default - a real, scheduled RailRadar request (consuming
real quota - RailRadar's free tier is 1,000 requests/month, see docs/configuration.md's RailRadar
section) is a genuine new side effect requiring explicit opt-in, mirroring
`prediction.evaluation.enabled`'s own convention. Even when enabled, an empty `train-numbers` list
makes the scheduler a safe no-op. The default interval (`3600000` ms = 1 hour) deliberately mirrors
`historical.profile-refresh.interval-ms`'s own conservative default - polling more aggressively is
an explicit operator choice, never the out-of-the-box behavior.

**Overlap protection** is a plain `AtomicBoolean` guard, process-local only - it does not coordinate
across multiple application instances (no distributed lock was introduced; premature for a
single-instance deployment with no real observation volume yet). If a previous run is still
executing (e.g. many trains configured, RailRadar responding slowly), the next tick is skipped
rather than starting a second, overlapping run - verified by a concurrency test using two threads
and a `CountDownLatch`.

**Failure isolation** operates at two levels: one train's failure never stops the remaining
configured trains within a single run (`HistoricalObservationCollectionService.collectOne` catches
`RuntimeException` per train), and one run's failure never prevents the next scheduled tick
(`HistoricalObservationCollectionScheduler.collect` catches around the whole run, and releases the
overlap guard in a `finally` block regardless of success/failure).

### What this phase deliberately did not do

No prediction snapshot is ever created by collection. No calibration was implemented. No train
discovery/crawling was added. No distributed coordination (Kafka/Redis/a distributed lock) was
introduced - explicitly premature at this stage, per this phase's own scope boundary.

### Configuration

```properties
historical.collection.enabled=${HISTORICAL_COLLECTION_ENABLED:false}
historical.collection.interval-ms=${HISTORICAL_COLLECTION_INTERVAL_MS:3600000}
historical.collection.initial-delay-ms=${HISTORICAL_COLLECTION_INITIAL_DELAY_MS:300000}
historical.collection.train-numbers=${HISTORICAL_COLLECTION_TRAIN_NUMBERS:}
```

Example: `HISTORICAL_COLLECTION_ENABLED=true HISTORICAL_COLLECTION_TRAIN_NUMBERS=12952,12002` (plus
the Phase 22 `postgres`/`prediction.evaluation.enabled` bundle above, for the observations to
actually persist anywhere).

### Verification performed this phase

- Full unit coverage for the new properties/service/scheduler (parsing/validation/dedup, per-train
  failure isolation, empty-config safety, disabled-by-default safety, overlap protection under real
  concurrency, scheduler-failure isolation) - all passing.
- The full chain from `TrainDataProvider` down to `HistoricalObservationRecorder` is **not**
  re-tested here - it was already, and remains, covered by the pre-existing
  `RailRadarTrainDataProviderTest`; Phase 22B's own tests only needed to prove the new scheduler/
  service correctly *drive* that existing, already-verified interface for each configured train.
- Clean application startup verified with default configuration (collector disabled) - no
  unexpected RailRadar activity, no interference with existing endpoints.
- **Not performed**: an actual scheduled collection run against the real RailRadar API (no train
  numbers were configured against a real key during this phase - enabling that was intentionally
  left to the operator, per this phase's "do not enable aggressive scheduled collection merely for
  testing" instruction) and PostgreSQL persistence of a real collected observation (Docker/Postgres
  still unavailable in this environment, unchanged from Phase 22/22A).
