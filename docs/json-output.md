# JSON Output (Phase 14)

`PredictionOutputMapper` turns the internal `PredictionResult` into `PredictionOutput` - the
stable, frontend-facing JSON shape. This document is the field-by-field data classification
required for this phase: every field is exactly one of **real**, **derived**, **simulated**,
**mocked**, or **assumed** data (see `docs/architecture.md` for the general definitions).

## Top level

| Field | Classification | Notes |
|---|---|---|
| `trainNumber`, `trainName`, `currentStatus`, `currentDelayMinutes` | Real | As reported by RailRadar (Phase 3) |
| `currentStation`, `nextStation` | Real | Station code/name/coordinates from RailRadar's route data |
| `distanceFromOriginKm` | Real | RailRadar's own cumulative distance figure |
| `remainingDistanceKm` | Derived | Computed from RailRadar's route data (Phase 3), not reported directly |
| `estimatedSpeedKmh` | Real | RailRadar's `currentLocation.speedKmh`, when reported |
| `estimatedSectionCondition` | Derived (heuristic) | `SectionAnalyzer` estimates this from delay alone - not a measurement of actual occupancy (Phase 4) |

## `prediction`

| Field | Classification |
|---|---|
| `baseTravelTimeMinutes` | Derived - remaining distance / speed (or an **assumed** average speed if speed is unknown/zero) |
| `currentDelayMinutes` | Real (repeated from top level for context) |
| `predictedExtraDelayMinutes`, `recoveryMinutes` | Simulated |
| `historicalAdjustmentMinutes` | Derived from historical data when available, else `0` (an explicit assumption - see `HistoricalDelayCalculator`) |
| `historicalAdjustmentSource` | Derived (Phase 16H-3) - one of `"SECTION"`, `"STATION_FALLBACK"`, `"NONE"`: *which strategy* produced `historicalAdjustmentMinutes` |
| `historicalAdjustmentProvenance` | Same classification as `HistoricalDelay.source()` below - *where* that winning strategy's data came from; a genuinely separate axis from `historicalAdjustmentSource`, never conflated with it |
| `predictedTotalDelayMinutes`, `predictedEta` | Derived (see `docs/prediction-model.md` for the formula) |

## `simulation`

| Field | Classification |
|---|---|
| `triggeredDisruptions`, `cascadeEffects` | Simulated - never a measured railway event, see each model's own Javadoc for its assumptions |
| `recoveryMinutes` | Simulated (same value as `prediction.recoveryMinutes`, shown here for the simulation summary) |

## `confidence`

| Field | Classification |
|---|---|
| `score`, `level` | Derived from data-availability signals - not a statistically calibrated probability of accuracy (Phase 12) |
| `contributingFactors`, `warnings` | Derived explanations of the above |

## `warnings` (top level)

Derived: a plain-English merge of every assumption/degraded-input note raised anywhere in the
pipeline (missing distance/speed, no historical data, `confidence.warnings()`, etc.) - see
`PredictionEngine`.

## Where mocked data enters, and how it's disclosed

**Weather** is supplied by `MockWeatherProvider` (Phase 5) until a real integration exists
(Phase 17). **Historical delay** data can come from either of two independent pipelines - see
below - each with its own mock/real provider pair (Phases 6, 16). Unlike most of the pipeline,
these carry a provenance tag their consumers can inspect:

- `WeatherData.source()` is a free-form string identifying where the reading came from -
  `DataProvenance.MOCK` ("mock-provider") for the mock provider, and any other value (e.g. a
  vendor name) once a real provider is wired in.
- **Historical delay provenance is two-layered since Phase 16H-3.** `historicalAdjustmentSource`
  (`"SECTION"`/`"STATION_FALLBACK"`/`"NONE"`) says *which* of the two historical pipelines actually
  produced `historicalAdjustmentMinutes`; `historicalAdjustmentProvenance` says *where* that
  winning pipeline's data came from - `DataProvenance.MOCK` ("mock-provider"),
  `DataProvenance.UNAVAILABLE` ("unavailable") when neither pipeline had anything usable
  (`historicalAdjustmentSource: "NONE"`), or any other value (a vendor/database name, or an
  explicit `"mixed(...)"` combination for section data blending sources) once real data is wired
  in and populated.
- `ConfidenceCalculator` reads `WeatherData.source()` directly, and reads the already-resolved
  `HistoricalAdjustmentResolution` (source + provenance) rather than either raw historical type -
  this is what lets it correctly recognise real section-sourced data even when the station-level
  lookup alone would have looked empty. Mock or real, available data still counts the same toward
  the confidence score (data being *available* is what's being scored) - but when the resolved
  historical provenance is `DataProvenance.MOCK`, an explicit warning is added:
  `"Historical delay data is simulated (mock-provider), not derived from real historical records"`.
  The historical contributing-factor text includes both the strategy and the source directly
  (e.g. `"... (strategy: STATION_FALLBACK, source: mock-provider)"`), so the provenance is visible
  even without special-casing.
- Those warnings flow into `PredictionResult.warnings()` / `PredictionOutput.warnings` through the
  existing merge in `PredictionEngine`. The historical source/provenance *themselves* are no longer
  left to warning text alone (a Phase 16H-2 gap) - they are now first-class JSON fields,
  `prediction.historicalAdjustmentSource`/`prediction.historicalAdjustmentProvenance` (see the
  `prediction` table above).

Everything else in the JSON (disruptions, cascade effects, section condition, confidence score
itself) is inherently model output, not a "real vs. mock" data source question - see the
per-section tables above for how each of those is classified instead.

The example `prediction_output.json` in this phase was generated against the mock providers, and
its `confidence.warnings` shows the historical-mock warning described above.

## Design choices for a stable frontend contract

- **Explicit `null`, never a missing key.** `nextStation`, `remainingDistanceKm`,
  `estimatedSpeedKmh`, and station coordinates are `null` when unknown rather than omitted, so the
  JSON shape never changes based on what data happened to be available for a given train.
- **A dedicated DTO tree (`model.dto`), not `PredictionResult` serialized directly.** The internal
  domain model can change (new calculators, new fields) without automatically changing the public
  contract - `PredictionOutputMapper` is the one seam where that translation is decided.
- **Only triggered disruptions are included** in `simulation.triggeredDisruptions` - the full
  6-model evaluation (including "didn't fire" results) is an internal simulation detail a
  frontend doesn't need.
