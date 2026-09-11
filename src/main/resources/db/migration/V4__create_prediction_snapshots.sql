-- Phase 16H-5: durable record of one prediction, captured at the moment it was made, so a later
-- process can check it against a real, subsequently-observed outcome. PredictionResult itself is
-- never persisted (it is a per-request response value) - without this table, "did the system
-- predict accurately" could never be answered after the fact.
--
-- Evaluation target is deliberately the train's NEXT station (not the final destination) - see
-- PredictionSnapshot's own Javadoc for why: RailRadar exposes no reliable whole-journey-completion
-- signal, so only a next-station match can be trusted.
--
-- One row per prediction made (never updated except by the evaluation process filling in the
-- actual_* / error_minutes / evaluated_at columns once a matching observation appears - a snapshot
-- is otherwise immutable, unlike the upserted historical_delay_profiles/historical_observations
-- tables).
CREATE TABLE prediction_snapshots (
    id                                    BIGSERIAL PRIMARY KEY,
    train_number                         VARCHAR(10) NOT NULL,
    prediction_made_at                   TIMESTAMPTZ NOT NULL,
    target_station_code                  VARCHAR(10) NOT NULL,
    current_delay_minutes                INTEGER NOT NULL,
    predicted_next_station_delay_minutes INTEGER NOT NULL,
    predicted_total_delay_minutes        INTEGER NOT NULL,
    predicted_eta                        TIMESTAMPTZ NOT NULL,
    historical_adjustment_minutes        INTEGER NOT NULL,
    historical_adjustment_source         VARCHAR(20) NOT NULL,
    historical_adjustment_provenance     VARCHAR(200) NOT NULL,
    confidence_score                     DOUBLE PRECISION NOT NULL,
    evaluation_status                    VARCHAR(30) NOT NULL,
    actual_delay_minutes                 INTEGER,
    error_minutes                        INTEGER,
    evaluated_at                         TIMESTAMPTZ
);

-- The evaluation refresh job's own discovery query: every PENDING snapshot. A plain (not partial)
-- index for portability across the real PostgreSQL target and the H2 engine used in repository
-- tests (see docs/configuration.md's testing-without-Postgres notes).
CREATE INDEX idx_prediction_snapshots_status
    ON prediction_snapshots (evaluation_status);

-- The evaluation matcher's own lookup: candidate observations for one (train, station) pair.
CREATE INDEX idx_prediction_snapshots_train_station
    ON prediction_snapshots (train_number, target_station_code);
