-- Phase 22E: one-time, evidence-based correction for the single known-contaminated
-- prediction_snapshots row, identified by direct psql inspection (never a heuristic or broad
-- WHERE clause) - see docs/historical-data-design.md's Phase 22E notes.
--
-- Evidence (from psql, 2026-09-11):
--   prediction_snapshots.id = 1 (train 22415, target station NDLS) was evaluated EVALUATED_EXACT
--   at 2026-09-11 11:15:56+05:30, against a historical_observations row for the same
--   (train, station) that carries the Phase 22D "upcoming"-stop placeholder signature:
--   actual_arrival = 2026-09-11T14:05:00+05:30 (a *scheduled* time, ~3 hours after it was
--   recorded) with arrival_delay_minutes = 0, at station_sequence 115 - a stop the train plainly
--   had not yet reached. Collected before the Phase 22D HistoricalObservationMapper fix was live.
--
--   prediction_snapshots.id = 2 (same train/station) remains PENDING - never evaluated, so it
--   contributes no accuracy evidence either way and needs no quarantine.
--
-- Only quarantined/quarantine_reason are set below - actual_delay_minutes/error_minutes/
-- evaluated_at and every predicted field on this row are left exactly as originally computed, so
-- the row remains fully auditable.
UPDATE prediction_snapshots
SET quarantined = TRUE,
    quarantine_reason = 'Phase 22C/22D: evaluated against a RailRadar "upcoming"-stop placeholder '
        || '(scheduled-time actual_arrival, arrival_delay_minutes=0) recorded before the '
        || 'HistoricalObservationMapper fix was live. Identified by direct psql inspection.'
WHERE id = 1;
