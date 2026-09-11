-- Phase 16A: raw historical station/journey observations extracted from RailRadar's own
-- RouteStop data (already fetched for every live-status call, never a second API call).
--
-- One row = one canonical, completed (train_number, journey_date, station_code, station_sequence)
-- event. Repeated polling upserts this same row (see HistoricalObservationRecorder) rather than
-- inserting duplicates - the unique constraint below is the durability backstop for that
-- application-level idempotency, not the primary mechanism.
--
-- Nullable columns: scheduled_arrival/actual_arrival/scheduled_departure/actual_departure and
-- the two delay columns can legitimately be unavailable for a given station (e.g. an origin
-- station has no scheduled/actual arrival; RailRadar itself may not report every field).
CREATE TABLE historical_observations (
    id                       BIGSERIAL PRIMARY KEY,
    train_number             VARCHAR(10) NOT NULL,
    journey_date             DATE        NOT NULL,
    station_code             VARCHAR(10) NOT NULL,
    station_sequence         INTEGER,
    scheduled_arrival        VARCHAR(50),
    actual_arrival           VARCHAR(50),
    scheduled_departure      VARCHAR(50),
    actual_departure         VARCHAR(50),
    arrival_delay_minutes    INTEGER,
    departure_delay_minutes INTEGER,
    observed_at              TIMESTAMPTZ NOT NULL,
    source                   VARCHAR(50) NOT NULL,

    CONSTRAINT uq_historical_observation
        UNIQUE (train_number, journey_date, station_code, station_sequence)
);

CREATE INDEX idx_historical_observations_lookup
    ON historical_observations (train_number, journey_date, station_code);
