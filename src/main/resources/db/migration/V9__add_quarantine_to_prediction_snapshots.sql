-- Phase 22E: marks a prediction_snapshots row's evaluation outcome as known-untrustworthy
-- evidence, without deleting or altering anything else - actual_delay_minutes/error_minutes/
-- evaluated_at and every predicted column remain exactly what was originally computed/observed.
-- Motivated by the Phase 22D finding: RailRadar returns a scheduled-time placeholder in
-- actualArrival/actualDeparture for stops it has not yet reached (status "upcoming"), which
-- HistoricalObservationMapper accepted as a genuine event before that phase's fix. Two snapshots
-- collected before the fix (Phase 22C) are known to have been evaluated against such a
-- placeholder - see V10 for the one-time, evidence-based correction applied to those specific
-- rows (never a blanket rule, never a guess).
--
-- DEFAULT FALSE / NULL is correct for every row that exists before this migration runs - no
-- snapshot was ever quarantined before this phase existed.
ALTER TABLE prediction_snapshots
    ADD COLUMN quarantined BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN quarantine_reason VARCHAR(500);
