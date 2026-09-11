-- Phase 16B: aggregated (derived) arrival-delay statistics per (train_number, station_code),
-- computed from historical_observations by HistoricalDelayProfileAggregator.
--
-- This table is a CACHE of a computation, not a second source of truth: it must always be
-- reproducible by re-aggregating historical_observations, which remains the authoritative
-- historical dataset and is never deleted or modified by this process. One row per
-- (train_number, station_code) - re-aggregating replaces the existing row in place (see
-- HistoricalDelayProfileRefresher), it does not version or append.
CREATE TABLE historical_delay_profiles (
    id                              BIGSERIAL PRIMARY KEY,
    train_number                    VARCHAR(10) NOT NULL,
    station_code                    VARCHAR(10) NOT NULL,
    sample_count                    INTEGER NOT NULL,
    average_arrival_delay_minutes   DOUBLE PRECISION NOT NULL,
    median_arrival_delay_minutes    DOUBLE PRECISION NOT NULL,
    standard_deviation_minutes      DOUBLE PRECISION NOT NULL,
    source                          VARCHAR(200) NOT NULL,
    computed_at                     TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_historical_delay_profile UNIQUE (train_number, station_code)
);
