-- Phase 16F: aggregated (derived) delay-CHANGE statistics per (train_number, from_station_code,
-- to_station_code), computed from historical_observations by
-- HistoricalSectionDelayProfileAggregator.
--
-- Like historical_delay_profiles, this table is a CACHE of a computation, never a second source
-- of truth: it must always be reproducible by re-aggregating historical_observations (which
-- remains the sole authoritative historical dataset, never deleted or modified by this process),
-- and it never stores raw section samples as a second copy of any observation. One row per
-- (train_number, from_station_code, to_station_code) - re-aggregating replaces the existing row
-- in place, it does not version or append.
--
-- "Section" here means two consecutively-persisted observations, not a guaranteed single
-- physical track segment - see HistoricalSectionDelayProfile's own Javadoc.
CREATE TABLE historical_section_delay_profiles (
    id                              BIGSERIAL PRIMARY KEY,
    train_number                    VARCHAR(10) NOT NULL,
    from_station_code               VARCHAR(10) NOT NULL,
    to_station_code                 VARCHAR(10) NOT NULL,
    sample_count                    INTEGER NOT NULL,
    average_delay_change_minutes    DOUBLE PRECISION NOT NULL,
    median_delay_change_minutes     DOUBLE PRECISION NOT NULL,
    standard_deviation_minutes      DOUBLE PRECISION NOT NULL,
    source                          VARCHAR(200) NOT NULL,
    computed_at                     TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_historical_section_delay_profile
        UNIQUE (train_number, from_station_code, to_station_code)
);
