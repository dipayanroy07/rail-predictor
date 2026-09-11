-- Phase 21: records the SEPARATE, next-station-scoped historical adjustment that now genuinely
-- contributes to predicted_next_station_delay_minutes - never to be confused with the existing
-- historical_adjustment_minutes/historical_adjustment_source/historical_adjustment_provenance
-- columns, which remain destination-scoped (they feed predicted_total_delay_minutes/predicted_eta
-- only, unchanged by this phase). See docs/prediction-model.md's Phase 21 notes for the exact
-- reason these had to be new, separate columns rather than reusing the existing ones: the
-- existing "SECTION" historical value sums delay-change across the ENTIRE remaining route, which
-- is correct for a destination-scoped total but wrong for a next-station value.
--
-- DEFAULT 0 / 'NONE' / 'unavailable' is the honest, correct value for every row that exists before
-- this migration runs - predicted_next_station_delay_minutes never included ANY historical
-- contribution before this phase, so "no historical signal was used" accurately describes every
-- pre-Phase-21 row, not a guess standing in for missing data.
ALTER TABLE prediction_snapshots
    ADD COLUMN next_station_historical_adjustment_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_station_historical_adjustment_source VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN next_station_historical_adjustment_provenance VARCHAR(200) NOT NULL DEFAULT 'unavailable';

-- Also Phase 21: simulation's own raw contribution, persisted directly (rather than left to be
-- reconstructed from other columns, which would be ambiguous whenever predicted_next_station_
-- delay_minutes was clamped at 0) so a future ablation can isolate "with vs. without simulation"
-- exactly. For every row that exists before this migration, predicted_next_station_delay_minutes
-- was exactly current_delay_minutes + predicted_extra_delay_minutes (the pre-Phase-21 formula, see
-- docs/prediction-model.md), so the backfill expression below is exact, not an approximation.
ALTER TABLE prediction_snapshots
    ADD COLUMN predicted_extra_delay_minutes INTEGER;
UPDATE prediction_snapshots
    SET predicted_extra_delay_minutes = predicted_next_station_delay_minutes - current_delay_minutes
    WHERE predicted_extra_delay_minutes IS NULL;
ALTER TABLE prediction_snapshots
    ALTER COLUMN predicted_extra_delay_minutes SET NOT NULL;
