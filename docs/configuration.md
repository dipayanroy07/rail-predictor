# Configuration

All configuration lives in `src/main/resources/application.properties` (always loaded) and
profile-specific files, using `${ENV_VAR:default}` placeholders throughout - nothing is
hardcoded, and secrets are never committed (see `railradar.api-key`, `spring.datasource.password`).

## Always-active configuration groups

| Prefix | Purpose |
|---|---|
| `railradar.*` | Base URL, API key, timeout for the real RailRadar client (Phase 3). Requires a real `RAILRADAR_API_KEY` (`Authorization: Bearer <key>`, verified current against https://railradar.in/docs/live-train-status) - with no key configured, `RailRadarClient` fails fast with `RailRadarAuthenticationException` (mapped to `503 EXTERNAL_SERVICE_UNAVAILABLE`) rather than sending a request RailRadar would reject anyway. Startup logs `RailRadar client configured: ... apiKeyConfigured=<true|false>` - never the key itself - so a `503` can be diagnosed as "no credential configured" without exposing it. |
| `route.section-analysis.*` | Delay thresholds for `DelayBasedSectionAnalyzer` (Phase 4) |
| `weather.provider`, `weather.mock.*`, `weather.openmeteo.*` | Weather provider selection (Phase 5/17) - see "Choosing which `WeatherProvider` is active" below |
| `historical.mock.*` | The fixed statistics `MockHistoricalDelayProvider` returns (Phase 6) |
| `simulation.disruption.*` | Per-model trigger probability/delay range (Phase 7) |
| `simulation.cascade.*` | Cascade decay model (Phase 9) |
| `simulation.recovery.*` | Recovery model (Phase 10) |
| `prediction.travel-time.*`, `prediction.historical-adjustment.*` | ETA formula inputs (Phase 11) |
| `prediction.confidence.*` | Confidence scoring weights/thresholds (Phase 12) |
| `evaluation.calibration.*` | Empirical calibration/ablation evaluation (Phase 20) - see below |

Every one of these follows the same pattern: a sensible default baked in, overridable via a named
environment variable, documented inline in `application.properties` itself.

## Empirical calibration/ablation evaluation (Phase 20)

| Property | Default | Meaning |
|---|---|---|
| `evaluation.calibration.enabled` | `${EVALUATION_CALIBRATION_ENABLED:false}` | Attaches a `calibrationEvaluation` object to `GET /api/v1/evaluation/accuracy` when `true` - only takes effect when `prediction.evaluation.enabled` is also `true` (see docs/prediction-model.md's Phase 20 notes). Never auto-enabled, never computed on startup. |
| `evaluation.calibration.minimum-sample-count` | `${EVALUATION_CALIBRATION_MINIMUM_SAMPLE_COUNT:30}` | Below this many real samples, an ablation variant or confidence bucket is marked `sufficientSample=false` - its metrics are still shown, but must not be trusted. Must be `>= 1`. |
| `evaluation.calibration.validation-split` | `${EVALUATION_CALIBRATION_VALIDATION_SPLIT:0.3}` | Fraction of chronologically-ordered evaluated snapshots assigned to the later "validation" period by `ChronologicalSplitter` (the rest form the earlier "training/calibration" period). Must be strictly between 0 and 1. |
| `evaluation.calibration.minimum-practical-improvement-percent` | `${EVALUATION_CALIBRATION_MIN_IMPROVEMENT_PERCENT:5.0}` | Reserved for a future phase that can actually reach `PROVISIONALLY_CALIBRATED`/`VALIDATED` - the minimum improvement a candidate value must show over the current one before it's considered practically (not just statistically) meaningful. Must be `>= 0`. Unused by any calibration attempt this phase can currently produce, since both existing attempts are structurally blocked (see docs/prediction-model.md). |

Deliberately only four keys - no per-component tuning parameters, and nothing here is ever
optimized automatically; every calibration attempt either reports why it's blocked or (in a future
phase, once unblocked) reports exactly what was selected and how it was validated.

## Choosing which `WeatherProvider` is active (Phase 17)

| Property | Default | Meaning |
|---|---|---|
| `weather.provider` | `${WEATHER_PROVIDER:mock}` | `mock` (default) → `MockWeatherProvider`; `openmeteo` → `OpenMeteoWeatherProvider` |

Exactly one `WeatherProvider` bean exists at a time, each `@ConditionalOnProperty`-gated on this
property - mirrors `historical.provider`'s exact selection rule. Never a silent fallback: an
unrecognised value selects neither bean, and `PredictionService`'s injection then fails to find
one, which is the correct "fail loud" outcome for a typo - not "quietly keep using mock."

### Mock weather (default)

| Property | Default | Meaning |
|---|---|---|
| `weather.mock.condition` | `${WEATHER_MOCK_CONDITION:CLEAR}` | The fixed `WeatherCondition` `MockWeatherProvider` always returns |
| `weather.mock.temperature-celsius` | `${WEATHER_MOCK_TEMPERATURE_CELSIUS:25.0}` | Fixed temperature |
| `weather.mock.visibility-meters` | `${WEATHER_MOCK_VISIBILITY_METERS:10000}` | Fixed visibility |
| `weather.mock.precipitation-mm` | `${WEATHER_MOCK_PRECIPITATION_MM:0.0}` | Fixed precipitation |

### Open-Meteo (real weather, Phase 17)

| Property | Default | Meaning |
|---|---|---|
| `weather.openmeteo.base-url` | `${WEATHER_OPENMETEO_BASE_URL:https://api.open-meteo.com}` | Open-Meteo's forecast API base URL |
| `weather.openmeteo.api-key` | `${WEATHER_OPENMETEO_API_KEY:}` | **Not required** for Open-Meteo's free/non-commercial tier (this application's default use case) - leave unset. Only needed for a paid Open-Meteo plan; if set, sent as an `apikey` query parameter (Open-Meteo's own convention), never a header, never logged |
| `weather.openmeteo.timeout` | `${WEATHER_OPENMETEO_TIMEOUT:5s}` | Bounds the entire request/response (mirrors `railradar.timeout`'s single-duration `.block(timeout)` pattern) - a slow or hanging Open-Meteo response can never stall the prediction endpoint indefinitely |
| `weather.openmeteo.dense-fog-visibility-meters` | `${WEATHER_OPENMETEO_DENSE_FOG_VISIBILITY_METERS:200}` | Visibility (meters) at or below which fog (WMO code 45/48) is classified `DENSE_FOG` rather than `FOG` - 200m mirrors the India Meteorological Department's own "dense fog" definition; see docs/prediction-model.md's Phase 17 notes |

Selecting `weather.provider=openmeteo` requires **no API key at all** for ordinary use - unlike
`historical.provider=postgres` (which fails startup without the `postgres` Spring profile also
active), there is no equivalent hard dependency here: Open-Meteo's free tier just works. A network
failure, timeout, or malformed response from Open-Meteo is caught by `PredictionService` exactly
like every other optional-data failure (weather becomes unavailable for that one request; the
prediction still succeeds) - it is never treated as a reason to fail the whole application or to
silently substitute mock data.

## Real railway operational disruption data - provider foundation only (Phase 18)

| Property | Default | Meaning |
|---|---|---|
| `railway-disruption.provider` | `${RAILWAY_DISRUPTION_PROVIDER:unavailable}` | `unavailable` (default) → `UnavailableRailwayDisruptionProvider`; `mock` → `MockRailwayDisruptionProvider` |

Unlike every other provider-selection switch in this codebase (`weather.provider`,
`historical.provider`, `historical.section-provider` all default to `mock`, standing in for a real
integration that already exists or is planned), `railway-disruption.provider` defaults to
`unavailable` - because, after this phase's own source research, **no real provider was found to
exist for this application to eventually switch to** (see docs/architecture.md's Phase 18 notes for
the full source-by-source findings: NTES is a website not a documented public API; Indian
Railways' own TSR data flows through CRIS's partner-only API gateway, not a public self-service
one; data.gov.in hosts only static reference datasets; RailRadar's own documented fields don't
include structured disruption causes). Defaulting to `mock` here would misrepresent that finding as
"a mock stands in for a real thing we'll wire in later" when no such real thing is currently
available to this project.

`unavailable` always reports `RailwayDisruptionAvailability.UNAVAILABLE` - an explicit, honest
signal, never silently treated as "no disruptions exist" by any future consumer of this interface.

### Mock railway disruption data (opt-in only, for testing/demonstration)

| Property | Default | Meaning |
|---|---|---|
| `railway-disruption.mock.enabled` | `${RAILWAY_DISRUPTION_MOCK_ENABLED:false}` | Whether the one configured disruption below is "present" at all |
| `railway-disruption.mock.type` | `${RAILWAY_DISRUPTION_MOCK_TYPE:TEMPORARY_SPEED_RESTRICTION}` | A `RailwayDisruptionType` value |
| `railway-disruption.mock.train-number` | `${RAILWAY_DISRUPTION_MOCK_TRAIN_NUMBER:}` | Blank (default) = route-wide, matches any queried train; set to restrict to one train |
| `railway-disruption.mock.from-station-code`, `...to-station-code` | unset | The section this disruption applies to - required when `enabled=true` |
| `railway-disruption.mock.restricted-speed-kmh` | unset | Optional, relevant to `TEMPORARY_SPEED_RESTRICTION` |
| `railway-disruption.mock.severity` | unset | Optional free-form description |

As of Phase 19, `PredictionService` does query `RailwayDisruptionProvider` on every prediction (see
below) - with the default `unavailable` provider this is a no-op degrading to
`DisruptionImpactAssessment.unavailable()`, identical to every request before Phase 19.

## Disruption-to-delay policy (Phase 19) - every default below is a heuristic

| Property | Default | Unit | Empirical or heuristic? |
|---|---|---|---|
| `railway-disruption-impact.engineering-block-default-delay-minutes` | `${RAILWAY_DISRUPTION_IMPACT_ENGINEERING_BLOCK_DEFAULT_DELAY_MINUTES:15}` | minutes | **Heuristic** - no real source supplies a duration to compute from |
| `railway-disruption-impact.signal-failure-default-delay-minutes` | `${RAILWAY_DISRUPTION_IMPACT_SIGNAL_FAILURE_DEFAULT_DELAY_MINUTES:10}` | minutes | **Heuristic** |
| `railway-disruption-impact.congestion-default-delay-minutes` | `${RAILWAY_DISRUPTION_IMPACT_CONGESTION_DEFAULT_DELAY_MINUTES:10}` | minutes | **Heuristic** - `severity` is deliberately not used to scale this (see docs/prediction-model.md) |
| `railway-disruption-impact.max-single-disruption-delay-minutes` | `${RAILWAY_DISRUPTION_IMPACT_MAX_SINGLE_DELAY_MINUTES:60}` | minutes | **Heuristic safety bound** - applied even to the physically-computed `TEMPORARY_SPEED_RESTRICTION` figure, against a pathological input |
| `railway-disruption-impact.max-aggregate-delay-minutes` | `${RAILWAY_DISRUPTION_IMPACT_MAX_AGGREGATE_DELAY_MINUTES:90}` | minutes | **Heuristic safety bound** - the summed cap across every currently-active disruption on one section; validated to be `>=` the single-disruption bound |

`TEMPORARY_SPEED_RESTRICTION` is the one disruption type with **no** corresponding default property
- its delay is computed from basic kinematics using the disruption's own reported
`restrictedSpeedKmh`, the train's own current speed, and the section's own distance (still capped by
`max-single-disruption-delay-minutes` as a safety bound, but the underlying number itself is not a
heuristic - see docs/prediction-model.md's Phase 19 notes for the full accounting).

No configuration exists for `ROUTE_DIVERSION`/`MAINTENANCE_BLOCK`/`OTHER` - this policy has no
impact rule for them at all yet (always reported present-but-not-estimable), so a delay-minutes
property for them would be meaningless.

## PostgreSQL / historical observation persistence (Phase 16A/16B) - opt-in

Unlike everything above, the database is **not active by default**. `application.properties`
excludes Spring Boot's DataSource/Hibernate/Spring-Data-JPA/Flyway auto-configuration outright:

```properties
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,\
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,\
org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
```

This means: no DataSource bean, no Flyway migration attempt, no repository proxy - the
application and its full test suite work identically whether or not a database exists anywhere
reachable. This was a deliberate choice, not an oversight: adding real dependencies (JDBC driver,
Flyway) to the classpath must not turn "no database configured" into a startup failure.

### Turning it on

Set `SPRING_PROFILES_ACTIVE=postgres`, which loads `application-postgres.properties`:

| Property | Default | Meaning |
|---|---|---|
| `spring.datasource.url` | `${DB_URL:jdbc:postgresql://localhost:5432/railpredictor}` | JDBC URL |
| `spring.datasource.username` | `${DB_USERNAME:railpredictor}` | DB user |
| `spring.datasource.password` | `${DB_PASSWORD:}` | DB password - **never hardcode**, always via env var |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Flyway owns the schema; Hibernate only checks it matches, never generates/alters it |
| `spring.flyway.locations` | `classpath:db/migration` | Where `V1__create_historical_observations.sql`/`V2__create_historical_delay_profiles.sql` (and future migrations) live |

With this profile active, `HistoricalObservationRepository` and `HistoricalDelayProfileRepository`
become real beans, and `HistoricalObservationRecorder`/`HistoricalDelayProfileRefresher` (see
docs/historical-data-design.md) actually persist. Without it, `HistoricalObservationRecorder`
receives no repository (`Optional.empty()`) and silently does nothing - the same "optional data
source degrades gracefully" pattern used for weather/historical lookups (Phase 13), applied here
to writing instead of reading. `HistoricalDelayProfileRefresher` differs deliberately: since
refreshing a profile is always an explicit, deliberate call (never automatic), it throws
`IllegalStateException` rather than silently no-op-ing when the database isn't configured - a
caller asking for a refresh deserves to know it didn't happen.

**Enabling real evaluation-data collection end-to-end (Phase 22)** combines this profile with a few
other already-existing properties into one deliberate operational bundle - see
docs/historical-data-design.md's own Phase 22 section for the full property list, the exact
snapshot/outcome lifecycle, and current limitations.

### Choosing which `HistoricalDelayProvider` is active (Phase 16B)

| Property | Default | Meaning |
|---|---|---|
| `historical.provider` | `${HISTORICAL_PROVIDER:mock}` | `mock` (default) → `MockHistoricalDelayProvider`; `postgres` → `PostgresHistoricalDelayProvider` |

Exactly one `HistoricalDelayProvider` bean exists at a time, each `@ConditionalOnProperty`-gated
on this one property - never both, never neither (an unrecognised value selects neither, and
`PredictionService`'s injection would then fail to find a bean, which is the correct "fail loud"
outcome for a typo). Setting `historical.provider=postgres` **without** also activating the
`postgres` Spring profile fails application startup outright
(`PostgresHistoricalDelayProvider`'s `HistoricalDelayProfileRepository` dependency is required,
not optional) - there is no silent fallback from real historical data to mock data. Today's
default configuration leaves `historical.provider` unset, so `PredictionService` continues to see
only `MockHistoricalDelayProvider` - Phase 16B does not change production behaviour by itself.

### Observation data-quality gate (Phase 16C)

| Property | Default | Meaning |
|---|---|---|
| `historical.observation-validation.max-plausible-delay-minutes` | `${HISTORICAL_OBSERVATION_MAX_PLAUSIBLE_DELAY_MINUTES:4320}` | An arrival/departure delay beyond this many minutes (72 hours by default) is treated as corrupt/nonsensical and the observation is rejected, rather than persisted as a genuinely extreme delay - an assumed sanity bound, not derived from real RailRadar data |

Applied by `HistoricalObservationMapper` before an observation is even considered for persistence -
see docs/historical-data-design.md's Phase 16C validation-rules table for the full set of
accept/reject decisions (blank station code and impossible station sequence are also rejected, but
have no corresponding property since there's no sensible default other than "always reject").

### Journey-identity mitigation (Phase 16E)

| Property | Default | Meaning |
|---|---|---|
| `historical.journey-date.operating-day-start-hour` | `${HISTORICAL_JOURNEY_DATE_OPERATING_DAY_START_HOUR:0}` | Hour (0-23) before which an observation is attributed to the *previous* calendar date rather than the literal one. `0` = midnight = identical to naive `LocalDate.now()` behaviour (no change) |

RailRadar's live-status response carries no trustworthy journey-start-date field (verified by
inspecting every DTO - see docs/historical-data-design.md's Phase 16E notes), so
`HistoricalObservation.journeyDate` is necessarily an *observation-date*, not a verified
physical-journey identifier. Raising this property above `0` only helps if you know a specific
overnight service's schedule and want to reduce (not eliminate) the risk of one physical journey
being split across two `journeyDate` values purely because polling happened to straddle local
midnight. It uses only the application's own clock - never RailRadar data - so it invents nothing
about the journey itself.

### Scheduled profile refresh (Phase 16C)

| Property | Default | Meaning |
|---|---|---|
| `historical.profile-refresh.interval-ms` | `${HISTORICAL_PROFILE_REFRESH_INTERVAL_MS:3600000}` | How often (milliseconds) `HistoricalDelayProfileRefreshScheduler` re-derives every known `HistoricalDelayProfile` from `historical_observations` |
| `historical.profile-refresh.initial-delay-ms` | `${HISTORICAL_PROFILE_REFRESH_INITIAL_DELAY_MS:60000}` | Delay before the first run after application startup |

This is the only thing that calls `HistoricalDelayProfileRefresher` - profiles are never refreshed
as a side effect of a prediction request. It runs independently of the `historical.provider`
selection below (it maintains data for `PostgresHistoricalDelayProvider` to eventually consume, but
runs regardless of which provider is currently active) and is a silent no-op when no observation
database is configured (the default) - see docs/historical-data-design.md for the full design.
Since Phase 16F, the same scheduled run also refreshes `historical_section_delay_profiles` for
every distinct train number (`HistoricalSectionDelayProfileRefresher`) - no separate schedule or
property, it reuses this same interval.

### Section-level historical provider (Phase 16G)

| Property | Default | Meaning |
|---|---|---|
| `historical.section-provider` | `${HISTORICAL_SECTION_PROVIDER:mock}` | `mock` (default) → `MockSectionHistoricalDelayProvider`; `postgres` → `PostgresSectionHistoricalDelayProvider` |
| `historical.section.minimum-sample-count` | `${HISTORICAL_SECTION_MINIMUM_SAMPLE_COUNT:10}` | Sample count below which a section profile is reported `INSUFFICIENT_SAMPLES` rather than `AVAILABLE` - a separate threshold from `prediction.historical-adjustment.minimum-sample-count`, deliberately not reused (see docs/historical-data-design.md for the variance reasoning behind the default) |
| `historical.section.mock.average-delay-change-minutes` | `${HISTORICAL_SECTION_MOCK_AVERAGE_DELAY_CHANGE_MINUTES:0}` | Fixed mock average delay-change (may be negative - a delay change can be a recovery) |
| `historical.section.mock.median-delay-change-minutes` | `${HISTORICAL_SECTION_MOCK_MEDIAN_DELAY_CHANGE_MINUTES:0}` | Fixed mock median delay-change |
| `historical.section.mock.standard-deviation-minutes` | `${HISTORICAL_SECTION_MOCK_STANDARD_DEVIATION_MINUTES:0}` | Fixed mock standard deviation |
| `historical.section.mock.sample-count` | `${HISTORICAL_SECTION_MOCK_SAMPLE_COUNT:0}` | Fixed mock sample count (`0` = "no historical data" by default) |

`historical.section-provider` is a **separate** selection switch from `historical.provider` (the
station-level provider's own) - the two may be configured independently, since nothing in the
application consumes `HistoricalSectionDelayProvider` yet (it exists only as a clean, tested
abstraction - see docs/historical-data-design.md's Phase 16G notes). The same "no silent fallback"
rule applies: `postgres` without the `postgres` Spring profile active fails startup outright.

`PostgresSectionHistoricalDelayProvider` answers a "now or later" `referenceInstant` from the
materialized `historical_section_delay_profiles` cache, but a genuinely past `referenceInstant` by
re-loading raw observations and re-aggregating on demand - the cache alone cannot safely answer a
retrospective query (no version history is retained). See docs/historical-data-design.md for the
full reasoning; this is a read-path distinction, not a configuration option.

### Testing without a real Postgres/Docker

Repository tests (`HistoricalObservationRepositoryTest`, `HistoricalDelayProfileRepositoryTest`)
run against an embedded H2 database via `@DataJpaTest`, with Flyway disabled and Hibernate's own
`create-drop` schema generation used instead, purely for that test slice:

```java
@DataJpaTest
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
```

This is a deliberate tradeoff, not Testcontainers-against-real-Postgres, because Docker isn't
available in this development environment - see docs/historical-data-design.md for what this
does and doesn't verify.
