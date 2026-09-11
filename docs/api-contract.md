# API Contract (Phase 15)

## Endpoint

```
GET /api/v1/trains/{trainNumber}/prediction
```

Returns the same `PredictionOutput` JSON contract documented in
[docs/json-output.md](json-output.md) - the REST response and a generated `prediction_output.json`
(Phase 14) represent the identical shape. Nothing about the prediction mathematics, simulation,
cascade, or recovery logic changed to add this endpoint; the controller only adds an HTTP
boundary in front of the existing `PredictionService`.

Interactive documentation (OpenAPI/Swagger, generated from the controller's own annotations - see
`PredictionController`): `/swagger-ui.html` and `/v3/api-docs` once the app is running.

## Request

| Part | Value |
|---|---|
| Method | `GET` |
| Path parameter | `trainNumber` - **exactly 5 digits** (e.g. `12952`) |

The 5-digit rule comes directly from RailRadar's own documented format (see
[docs/architecture.md](architecture.md) / Phase 3), not an invented restriction. Validation
happens before the request reaches `PredictionService` or any external provider - an invalid
train number never causes a RailRadar call.

## Successful response — `200 OK`

Body: `PredictionOutput` (see [docs/json-output.md](json-output.md) for the full field-by-field
shape and data classification). Example:

```json
{
  "trainNumber": "12952",
  "trainName": "New Delhi - Mumbai Central Rajdhani Express",
  "currentStatus": "RUNNING",
  "currentDelayMinutes": 12,
  "currentStation": { "code": "KOTA", "name": "Kota Jn", "latitude": 25.18, "longitude": 75.83 },
  "nextStation": { "code": "RTM", "name": "Ratlam Jn", "latitude": 23.33, "longitude": 75.04 },
  "distanceFromOriginKm": 465.0,
  "remainingDistanceKm": 919.0,
  "estimatedSpeedKmh": 92.5,
  "estimatedSectionCondition": "NORMAL",
  "prediction": {
    "baseTravelTimeMinutes": 596.1,
    "currentDelayMinutes": 12,
    "predictedExtraDelayMinutes": 27,
    "historicalAdjustmentMinutes": 2,
    "historicalAdjustmentSource": "STATION_FALLBACK",
    "historicalAdjustmentProvenance": "mock-provider",
    "recoveryMinutes": 3,
    "predictedTotalDelayMinutes": 41,
    "predictedEta": "2026-09-09T20:25:00Z"
  },
  "simulation": {
    "triggeredDisruptions": [
      { "disruptionType": "HEAVY_RAIN", "disruptionDelayMinutes": 18, "explanation": "Weather reading reports heavy rain - simulated delay of 18 min" }
    ],
    "cascadeEffects": [
      { "description": "Simulated: increased section occupancy propagates delay to affected entity #1", "depth": 1, "additionalDelayMinutes": 6 }
    ],
    "recoveryMinutes": 3
  },
  "confidence": {
    "score": 60.0,
    "level": "MEDIUM",
    "contributingFactors": ["Live train data is complete (next station and remaining distance known)"],
    "warnings": ["Route section distance is unknown, limiting route information quality"]
  },
  "warnings": ["Route section distance is unknown, limiting route information quality"]
}
```

`prediction.historicalAdjustmentSource` (Phase 16H-3) is one of `"SECTION"`,
`"STATION_FALLBACK"`, or `"NONE"` - which strategy actually produced
`historicalAdjustmentMinutes`, so a client no longer has to infer this from `warnings` text.
`prediction.historicalAdjustmentProvenance` is a separate axis - where that winning strategy's
data came from (e.g. `"railradar"`, `"mock-provider"`, `"unavailable"`, or an explicit
`"mixed(...)"` combination) - see docs/json-output.md and docs/prediction-model.md for the full
distinction and selection rules.

## Error responses

Every error uses one consistent envelope, built by `GlobalExceptionHandler` (in `exception`,
consistent with that package's documented purpose since Phase 1):

```json
{
  "timestamp": "2026-09-09T18:16:58.201953500Z",
  "status": 400,
  "error": "INVALID_TRAIN_NUMBER",
  "message": "trainNumber must be exactly 5 digits (e.g. 12952)",
  "path": "/api/v1/trains/abc/prediction"
}
```

| Scenario | Status | `error` | Notes |
|---|---|---|---|
| Train number isn't 5 digits | 400 | `INVALID_TRAIN_NUMBER` | Caught before calling `PredictionService` |
| No train found for that number | 404 | `TRAIN_NOT_FOUND` | From `TrainNotFoundException` |
| Train has no next station (arrived/terminated) | 422 | `PREDICTION_NOT_APPLICABLE` | From `PredictionNotApplicableException` - nothing future to predict |
| RailRadar returned unusable data | 502 | `INVALID_TRAIN_DATA` | From `MalformedRailRadarResponseException`; the specific missing/bad field is logged, never returned |
| RailRadar unreachable, timed out, rate-limited, or rejected our API key | 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | From `RailRadarUnavailableException`/`RailRadarAuthenticationException` (and any future `RailRadarException` subtype) - the client can't act differently on any of these, so they share one response; the real cause is logged server-side only |
| Anything else unexpected | 500 | `INTERNAL_ERROR` | Full exception logged server-side; never returned to the client |

No response ever includes a stack trace, a raw exception message, or an internal detail (e.g.
RailRadar's name as a vendor) - client-facing messages for 502/503/500 are deliberately generic.

## Current limitations

- No pagination, filtering, or bulk endpoint - one train per request, as scoped.
- No authentication/authorization on this endpoint yet.
- Manual end-to-end testing against the real RailRadar API wasn't practical in this environment
  (no `RAILRADAR_API_KEY` configured) - verified instead via `PredictionControllerTest` (mocked
  `PredictionService`, real controller/validation/exception-handling/mapper) and by starting the
  app and confirming `/v3/api-docs` and the validation error path respond correctly over real
  HTTP.

# Prediction Accuracy Report (Phase 16H-6)

## Endpoint

```
GET /api/v1/evaluation/accuracy
```

Read-only reporting over the Phase 16H-5 prediction-accuracy-tracking foundation - see
`EvaluationController`/`PredictionAccuracyReportService`. Never triggers evaluation itself (that
remains `PredictionEvaluationRefreshScheduler`'s job, offline) and never affects live prediction.

## Request

| Part | Value |
|---|---|
| Method | `GET` |
| Query parameter `trainNumber` (optional) | Exactly 5 digits |
| Query parameter `stationCode` (optional) | Restrict to this target station code |
| Query parameter `predictionMadeFrom` (optional) | ISO-8601 instant; inclusive lower bound on `predictionMadeAt` |
| Query parameter `predictionMadeTo` (optional) | ISO-8601 instant; inclusive upper bound on `predictionMadeAt` |
| Query parameter `historicalAdjustmentSource` (optional) | One of `SECTION`, `STATION_FALLBACK`, `NONE` |
| Query parameter `evaluationStatus` (optional) | One of `PENDING`, `EVALUATED_EXACT`, `EVALUATED_APPROXIMATE`, `NOT_EVALUABLE` (only `EVALUATED_*` rows can ever appear in the report - other values simply produce an empty result, not an error) |

Every parameter is independent and optional; omitting all of them reports over every evaluated
snapshot. All supplied parameters must match simultaneously (a logical AND).

## Successful response — `200 OK`

Body: `AccuracyReportResponse`. Example (evaluation enabled, some data evaluated):

```json
{
  "evaluationEnabled": true,
  "overall": {
    "sampleCount": 12,
    "exactCount": 10,
    "approximateCount": 2,
    "comparison": {
      "currentModel": { "sampleCount": 12, "meanAbsoluteError": 3.4, "rootMeanSquaredError": 4.8, "bias": 1.1 },
      "baseline": { "sampleCount": 12, "meanAbsoluteError": 5.9, "rootMeanSquaredError": 7.2, "bias": -0.4 }
    },
    "improvementPercent": 42.4
  },
  "exactOnly": {
    "currentModel": { "sampleCount": 10, "meanAbsoluteError": 3.1, "rootMeanSquaredError": 4.5, "bias": 1.0 },
    "baseline": { "sampleCount": 10, "meanAbsoluteError": 5.7, "rootMeanSquaredError": 7.0, "bias": -0.3 }
  },
  "bySource": {
    "SECTION": { "sampleCount": 3, "exactCount": 3, "approximateCount": 0, "comparison": { "...": "..." }, "improvementPercent": 12.0 },
    "STATION_FALLBACK": { "sampleCount": 9, "exactCount": 7, "approximateCount": 2, "comparison": { "...": "..." }, "improvementPercent": 51.0 },
    "NONE": { "sampleCount": 0, "exactCount": 0, "approximateCount": 0, "comparison": { "...": "..." }, "improvementPercent": null }
  },
  "byProvenance": {
    "railradar": { "sampleCount": 8, "exactCount": 7, "approximateCount": 1, "comparison": { "...": "..." }, "improvementPercent": 40.1 },
    "mock-provider": { "sampleCount": 4, "exactCount": 3, "approximateCount": 1, "comparison": { "...": "..." }, "improvementPercent": 48.9 }
  },
  "byEvaluationMode": {
    "LIVE_EVALUATION": { "sampleCount": 12, "exactCount": 10, "approximateCount": 2, "comparison": { "...": "..." }, "improvementPercent": 42.4 },
    "HISTORICAL_BACKTEST": { "sampleCount": 0, "exactCount": 0, "approximateCount": 0, "comparison": { "...": "..." }, "improvementPercent": null }
  }
}
```

Field notes:

- **Sign convention** (unchanged from Phase 16H-5): `error = predicted - actual`. Positive `bias`
  means the model over-predicts delay; negative means it under-predicts. `sampleCount == 0`
  produces `0.0` for every statistic - a placeholder, never a measured "zero error".
- **`overall` vs `exactOnly`**: `overall` combines `EVALUATED_EXACT` and `EVALUATED_APPROXIMATE`
  snapshots (its own `exactCount`/`approximateCount` expose the split); `exactOnly` recomputes the
  same current-model/baseline comparison using *only* unambiguous exact matches, so a caller can
  see whether approximate-match uncertainty is meaningfully affecting the headline numbers.
- **`bySource`** always has all three keys (`SECTION`, `STATION_FALLBACK`, `NONE`), even at
  `sampleCount: 0` - a missing key would be indistinguishable from "not computed". No slice ever
  claims one source is "better"; that requires a sample-size/significance judgment this phase
  deliberately does not make.
- **`byProvenance`** has one key per provenance string actually observed (e.g. `"railradar"`,
  `"mock-provider"`, or a `"mixed(...)"` composite) - present only when it occurs, so mock-derived
  evaluation data is never silently folded into a real-data bucket.
- **`byEvaluationMode`** (Phase 16H-7) always has both `LIVE_EVALUATION` and `HISTORICAL_BACKTEST`
  keys, even though only `LIVE_EVALUATION` is currently ever produced (see
  docs/prediction-model.md's Phase 16H-7 notes on why full historical replay isn't yet possible) -
  so the two are never silently blended once/if a future phase does produce backtest evidence.
- **`improvementPercent`** is `(baselineMae - currentModelMae) / baselineMae * 100`; `null` (never
  `0.0` or a fabricated number) whenever the baseline's own MAE is `0.0`.

### Evaluation-disabled response — still `200 OK`

When `prediction.evaluation.enabled=false` (the default):

```json
{ "evaluationEnabled": false, "overall": null, "exactOnly": null, "bySource": null, "byProvenance": null }
```

This is an explicit, predictable state - not an error, and not a fabricated empty dataset. A
client must never confuse this with "enabled, but nothing evaluated yet" (`evaluationEnabled:
true`, every slice present with `sampleCount: 0`) - the two are deliberately distinguishable.
Evaluation is never auto-enabled by calling this endpoint.

## Error responses

Reuses the same `ErrorResponse` envelope as `GET /api/v1/trains/{trainNumber}/prediction`.

| Scenario | Status | `error` | Notes |
|---|---|---|---|
| `trainNumber` filter isn't 5 digits | 400 | `INVALID_TRAIN_NUMBER` | Same `ConstraintViolationException` handler as the prediction endpoint |
| An enum filter (`historicalAdjustmentSource`, `evaluationStatus`) or a timestamp filter can't be parsed | 400 | `INVALID_FILTER` | From `MethodArgumentTypeMismatchException` |
| `predictionMadeFrom` is after `predictionMadeTo` | 400 | `INVALID_FILTER` | From `InvalidAccuracyFilterException`, thrown by `PredictionAccuracyReportService` |
| No evaluated data matches the filter | 200 | — | A valid result, not an error: every slice is present with `sampleCount: 0` |
| Anything else unexpected (e.g. a repository failure) | 500 | `INTERNAL_ERROR` | Same catch-all as every other endpoint; full exception logged server-side only |

## Current limitations

- No pagination - a report is computed over every matching evaluated snapshot in one response.
  Acceptable at this phase's expected data volume (evaluation is disabled by default; see
  docs/prediction-model.md's Phase 16H-6 notes on the in-memory-filtering decision).
- No authentication/authorization, consistent with the rest of this API.
- Filtering happens entirely in memory over `findByEvaluationStatusIn` (the query the Phase 16H-5
  foundation already had) - no new repository query or database index was added this phase; see
  docs/prediction-model.md for why that's currently sufficient and what would change it.
