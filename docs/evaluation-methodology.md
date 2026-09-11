# Evaluation Methodology

This document is the single place that explains how RailPredictor's own accuracy is measured,
what "real" vs "synthetic/controlled" evidence means in this codebase, and exactly how much real
evidence currently exists. Read this before trusting (or calibrating against) any number the
system reports about itself.

## Two entirely separate kinds of evaluation

### 1. Real evaluation (production evidence)

- Source: live RailRadar responses, recorded as `historical_observations`, matched against
  `prediction_snapshots` by `PredictionOutcomeMatcher`.
- Pipeline: `HistoricalObservationCollectionScheduler` / `PredictionCollectionScheduler` /
  `PredictionEvaluationRefreshScheduler` — all opt-in (`historical.collection.enabled`,
  `prediction.collection.enabled`, `prediction.evaluation.enabled`, all default `false`).
- Reported via `GET /api/v1/evaluation/accuracy`, backed by `PredictionAccuracyReportService` /
  `CalibrationEvaluationReportBuilder`.
- Gated by `evaluation.calibration.minimum-sample-count` (default 30) before any calibration
  component is considered statistically eligible — see "Current real-data status" below.
- A `quarantined` snapshot (see `PredictionSnapshotQuarantineService`, migration `V10`) is real data
  known to be corrupted at ingestion; it is retained for audit but permanently excluded from every
  metric.

### 2. Controlled / synthetic evaluation (`ControlledEvaluationSuiteTest`)

- Source: hand-constructed, deterministic inputs (fixed `Clock`, no RNG) run directly through
  `PredictionEngine`.
- Purpose: verify safety invariants (non-negative delay, bounded totals, graceful degradation,
  deterministic output) across a fixed scenario matrix — zero delay, existing delay, increasing
  delay, recovery, each disruption type individually and combined, extreme-but-bounded delay,
  unavailable weather/disruption, insufficient historical data, partial route information.
- **Never produces or reports a real-world accuracy claim.** There is no "actual" outcome
  independent of the synthetic inputs to compare against, so computing an MAE/RMSE here would be
  circular. This suite asserts *safety*, not *accuracy*.
- **Never persisted** to `prediction_snapshots` or any other production table, and never mixed
  into the `/api/v1/evaluation/accuracy` report.

**Rule: a synthetic result is never stored, logged, or reported as if it were real evidence.**
If you are looking at a number and unsure which kind it is, check its source: anything coming out
of the live database or `/api/v1/evaluation/accuracy` is real; anything from
`ControlledEvaluationSuiteTest` (or any future test using the word "synthetic"/"controlled" in its
name) is not.

## Current real-data status (last measured; will drift as collection continues)

- **13 valid (non-quarantined) evaluated samples** — far below the 30-sample calibration gate.
- All 13 currently have `historicalAdjustmentSource=NONE` (real Postgres-backed historical
  providers are active as of Phase 23D/24, but every accumulated profile row is still at
  `sample_count≤1`, below the `minimum-sample-count` thresholds of 5 (station) / 10 (section)).
- 0 samples have real weather data (RailRadar's live-status response does not supply station
  coordinates for the trains observed so far, so `PredictionService.fetchWeather()` never even
  calls a weather provider for them — an external data limitation, not a code defect).
- 0 samples have real railway-disruption data (`railway-disruption.provider=unavailable` is the
  intentional default — no reliable public real-time disruption source has ever been found; see
  `docs/architecture.md`'s Phase 18 notes).
- Descriptive-only accuracy at n=13: model MAE ≈38, baseline (current-delay-only) MAE ≈8. **This is
  not a statistically meaningful comparison** — it is reported here only as the literal current
  value, not as a claim about real-world accuracy. Every calibration component
  (`CalibrationEvaluationReportBuilder`) independently reports `INSUFFICIENT_DATA` until its own
  30-sample gate is met.

## Why calibration has not been performed

Every calibration/ablation assessor in this codebase
(`HistoricalWeightCalibrationAssessor`, `DisruptionImpactCalibrationAssessor`,
`WeatherContributionEvaluator`, `SimulationContributionEvaluator`, `ConfidenceCalibrationAuditor`)
enforces the same `evaluation.calibration.minimum-sample-count` gate before producing anything
other than `INSUFFICIENT_DATA`/`NOT_EVALUABLE`. This is deliberate: fitting any coefficient to 13
samples — concentrated in a handful of (train, station) pairs, with zero variation in
historical/weather/disruption inputs — would produce a confident-looking number with no real
statistical support. See each assessor's own Javadoc for its exact reasoning.

**No production coefficient in this codebase has ever been empirically calibrated against real
data.** Every simulation probability/delay range, every confidence weight/threshold, the
historical-adjustment weight, and the disruption-impact defaults are all documented, provisional
heuristics (see `docs/prediction-model.md`/`docs/architecture.md`) pending sufficient real
evidence.

## Chronological backtest status: structurally NOT_READY

A true `HISTORICAL_BACKTEST` would need to reconstruct, at a past instant: (1) the historical
station/section delay profile as it existed then — possible, since `historical_delay_profiles`/
`historical_section_delay_profiles` are point-in-time reproducible; (2) the weather at that instant
— **impossible**, Open-Meteo has no historical archive integration in this codebase; (3) the
railway-disruption state at that instant — **impossible**, no disruption data (real or historical)
exists at all. Because two of the four required inputs cannot be reconstructed, a full backtest
cannot be built honestly today. `LIVE_EVALUATION` is never treated as equivalent to
`HISTORICAL_BACKTEST` anywhere in this codebase.

## What would change this document

- Real evaluated-sample count crossing 30 (continued unattended `historical.collection`/
  `prediction.collection` operation, over multiple days/weeks — not a configuration change).
- Any individual (train, station) or (train, section) profile crossing its minimum-sample
  threshold (5/10) — automatic once enough real repeated journeys are observed.
- RailRadar beginning to supply station coordinates (external, out of this project's control) —
  would unblock real weather evidence.
- A reliable real-time railway-disruption data source becoming available (external) — would
  unblock real disruption evidence.
- A future decision to build a genuine (if partial) backtest mechanism, explicitly scoped to what
  *can* be reconstructed (historical only) and clearly labeled as such.
