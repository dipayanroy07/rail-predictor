# Prediction Model

This document defines the formula `PredictionEngine` (Phase 11) uses to turn live train data, a
simulation result, and historical delay stats into a `PredictionResult` - and, more importantly,
*why* it's shaped this way instead of the naive version.

## The baseline: what "base travel time" already represents

**Base travel time** = the time needed to cover the train's *remaining* distance, at its
*current* reported speed (or an assumed average speed if that's unknown/zero), assuming nothing
further changes.

This answers one specific question: **"how long from right now until arrival, if things continue
exactly as they are?"** It is computed from the train's *current* position and *current* speed -
it is not a projection from the train's original scheduled departure.

## The mistake the naive formula makes

A tempting first draft is:

```
ETA = now + (distance / speed) + current_delay + predicted_delay + historical_delay
```

This double-counts. `distance / speed` already starts from *now*, using the train's *current*
speed - and "now" already reflects however delayed the train currently is (that's baked into
where the train physically is at this exact moment). `current_delay` is a fact about the *past*
(time already lost since departure); it has no place in a calculation of time remaining *from the
current moment forward*. Adding it again on top of `distance/speed` effectively counts the
already-elapsed delay twice.

## The actual formula

Two different outputs answer two different questions, and only one of them includes
`current_delay`:

```
predictedEta (an absolute clock time)
    = now
    + baseTravelTimeMinutes
    + predictedExtraDelayMinutes
    + historicalAdjustmentMinutes
    # current_delay is NOT added - see above.

predictedTotalDelayMinutes (a duration, relative to the ORIGINAL schedule)
    = max(0, currentDelayMinutes + predictedExtraDelayMinutes + historicalAdjustmentMinutes)
    # this DOES include current_delay - it's answering "how late will this train
    # ultimately be versus its published schedule", which spans the already-elapsed
    # portion of the journey (current_delay) and the still-to-come portion.
```

Where:

- **`predictedExtraDelayMinutes`** = `SimulationResult.netDelayMinutes()` - already
  `(direct disruption delay + cascade delay − recovered delay)`, clamped at 0 by
  `SimulationEngine` (Phases 7-10). `DelayCalculator` just extracts this value; it does not
  recombine anything.
- **`recoveryMinutes`** in the final result is the *same* recovered-delay figure, shown for
  transparency/breakdown. **It must not be subtracted again** - it is already netted into
  `predictedExtraDelayMinutes` above.
- **`historicalAdjustmentMinutes`** - exactly ONE historical contribution, never two. Since Phase
  16H-2, this is decided by `PredictionEngine` as a replacement policy, not an additive one - see
  "Phase 16H-2: section history replaces station-level history" below for the full rule. Before
  Phase 16H-2 (and still true whenever no usable section history exists): `round(historicalDelay.
  averageDelayMinutes() × weight)` if `historicalDelay.sampleCount() >= minimumSampleCount`, else
  `0`. `weight` and `minimumSampleCount` are configurable (`prediction.historical-adjustment.*`),
  not a hardcoded `× 20%` - see `HistoricalDelayCalculator`.

## Edge cases (`TravelTimeCalculator`)

| Input | Behaviour |
|---|---|
| Remaining distance unknown (null) | Base travel time = 0 (nothing to compute from) |
| Remaining distance = 0 | Base travel time = 0 (correct: the train has arrived) |
| Speed unknown (null) or 0 | Falls back to a configured assumed average speed (`prediction.travel-time.assumed-average-speed-kmh-when-unknown`, default 60 km/h) |
| Negative distance or speed | Cannot occur - `LiveTrainData`'s own compact constructor (Phase 2) already rejects negative values when the object is constructed |

Every fallback is recorded in `PredictionResult.warnings()`, so a degraded input is visible in the
output, never silently smoothed over.

## Worked example

A train is currently **12 minutes late** (`currentDelayMinutes = 12`), with **90 km** remaining at
a current speed of **60 km/h**.

- `baseTravelTimeMinutes = 90 / 60 × 60 = 90 minutes`
- The simulation found a direct disruption delay of 15 min and a cascade delay of 3 min, of which
  10 min was recovered: `netDelayMinutes = 15 + 3 − 10 = 8` → `predictedExtraDelayMinutes = 8`,
  `recoveryMinutes = 10` (shown, not re-subtracted)
- Historical data for this train/section: average delay 15 min over 40 samples (≥ the minimum of
  5), weight 0.2: `historicalAdjustmentMinutes = round(15 × 0.2) = 3`

```
predictedTotalDelayMinutes = max(0, 12 + 8 + 3) = 23 minutes late (vs. the original schedule)
predictedEta               = now + 90 + 8 + 3   = now + 101 minutes
```

If "now" is `2026-09-09T10:00:00Z`, `predictedEta` = `2026-09-09T11:41:00Z`.

## Phase 16H-2: section history replaces station-level history

`PredictionEngine.predict(...)` now takes an additional argument, a
`RemainingRouteHistoricalSummary` (built by `PredictionService` from
`RouteProvider.remainingRoute(train)` and `RemainingRouteHistoricalAggregator.summarize(...)`,
Phase 16H-1) - `PredictionEngine` itself never discovers route topology or queries a historical
provider; it only decides how to combine numbers it's handed. A six-argument overload of `predict`
is preserved exactly as it always behaved (it supplies a synthetic "nothing to work with" summary),
so no pre-16H-2 caller needed to change.

**The replacement rule** - there is still exactly one historical contribution, never a sum of two:

```
sectionAdjustment = SectionHistoricalDelayCalculator.sectionAdjustmentMinutes(summary)
                   = round(summary.totalDelayChangeMinutes() × weight), or null if
                     summary.totalDelayChangeMinutes() is null (no usable section history)

historicalAdjustmentMinutes =
    sectionAdjustment                                     if sectionAdjustment != null
    HistoricalDelayCalculator.historicalAdjustmentMinutes(historicalDelay)   otherwise (fallback)
```

The same configured `prediction.historical-adjustment.weight` applies to whichever figure is used
- there remains exactly one weight to tune, not two independent ones.
`prediction.historical-adjustment.minimum-sample-count` is **not** consulted for the section path:
the section-level minimum-sample gate (`historical.section.minimum-sample-count`, Phase 16G) was
already enforced per section, upstream, before a section could ever count as `AVAILABLE` and
therefore contribute to `totalDelayChangeMinutes` at all.

**"Usable" section history includes `PARTIAL_SECTIONS_AVAILABLE`, not only
`ALL_SECTIONS_AVAILABLE`.** A partial signal - built only from the remaining sections that actually
had usable history - is still more specific to this train's actual remaining route than the
station-level average (a different statistic: one downstream station's arrival delay, not this
route's delay change), so it is preferred over falling back. When partial, a warning makes the
limited coverage explicit (see below) rather than presenting it as if it covered the whole
remaining journey.

**Fallback to station-level history** happens only when `summary.totalDelayChangeMinutes()` is
`null` - i.e. `RemainingRouteHistoricalStatus.NO_REMAINING_SECTIONS` (already arrived, or the route
itself couldn't be resolved) or `NO_SECTIONS_AVAILABLE` (sections exist, none were `AVAILABLE`).
This is unchanged, pre-16H-2 behaviour - `HistoricalDelayCalculator`, `HistoricalDelayProvider`, and
the station-level `HistoricalDelay` fetch are all untouched.

**New warnings**, added by `PredictionEngine` alongside the existing ones:

| Condition | Warning |
|---|---|
| Section history used, `PARTIAL_SECTIONS_AVAILABLE` | "Historical adjustment is based on partial remaining-route section history (N of M remaining sections) - coverage does not extend to the full remaining route." |
| Section history used, provenance not `railradar` | "Section historical delay data is simulated or mixed (...), not derived entirely from real historical records." |
| Fell back, and the reason was `NO_SECTIONS_AVAILABLE` | "No usable section-level historical data for the remaining route - falling back to station-level historical data." |
| Fell back (any reason), and station-level `historicalDelay.sampleCount() == 0` | "No historical delay data available for this train/section - historical adjustment is 0." (unchanged wording, now gated so it never fires when section history was actually used) |

`NO_REMAINING_SECTIONS` (already arrived, or route unavailable) produces **no** fallback warning of
its own - it isn't a data-quality problem, so calling it out would be noise.

**Updated by Phase 16H-3** (was a known gap in 16H-2): `ConfidenceCalculator`'s
`historicalDataAvailability` factor now reflects the already-resolved
`HistoricalAdjustmentResolution` (see below), not just the station-level `HistoricalDelay` - so it
correctly recognises real section-sourced data even when the station-level lookup happens to be
empty. The weights, thresholds, and overall scoring formula are unchanged - see "Phase 16H-3" below.

## Phase 16H-3: making the historical source/provenance explicit

Phase 16H-2 left a known gap: `historicalAdjustmentMinutes` could come from either section or
station history, but nothing machine-readable said which - only prose warnings hinted at it. Phase
16H-3 closes this with two small, deliberately separate concepts:

- **`HistoricalAdjustmentSource`** (`SECTION` / `STATION_FALLBACK` / `NONE`) - *which calculation
  strategy* actually produced the adjustment.
- **`DataProvenance`** (`railradar` / `mock-provider` / `unavailable` / `mixed(...)`) - *where that
  winning strategy's data came from*, unchanged and reused as-is - never a new vocabulary.

These are packaged together as one small value object, `HistoricalAdjustmentResolution(source,
provenance)`, carried on `PredictionResult.historicalAdjustmentResolution()`. The two axes vary
independently: a `SECTION`-sourced adjustment can be `mock-provider`-provenanced exactly as easily
as a `STATION_FALLBACK` one (e.g. if `historical.section-provider=mock` while station-level
history happens to be real, or vice versa) - never conflated.

**Exactly how each value is selected** (`PredictionEngine.resolveHistoricalAdjustment`, no change
to the replacement policy's shape from Phase 16H-2):

```
sectionAdjustment = SectionHistoricalDelayCalculator.sectionAdjustmentMinutes(summary)   // null if unusable
if sectionAdjustment != null:
    source = SECTION;            provenance = summary.provenance()
else:
    stationAdjustment = HistoricalDelayCalculator.historicalAdjustmentMinutes(historicalDelay)  // null if unusable (Phase 16H-3: was 0)
    if stationAdjustment != null:
        source = STATION_FALLBACK;  provenance = historicalDelay.source()
    else:
        source = NONE;              provenance = DataProvenance.UNAVAILABLE
        historicalAdjustmentMinutes = 0   # the only place a hard 0 is fabricated - because there is genuinely nothing to use
```

`HistoricalDelayCalculator.historicalAdjustmentMinutes(...)` changed from returning `int` to
`Integer` (`null` below the minimum sample count) specifically so this distinction is possible -
before this phase, "insufficient samples" and "the real average happens to be exactly 0" were both
just `0`, making it impossible to tell `STATION_FALLBACK` (with a genuine zero) apart from `NONE`.
`SECTION` includes both `ALL_SECTIONS_AVAILABLE` and `PARTIAL_SECTIONS_AVAILABLE` - see Phase
16H-2's own reasoning above for why partial coverage is still accepted, not treated as unusable.

**Nothing about the arithmetic changed**: `historicalAdjustmentMinutes` still enters
`predictedTotalDelayMinutes` and `predictedEta` in exactly the same two places, exactly once,
computed by the exact same `round(weight × total)` shape regardless of which branch supplied it -
`resolveHistoricalAdjustment` only decides *which* number and *how to label it*, never how it's
used afterward.

**Confidence** (`ConfidenceCalculator`): the `historicalDataAvailability` factor's condition
changed from `historicalDelay.sampleCount() > 0` to `historicalResolution.source() != NONE` - a
factual-accuracy correction, not new confidence mathematics. The weights, thresholds, and the
overall `achieved / possible` scoring formula are byte-for-byte unchanged; only which existing
signal decides whether this one factor is satisfied. The mock-provenance warning now checks
`historicalResolution.provenance()` instead of `historicalDelay.source()`, for the same reason -
`historicalDelay` is no longer a parameter of `ConfidenceCalculator.calculate(...)` at all (it was
otherwise unused inside that method).

**Output contract**: `PredictionBreakdownResponse` gained two fields,
`historicalAdjustmentSource` and `historicalAdjustmentProvenance`, sitting directly alongside
`historicalAdjustmentMinutes` - see docs/json-output.md and docs/api-contract.md. No existing field
was removed or renamed. `PredictionResult`'s constructor remains backward-compatible: a legacy
overload (the pre-16H-3 argument list) defaults `historicalAdjustmentResolution` to
`NONE`/`unavailable` for any caller not concerned with it - `PredictionOutput` itself is only ever
built by `PredictionOutputMapper`, so no equivalent overload was needed there.

## Phase 16H-4: historical confidence policy - SECTION and STATION_FALLBACK score identically

Phase 16H-3 made `HistoricalAdjustmentSource`/provenance explicit but deliberately left the
confidence *formula* untouched. Phase 16H-4 answers the resulting open question directly: **should
`SECTION` and `STATION_FALLBACK` receive different numerical confidence? No - not currently, and
this was a considered decision, not an oversight.**

**Why no numeric distinction was introduced.** A real, already-established asymmetry between the
two does exist: section-level delay-change samples need a higher minimum sample count than
station-level ones to even be considered usable at all
(`historical.section.minimum-sample-count`, default `10`, vs.
`prediction.historical-adjustment.minimum-sample-count`, default `5` - see
docs/historical-data-design.md's Phase 16G notes on the variance-of-a-difference reasoning behind
that gap). But that asymmetry is **already fully accounted for** by the gate itself: by the time
either source reaches `ConfidenceCalculator`, it has already cleared its own (different) bar for
"usable." Discounting `SECTION` again in confidence would double-count a threshold that was already
applied upstream, not add new information. No calibrated evidence exists in this codebase (no
backtesting or real-accuracy-tracking infrastructure) from which a defensible *numeric* multiplier
(e.g. "SECTION counts for 0.9× as much as STATION_FALLBACK") could be derived - inventing one would
be fake statistical precision, which this phase explicitly avoids. Both therefore satisfy the
`historicalDataAvailability` factor identically whenever `source != NONE`.

**Partial section coverage (`PARTIAL_SECTIONS_AVAILABLE`) is deferred, not scored.** The existing
partial-coverage warning (Phase 16H-2) already discloses this in prose, and
`RemainingRouteHistoricalSummary`'s own `availableSectionCount`/`sectionResults` remain fully
visible on the result for a future phase to calibrate against - nothing is lost by not turning
coverage into a number today. A coverage-fraction confidence factor was considered and rejected:
there is no evidence in this codebase relating "N of M sections available" to actual prediction
accuracy, so any such factor would be an invented statistical model, not a derived one.

**Provenance - one genuine, small correction.** The mock-provenance warning check changed from an
exact `DataProvenance.MOCK.equals(provenance)` comparison to `provenance.contains(DataProvenance.MOCK)`,
so a `mixed(...)` composite (e.g. `"mixed(mock-provider,railradar)"`, produced when a section
aggregate blends real and mock-sourced samples - Phase 16F) is correctly recognised as partially
simulated instead of silently passing as fully real. This is a factual-accuracy fix to an existing
warning, not a score change: mock, mixed, and real data all still satisfy
`historicalDataAvailability` identically (matching how `WeatherData`'s own mock/real handling has
always worked) - only the warning text differs.

**Sample counts remain pure operational gates, never a confidence input.**
`historical.section.minimum-sample-count` and `prediction.historical-adjustment.minimum-sample-count`
decide whether an adjustment is usable at all (null vs. a real number) - they are not, and were not
made, an additional numeric confidence signal. Introducing "more samples = higher confidence"
scoring now would be a new statistical model invented without supporting evidence, exactly what
this phase's brief warned against.

**What would justify revisiting this**: real accuracy-tracking/backtesting data showing that
predictions using `SECTION` history are measurably more (or less) accurate than those using
`STATION_FALLBACK`, or showing a measurable accuracy relationship to section coverage fraction -
until that data exists, treating them identically is the more honest position than guessing.

**No configuration was added** - since no numeric distinction was introduced, there was nothing to
make configurable (a new confidence weight would only be meaningful once a real value backs it).

## Confidence

`confidence` in `PredictionResult` is produced by `ConfidenceCalculator` (Phase 12) - see its own
Javadoc and `docs/architecture.md` for how the score is built from data-availability signals. Its
own `warnings()` are merged into `PredictionResult.warnings()` so everything limiting trust in a
prediction is visible in one place.

## Phase 16H-5: measuring the model (not yet optimizing it)

This formula has never been validated against real outcomes - every weight, threshold, and
assumption documented above is exactly that, an assumption. Phase 16H-5 builds the foundation for
closing that gap, without changing a single number in this formula.

**What is measured**: not the full destination-level prediction (RailRadar exposes no reliable
whole-journey-completion signal - see docs/historical-data-design.md's Phase 16H-1 notes), but the
train's **next station** - the largest target this system can currently evaluate honestly, since
that's exactly the completion signal `HistoricalObservationRecorder` already trusts. The evaluated
predicted value is `currentDelayMinutes + predictedExtraDelayMinutes` - **not**
`predictedTotalDelayMinutes()`, which is destination-scoped and includes a historical adjustment
whose spatial scope may extend beyond the next station.

**What "accurate" means here**: `error = predictedNextStationDelayMinutes - actualDelayMinutes`
(the real `arrivalDelayMinutes` RailRadar reports once the train reaches that station), aggregated
as MAE/RMSE/bias (see docs/historical-data-design.md's Phase 16H-5 notes for the exact formulas)
against a simple "current-delay-only" baseline.

**Leakage is prevented structurally**: a `PredictionSnapshot` is only ever matched against an
observation recorded strictly *after* the prediction was made
(`observation.observedAt().isAfter(snapshot.predictionMadeAt())`) - never one that already existed.

See docs/historical-data-design.md's own "Phase 16H-5" section for the full design: the snapshot
schema, matching rules, journey-identity uncertainty handling
(`EXACT`/`APPROXIMATE`/`NOT_EVALUABLE`), and why this is disabled by default
(`prediction.evaluation.enabled=false`). Nothing in this phase feeds back into the formula above -
that is explicitly a future phase's job, once real measured data exists to justify it.

## Phase 16H-6: making the accuracy data observable (still not optimizing the model)

Phase 16H-5 built the measurement foundation but had no way to *see* the numbers except by writing
a one-off test. Phase 16H-6 adds a read-only reporting endpoint over exactly that data - see
docs/api-contract.md's "Prediction Accuracy Report" section for the full request/response contract
(`GET /api/v1/evaluation/accuracy`). As before, nothing here changes the ETA formula, the
simulation, or any weight/threshold - this phase only observes.

**Exact vs. approximate, never silently blended.** `PredictionAccuracyReportBuilder.buildReport`
exposes three distinct views rather than picking one: `overall` (`EVALUATED_EXACT` +
`EVALUATED_APPROXIMATE` combined, with `exactCount`/`approximateCount` still visible), `exactOnly`
(the same current-model/baseline comparison recomputed using *only* unambiguous exact matches), and
each `bySource`/`byProvenance` slice (which also carries its own exact/approximate split). A caller
who only trusts unambiguous journey matches can compare `overall` against `exactOnly` directly
rather than being handed one blended number with the uncertainty hidden.

**Breakdown by source and by provenance, never merged with a value judgment.** `bySource` always
reports all three `HistoricalAdjustmentSource` keys (`SECTION`, `STATION_FALLBACK`, `NONE`), even at
zero samples - a missing key would be indistinguishable from "not computed". `byProvenance` reports
one slice per provenance string actually observed (`"railradar"`, `"mock-provider"`, or a
`"mixed(...)"` composite), so mock-derived evaluation data can never be mistaken for a real-data
result. Neither breakdown ever concludes one bucket is "better" - that requires a sample-size or
statistical-significance judgment this phase deliberately does not invent, especially since sample
sizes across sources/provenances will typically be very different.

**`improvementPercent`**: `(baselineMae - currentModelMae) / baselineMae * 100`, computed
identically for `overall` and every breakdown slice via the same
`PredictionAccuracyCalculator`/`PredictionAccuracyComparison` Phase 16H-5 already built - this phase
adds no new statistics math, only new ways of slicing the same numbers. It is `null` (never `0.0`
or a fabricated figure) whenever the baseline's own MAE is exactly `0.0`, since "N% better than a
perfect baseline" isn't a meaningful statement.

**Filtering is deliberately small and entirely in-memory.** `PredictionAccuracyReportFilter` covers
exactly six dimensions (train number, station code, an inclusive prediction-time range, historical-
adjustment source, and evaluation status) - the ones actually asked for, not every conceivable
filter. `PredictionAccuracyReportService` loads snapshots via the same `findByEvaluationStatusIn`
query the Phase 16H-5 foundation already had (no new repository method, no new Flyway migration/
index) and applies every other filter in memory. This is a deliberate, documented choice for the
dataset size this phase expects: evaluation is disabled by default, and even once enabled, at most
one snapshot is recorded per live prediction with a next station - nowhere near the volume that
would justify pushing every filter combination into SQL. If the evaluated-snapshot table grows
large enough that loading `findByEvaluationStatusIn`'s full result set becomes a real cost, the next
step is a JPQL query on `PredictionSnapshotRepository` that pushes the train/station/time-range
filters into the database - not a rewrite of this reporting layer.

**Evaluation-disabled is a first-class, explicit state, not a fabricated empty dataset.**
`PredictionAccuracyReportService.getReport` returns `Optional.empty()` when
`prediction.evaluation.enabled=false`; the controller maps that to
`{"evaluationEnabled": false, ...all other fields null}` - HTTP `200`, not an error, and clearly
distinct from "enabled, but nothing evaluated yet" (`evaluationEnabled: true`, every slice present
with `sampleCount: 0`). Calling this endpoint never enables evaluation as a side effect.

## Phase 16H-7: evaluation data collection integrity and backtest readiness

This phase still changes no prediction mathematics - it hardens the Phase 16H-5/16H-6 evaluation
*infrastructure* itself, and answers a question those phases left open: is this system actually
ready for retrospective backtesting?

**Live evaluation is not a backtest.** Every prediction this application has ever evaluated is a
*live* evaluation: made now, by the real request path, using whatever data genuinely existed at
that moment, checked later against a real outcome. A true *historical backtest* - reconstructing
what the model would have said at an earlier timestamp T, using only information available at T -
is a fundamentally different (and strictly harder) guarantee: it needs not just cutoff-aware
historical statistics, but a T-cutoff reconstruction of the train's live state, route, and weather
too. `model.domain.PredictionEvaluationMode` (`LIVE_EVALUATION`/`HISTORICAL_BACKTEST`) makes this
distinction explicit on every snapshot, rather than letting "we compared a prediction to a later
outcome" get casually called a backtest.

**Station-level history is now point-in-time capable at the aggregation layer.**
`HistoricalDelayProfileAggregator` gained the same cutoff-aware overload
`HistoricalSectionDelayProfileAggregator` already had since Phase 16F -
`aggregate(trainNumber, stationCode, observations, referenceInstant)`, with the identical
inclusive-cutoff rule (`!observedAt().isAfter(referenceInstant)`). This closes the specific gap
Phase 16H-5 documented ("`STATION_FALLBACK`'s own historical inputs cannot yet be reconstructed
point-in-time"). It does **not** extend to the provider layer - `PostgresHistoricalDelayProvider`
still always reads the always-current materialized cache, unlike its section counterpart, which
already re-aggregates on demand for a past cutoff (Phase 16G). Extending that is deliberately
deferred, since it would touch `HistoricalDelayProvider`'s interface and its live caller,
`PredictionService` - outside this phase's "no model/live-path changes" boundary.

**Backtest readiness: NOT_READY, explicitly.** Even with both historical aggregators now
cutoff-capable, a genuine backtest additionally requires reconstructing a train's live
position/delay/route/weather as of an arbitrary past instant - none of which this application
archives (only the *current* live state is ever fetched). This phase concludes, rather than
fabricating a partial or invalid backtest capability: **live evaluation is production-capable;
full historical replay remains unavailable** until a future phase builds that archival capability.
See docs/historical-data-design.md's own Phase 16H-7 section for the full input-by-input
readiness table and reasoning.

## Phase 17: a real weather provider (Open-Meteo) - the model itself is unchanged

This phase replaces `MockWeatherProvider` as the *default-capable* weather source with a real
integration, without touching a single weight, threshold, or formula anywhere in this document.
`HeavyRainModel`/`DenseFogModel` are byte-for-byte unchanged: they still only ever read
`WeatherData.condition()`, exactly as they did with mock data.

**Selected provider: Open-Meteo** (https://open-meteo.com). Chosen over the more commonly-known
OpenWeatherMap after checking current capabilities: Open-Meteo needs **no API key at all** for
non-commercial use (a generous ~10,000 requests/day), returns `precipitation` (mm),
`visibility` (m), and `temperature_2m` (°C) in exactly this application's existing domain units (no
conversion needed - see below), reports a genuine per-reading observation timestamp
(`current.time`), and its response is a single plain HTTP GET returning JSON - trivially compatible
with the same `WebClient`-based pattern `RailRadarClient` already established. `weather.provider`
selects between it and mock exactly like `historical.provider` selects between mock and Postgres -
deterministic, `@ConditionalOnProperty`-gated, no silent fallback (see docs/configuration.md).

**Classifying a real reading into `WeatherCondition` is new** - this did not exist anywhere before
Phase 17; `MockWeatherProvider` only ever echoed a pre-configured enum value, never derived one.
`OpenMeteoWeatherMapper` classifies primarily from Open-Meteo's own WMO weather code (an
international standard, not an invented threshold): codes 51-67/80-81 → `RAIN`, 65/82/95/96/99 →
`HEAVY_RAIN` (treated as certain, mirroring how `HeavyRainModel` already treats a `HEAVY_RAIN`
reading), 0/1 → `CLEAR`, 45/48 → `FOG` or `DENSE_FOG`. The WMO fog code carries no light/dense
distinction, so the one genuinely new number this phase introduces,
`weather.openmeteo.dense-fog-visibility-meters` (default 200m), uses `visibility` to decide that
split - 200m mirrors the India Meteorological Department's own official "dense fog" definition
(visibility 51-200m), a documented, non-arbitrary convention. Snow codes and partly-cloudy/overcast
codes fall through to `UNKNOWN`, a known, explicit limitation (this enum has no `SNOW`/`CLOUDY`
value, and adding one is out of this phase's scope).

**Units require no conversion.** Open-Meteo's default units (`°C`, `mm`, meters) already match
`WeatherData`'s own units exactly - verified directly (see `OpenMeteoWeatherMapperTest`), not
assumed.

**Location**: already solved before this phase. `Station.latitude()`/`longitude()` have carried
real coordinates (from RailRadar's own route data, via `LiveTrainDataMapper`) since Phase 13
specifically so `PredictionService.fetchWeather(train.currentStation())` could call a real weather
API - no new location-resolution capability was needed.

**Freshness**: `WeatherData` gained `observedAt` (Phase 17) - Open-Meteo's own `current.time`,
parsed as UTC (the mapper always requests `timezone=UTC`), representing when the reading is valid
*for*, not when the HTTP response was received. `MockWeatherProvider`'s reading has no real
observation moment at all, so it is `null` there - never a fabricated "now". No staleness/lookback
threshold was introduced: nothing in this codebase currently needs one (Open-Meteo's "current"
endpoint is inherently near-real-time, and this is live evaluation, not the point-in-time replay
Phase 16H-7 separately addressed for historical profiles) - inventing one without a concrete need
would be exactly the kind of unjustified threshold this phase's own instructions warn against.

**Provenance and audit**: a real reading is tagged `DataProvenance.OPENMETEO` (`"open-meteo"`),
mock stays `DataProvenance.MOCK`, and a failed/unavailable fetch is simply `null` weather (no
`WeatherData` at all) - the existing three-state provenance vocabulary, no second system invented.
`PredictionResult` gained `weatherProvenance` (Phase 17, `WeatherData.source()` at prediction time,
or `null`) purely for audit; `PredictionSnapshot` carries the same field through to persistence, so
a later evaluation could determine whether a given past prediction used mock, real, or no weather
data. Deliberately **not** exposed in the REST output - `confidence.contributingFactors` already
surfaces "Weather data is available (`<source>`)" today (unchanged), and no accuracy-report
breakdown by weather provenance exists yet (explicitly out of scope this phase - see
docs/architecture.md).

**Integration boundary, unchanged in shape**: `PredictionService` still fetches weather via
`WeatherProvider` and hands the plain `WeatherData` to `SimulationEngine`/`PredictionEngine`/
`ConfidenceCalculator` - none of them know or care which vendor produced it.
`PredictionService.fetchWeather`'s existing try/catch (weather failure → `null`, never propagated,
never fabricated) needed no changes at all to correctly handle real-provider failures - it already
handled every "optional data source failed" case identically.

## Phase 18: real railway operational disruption data - provider foundation, not yet consumed

This phase is evidence-first, per its own explicit instructions: it does not add a delay formula,
does not touch `PredictionEngine`, and does not claim real operational data exists where research
found none. See docs/architecture.md's Phase 18 notes for the full source-by-source research
findings; the short version: no reliable, publicly accessible, machine-readable, appropriately
licensed source of real-time Indian Railways TSR/engineering-block/signal-failure/congestion data
was found (India Railways' own TSR data exists digitally, but flows through CRIS's partner-only API
gateway, not a public self-service one). `UnavailableRailwayDisruptionProvider` is the honest,
explicit result - not a placeholder pretending a real integration is imminent.

**Observed disruption vs. simulated delay - a distinction this phase deliberately keeps separate.**
`RailwayDisruption` represents a reported operational *fact* ("a TSR is in effect over this
section"); `DisruptionResult` (produced by `HeavyRainModel`/`CongestionModel`/etc.) represents a
simulation's *hypothesis* about resulting delay. This phase's `RailwayDisruptionProvider` answers
only "what is reported to exist" - it has no `delayMinutes` field and no code path that computes
one. Deciding "a temporary speed restriction of X km/h over Y km costs Z minutes" is a **separate,
future, deliberately-deferred policy decision** (tentatively "Phase 18-1" or "Phase 19" - see the
recommendation below) that would need its own justification, tests, and review, exactly like every
other simulation model's assumptions in this document already have their own documented rationale.
Nothing in this phase converts one into the other, even provisionally.

**Why `PredictionResult`/`PredictionSnapshot` gained no new field this phase.** Phase 17 added
`weatherProvenance` because `WeatherData` was *already* flowing into `PredictionEngine` and
therefore into every prediction - an audit trail was immediately meaningful. `RailwayDisruption`
data flows into nothing yet; there is no prediction behavior it could possibly explain, so an audit
field would have no referent to describe. Once a future phase actually wires disruption data into
a prediction (in whatever form that phase designs), that is the point to add the corresponding
audit field - not before.

**Route matching** mirrors `HistoricalSectionDelayProvider` exactly: a (trainNumber, fromStationCode,
toStationCode) key, using the same plain station-code section identity already established by
`RouteSection` - no new section-identity concept, no geographic-coordinate matching (not needed;
not invented).

**Temporal semantics**: `RailwayDisruptionStatusClassifier` computes ACTIVE/FUTURE_EFFECTIVE/
EXPIRED/UNKNOWN_VALIDITY relative to a reference instant - an undated disruption
(`effectiveFrom == null`) is always `UNKNOWN_VALIDITY`, never assumed active, even if an end date
happens to be present without a start date.

## Phase 19: the disruption-to-delay policy - evidence-first, still not model optimization

Phase 18 deliberately stopped at "what disruption is reported to exist." Phase 19 answers "what
delay should the prediction assume" - the policy boundary Phase 18's own recommendation named -
without touching the ETA formula, historical weighting, confidence mathematics, or the six existing
simulation models' internal logic.

### Evidence check: what can and cannot be calibrated today

This codebase has **zero** real evaluated disruption-to-delay outcomes. Evaluation
(`prediction.evaluation.enabled`) has never been enabled in a real deployment (Phase 16H-5/16H-7/17
all built the *capability* to evaluate, but no real accumulation run has ever happened in this
environment), and even if it had, no historical disruption reports exist to correlate against
delay outcomes - `RailwayDisruptionProvider` itself was only established in Phase 18. There is
therefore no dataset from which to derive, e.g., "an engineering block adds on average N minutes"
or "a signal failure's delay correlates with time-of-day" - inspecting for such evidence (per this
phase's own instructions) confirms its absence rather than assuming it.

**What this means concretely**: every relationship this phase's policy encodes is either (a) basic
physics using inputs a real source can actually supply (see speed restriction, below), or (b) an
explicitly labeled provisional heuristic with no claim of empirical grounding. `CalibrationStatus`
(`INSUFFICIENT_DATA`/`CALIBRATED`) makes this a queryable fact rather than an implicit assumption -
`HeuristicDisruptionImpactPolicy.calibrationStatus()` always returns `INSUFFICIENT_DATA` today.
`CALIBRATED` is reserved for a future phase that has actually accumulated and analyzed real
evaluated outcomes broken down by disruption presence - see "Recommended next phase" in the final
report for what that would require.

### The policy boundary

`disruptionimpact.DisruptionImpactPolicy.evaluate(RailwayDisruption, RouteSection, LiveTrainData):
DisruptionImpact` - operates purely on domain objects (no RailRadar/Open-Meteo/JPA/REST types),
assumes the caller has already established the disruption is currently active (temporal filtering
is `DisruptionImpactAggregator`'s job, not the policy's).

**Each disruption type is evaluated independently - there is no shared formula:**

- **`TEMPORARY_SPEED_RESTRICTION`** - the one type with a *physically-grounded* computation:
  `time = distance / speed`, applied twice (once at the train's current speed, once at the
  disruption's own reported `restrictedSpeedKmh`, over the section's own `distanceKm`) and
  subtracted - mirroring `SpeedRestrictionModel`'s own existing formula for the identical physical
  relationship. This is a first-order estimate, not empirically validated against real outcomes: it
  assumes the train travels at exactly the restricted speed for the section's entire length,
  ignoring deceleration/acceleration profiles this codebase has no justified values for (per this
  phase's own instruction not to invent them). Any required input missing, zero, or non-restrictive
  (restricted speed not actually below current speed) yields
  `DisruptionImpactStatus.PRESENT_BUT_NOT_ESTIMABLE` - never a fabricated number filling the gap.
- **`ENGINEERING_BLOCK`**, **`SIGNAL_FAILURE`**, **`CONGESTION`** - no real source in this codebase
  supplies a duration or severity figure precise enough to compute from, so each uses its own
  configurable flat default (`railway-disruption-impact.*-default-delay-minutes` - see
  docs/configuration.md). Explicitly a provisional heuristic. `RailwayDisruption.severity()` (a
  free-form string, when a source supplies one) is captured for audit but deliberately **not** used
  to scale these defaults - no reliable, source-guaranteed severity vocabulary exists to interpret,
  and inventing one would itself be an unjustified inference.
- **`ROUTE_DIVERSION`**, **`MAINTENANCE_BLOCK`**, **`OTHER`** - no impact rule exists at all yet;
  always `PRESENT_BUT_NOT_ESTIMABLE`.

Every estimated figure (including the speed-restriction physics result) is capped at
`railway-disruption-impact.max-single-disruption-delay-minutes` - a safety bound against a
pathological input, not a claim about real-world plausibility.

### Real disruptions and simulation: mutually exclusive per type, never additive

The six existing `DisruptionModel`s are unchanged, but a **real, currently-active** disruption now
suppresses its **simulated** counterpart for that run - never both counted for the same underlying
cause. `disruptionimpact.SimulationSuppression` maps `TEMPORARY_SPEED_RESTRICTION`→
`SPEED_RESTRICTION`, `ENGINEERING_BLOCK`→`ENGINEERING_BLOCK`, `SIGNAL_FAILURE`→`SIGNAL_HALT`,
`CONGESTION`→`HIGH_CONGESTION`; `PredictionService` disables the matching
`SimulationContext.*Enabled` flag. Suppression happens **regardless of whether the real disruption
was estimable** - a confirmed real signal failure means `SignalHaltModel`'s own probabilistic "what
if a signal fails" is no longer a meaningful hypothesis, whether or not this phase's policy could
quantify the real one's minutes yet. `HEAVY_RAIN`/`DENSE_FOG` (weather-driven, no
`RailwayDisruptionType` counterpart) are never suppressed by this mechanism.

With the default configuration (`railway-disruption.provider=unavailable`), no type is ever
suppressed, and every simulated model's behavior is unchanged from before this phase - the full
pre-existing 645-test regression suite passes unmodified, proving this directly rather than by
inspection alone.

### Arithmetic: a fourth, distinct term - never double-counted

```
predictedTotalDelayMinutes = max(0,
    currentDelayMinutes
    + predictedExtraDelayMinutes   (simulated disruption + cascade, already netted against recovery)
    + historicalAdjustmentMinutes  (station- or section-level historical signal)
    + disruptionImpactMinutes)     (Phase 19: real/mock disruption impact, from DisruptionImpactAssessment)
```

`disruptionImpactMinutes` is `DisruptionImpactAssessment.additionalDelayMinutes()`, treated as `0`
only when it is genuinely non-null (i.e. for `NO_KNOWN_DISRUPTION`, always `0`, and `ESTIMATED`).
For `DATA_UNAVAILABLE`/`PRESENT_BUT_NOT_ESTIMABLE` it is `null` and contributes `0` to the
arithmetic *without* being reinterpreted as "confirmed zero disruption" - `PredictionResult.warnings()`
surfaces the distinction explicitly (a present-but-not-estimable disruption produces a warning; an
unavailable one does not, to avoid warning on every single request in the default configuration).
The same term is added to `predictedEta`, once, alongside `historicalAdjustmentMinutes` - never
routed through `recoveryMinutes` (which only ever applies to the simulated contribution, inside
`SimulationEngine`) or added a second time anywhere else.

### Aggregation rules (`DisruptionImpactAggregator`)

1. Only disruptions `RailwayDisruptionStatusClassifier` reports as `ACTIVE` as of the reference
   instant are ever evaluated - future/expired/undated ones are treated exactly like
   `NO_KNOWN_DISRUPTION` (never applied, but also never triggering a "not estimable" warning, since
   nothing active is actually unaccounted for).
2. Disruptions sharing the same (type, trainNumber, fromStationCode, toStationCode) identity are
   de-duplicated to the single most-recently-observed one - a re-reported disruption is never
   double-counted.
3. Distinct types (and, in principle, distinct sections once a future phase aggregates across the
   full remaining route rather than one section at a time - see limitations below) are summed, each
   treated as an independent, plausible additive cause.
4. The total is capped at `railway-disruption-impact.max-aggregate-delay-minutes`
   (`cappedByMaximumAggregate` surfaces whenever this actually clamps something - never a silent
   cap).

### Calibration foundation, not a calibrated model

`PredictionResult`/`PredictionSnapshot` each gained the minimal fields needed to eventually compare
"predictions made with a nonzero real-disruption contribution" against "predictions made without
one" against "actual next-station delay" - `disruptionImpactAssessment`/`disruptionImpactMinutes`
respectively (`null` when unavailable/not-estimable, otherwise the minutes actually added).
`PredictionOutcomeMatcher` carries the snapshot field through evaluation unchanged, exactly like
`evaluationMode`/`weatherProvenance`. **No accuracy-report breakdown by this field was built** - per
this phase's own scope restriction against building a "disruption experiment framework" prematurely
- the column only makes such a breakdown possible later without a backfill. Passing tests prove the
policy's *semantics* (no double-counting, correct temporal filtering, correct aggregation) - they do
not and cannot prove the policy's *numbers* are accurate; that claim is exactly what
`CalibrationStatus.INSUFFICIENT_DATA` exists to prevent anyone from making prematurely.

## Phase 20: empirical calibration, backtesting, and prediction-accuracy evaluation

Phase 19 asked "does the code correctly implement a disruption-impact policy" and answered yes.
Phase 20 asks a harder, different question: "does any of this actually make predictions more
accurate, and by how much - measured, not assumed?" This phase adds an evaluation/ablation
framework (the `evaluation.calibration.*` classes); it does **not** add ML, does not auto-tune
anything on startup, and does not change the evaluated target, the ETA formula, or any provider.

### Evaluation target - unchanged

Still **next-station arrival delay**, exactly as Phase 16H-5 defined it - `predictionMadeAt`,
`currentDelayMinutes`, `predictedExtraDelayMinutes`, `predictedNextStationDelayMinutes`,
`actualDelayMinutes`, `errorMinutes = predicted - actual`. Phase 20 adds analysis on top of this
existing target; it never redefines it.

### The central finding: two components are *structurally* unmeasurable today, not just data-starved

Tracing the exact arithmetic settles a question the rest of this phase depends on:

```
predictedNextStationDelayMinutes = currentDelayMinutes + predictedExtraDelayMinutes   // evaluated
predictedTotalDelayMinutes       = max(0, currentDelayMinutes + predictedExtraDelayMinutes
                                          + historicalAdjustmentMinutes + disruptionImpactMinutes)  // NOT evaluated
```

`historicalAdjustmentMinutes` and `disruptionImpactMinutes` (Phase 19) both feed only
`predictedTotalDelayMinutes` - the destination-scoped figure no evaluation in this codebase ever
compares against an actual outcome. Neither term can move `errorMinutes` by construction. This is
why `HistoricalWeightCalibrationAssessor` and `DisruptionImpactCalibrationAssessor` always report
`CalibrationStatus.INSUFFICIENT_DATA` with `CalibrationBlockerReason.METRIC_STRUCTURALLY_UNAFFECTED`
- a distinct, stronger claim than `INSUFFICIENT_SAMPLE_SIZE` (collecting more snapshots would never
unblock this; only widening the evaluated metric's scope in a future phase could). The chronological
train/validation split machinery (`ChronologicalSplitter`) is still exercised and its sample counts
still reported by `HistoricalWeightCalibrationAssessor`, purely for transparency about how much
real, point-in-time-ordered evidence exists - not because the split changes the conclusion.

By contrast, two things genuinely are measurable against the evaluated metric today:

- **Weather's contribution** - only indirectly, since weather never adds minutes directly; it can
  only change `predictedExtraDelayMinutes` by triggering `HeavyRainModel`/`DenseFogModel` inside
  simulation. `weatherProvenance` (non-null exactly when weather was available) is a real,
  already-persisted partition key - `WeatherContributionEvaluator` compares
  `WITH_WEATHER`/`WITHOUT_WEATHER` on the *same* evaluated population, split by this one field.
- **Simulation's own contribution** - directly, via the *derived* quantity
  `predictedExtraDelayMinutes = predictedNextStationDelayMinutes - currentDelayMinutes`.
  `SimulationContributionEvaluator` compares `WITH_SIMULATION_CONTRIBUTION` (`> 0`) against
  `WITHOUT_SIMULATION_CONTRIBUTION` (`<= 0`) - no schema change was needed. A snapshot records only
  the *aggregate* simulated contribution, not which of the six `DisruptionModel`s fired, so the six
  models cannot be isolated from each other this way - only "simulation contributed something or it
  didn't" is currently measurable.

### Temporal leakage

Every already-evaluated `PredictionSnapshot` is, by construction, valid live evaluation evidence -
its `predictionMadeAt` is genuinely when it happened, and its recorded weather/historical/disruption
fields are exactly what the live prediction actually used at that instant (Phase 16H-7's
`referenceInstant`/cutoff mechanisms already guarantee this for station/section historical
profiles). What is **not** point-in-time reproducible is *counterfactual replay* - re-running an
old prediction under a different historical weight or a different weather/disruption input. Weather
(Open-Meteo) and railway-disruption data carry no historical archive at all in this codebase, so no
snapshot's weather/disruption inputs could ever be reconstructed as they stood at a past instant;
only the value already captured on the snapshot can be read back. `DataQualityAssessor` documents
this distinction in its `pointInTimeReproducibilityNote` rather than silently assuming replay is
possible. `ChronologicalSplitter` enforces the ordering half of leakage-prevention directly: sorted
strictly by `predictionMadeAt`, every training-period instant is guaranteed `<=` every
validation-period instant - never a random shuffle, which would leak future information into a
"calibration" period for temporally-ordered data.

### Ablation/model-variant methodology

Every comparison this phase builds is **paired**: one already-evaluated population, partitioned by
a real recorded field, never two differently-sourced populations compared and the difference called
an improvement. `byWeatherAvailability`/`bySimulationContribution` are the only two ablation
breakdowns built this way, for the reasons above. A `historicalAdjustmentSource`/disruption-impact
breakdown is deliberately **not** built the same way as an accuracy-improvement comparison - it
would be spuriously uninformative by construction, since neither term can move the evaluated error.

### Metrics

`ErrorDistributionCalculator` adds median and P90 absolute error (nearest-rank method - no
interpolation, since interpolation implies a precision this phase's own instructions warn against
manufacturing) alongside the existing MAE/RMSE/bias/improvement-percent (`PredictionAccuracyCalculator`/
`PredictionAccuracyReportBuilder`, unchanged). Every `AblationVariantResult` and
`ConfidenceBucketMetrics` carries its own `sampleCount` and an explicit `sufficientSample` flag
(gated by `evaluation.calibration.minimum-sample-count`) - a client must never read a MAE number
without also seeing whether it is backed by enough real evidence to trust.

### Confidence-score audit

`ConfidenceCalibrationAuditor` buckets already-evaluated snapshots by `confidenceScore` using the
same `ConfidenceThresholds` the live `ConfidenceCalculator` uses (never a separate, duplicated
threshold definition), then checks whether mean absolute error is non-increasing as confidence rises
across every bucket with a sufficient sample. This is a read-only audit - it never modifies the
confidence formula itself, and reports `monotonic=false` with an explicit explanation whenever fewer
than two buckets have enough real samples to compare, rather than fabricating a verdict from an
under-powered comparison.

### Data-quality report

`DataQualityAssessor` (a genuine, DB-backed inventory - `findAll()` on the existing
`PredictionSnapshotRepository`, no new query) reports total/evaluated/exact/approximate/pending/
not-evaluable snapshot counts, weather/historical/disruption/simulation availability counts, and
distinct-train/station counts - every number a real database count, never estimated or padded. With
no database configured (the default - no `postgres` profile active) or a database with zero rows,
it returns `DataQualityReport.empty(...)` with an explicit, different explanatory note for each of
those two distinct "nothing to report" cases.

### Calibration status states

`CalibrationStatus` (extended from Phase 19's two values): `NOT_CALIBRATED` (never attempted),
`INSUFFICIENT_DATA` (attempted, but blocked - see `CalibrationBlockerReason` for exactly why),
`PROVISIONALLY_CALIBRATED` (a candidate value was selected and validated, but on too small/narrow a
sample to fully trust), `VALIDATED` (selected from an earlier chronological period, confirmed on a
later, disjoint one, with a practically meaningful improvement). No code path in this codebase
currently produces `PROVISIONALLY_CALIBRATED` or `VALIDATED` - both calibration assessors are
structurally blocked today (see above), so `overallCalibrationStatus` is always `INSUFFICIENT_DATA`.
This is the correct, honest result for the current codebase, not a placeholder - see "Actual
real-data sample sizes" in the Phase 20 completion report for why.

### Configuration

`evaluation.calibration.*` (four keys, deliberately minimal, mirroring `prediction.evaluation.*`):
`enabled` (default `false`), `minimum-sample-count` (default `30`), `validation-split` (default
`0.3`, strictly between 0 and 1), `minimum-practical-improvement-percent` (default `5.0` - reserved
for a future phase that can actually reach `VALIDATED`; nothing in this phase compares against it
yet, since no calibration attempt gets far enough to produce a candidate value). See
docs/configuration.md.

### API

`GET /api/v1/evaluation/accuracy` gains one new, nullable field: `calibrationEvaluation`
(`CalibrationEvaluationResponse`) - populated only when **both** `prediction.evaluation.enabled=true`
*and* `evaluation.calibration.enabled=true` (the latter is checked inside the former's already-enabled
branch, since a calibration analysis with prediction-accuracy tracking itself disabled would have no
snapshots to analyze regardless). `null` otherwise - never a fabricated empty object. No new
endpoint was added, per this phase's own preference for structured extension over endpoint
proliferation.

### Database

No migration was needed. Every Phase 20 class reads the existing `prediction_snapshots` table
exactly as Phase 16H-5/16H-6 already query it (`findAll()`/`findByEvaluationStatusIn`) - raw
snapshots remain the untouched source of truth; nothing here ever writes a calibrated value back
over a raw observation.

### Empirical vs. heuristic - the load-bearing distinction of this phase

"Heuristic" (Phase 19's `HeuristicDisruptionImpactPolicy` defaults, `prediction.historical-adjustment.weight`'s
current value) means: a defensible engineering assumption, never validated against real outcomes.
"Empirical" means: derived from real, point-in-time-valid evaluated `PredictionSnapshot`s, using a
chronological calibration/validation split, with the sample size and validation result disclosed.
Every value this codebase uses today is the former. Phase 20 built the machinery to produce the
latter, honestly reports that the machinery currently has nothing to work with (real dataset: zero
recorded snapshots in this environment - `prediction.evaluation.enabled=false` by default and no
`postgres` profile active in any verification run this phase performed), and refuses to manufacture
a calibrated-sounding number in the meantime.
