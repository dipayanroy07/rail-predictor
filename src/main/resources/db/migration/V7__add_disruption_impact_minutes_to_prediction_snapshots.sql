-- Phase 19: records the real-disruption delay contribution actually added to
-- predicted_total_delay_minutes at prediction time - NULL when real disruption data was
-- unavailable or present-but-not-estimable (never a fabricated 0 standing in for "don't know"),
-- otherwise the (possibly legitimately 0) minutes added. This is the calibration-foundation field:
-- it lets a future evaluation phase compare accuracy for predictions made with a nonzero real-
-- disruption contribution against those without one - see docs/prediction-model.md's Phase 19
-- notes. No such comparison is built yet; this column only makes it possible without a backfill.
ALTER TABLE prediction_snapshots
    ADD COLUMN disruption_impact_minutes INTEGER;
