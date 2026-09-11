-- Phase 16H-7: records which process produced each prediction_snapshots row - LIVE_EVALUATION
-- (a prediction made now by the live request path, checked later against a real outcome) versus
-- HISTORICAL_BACKTEST (a prediction reconstructed as of a past timestamp using only inputs that
-- would genuinely have been available then). No code in this application produces the latter yet
-- (see docs/historical-data-design.md's Phase 16H-7 notes on why true historical replay is not
-- yet possible) - this column exists so the audit record is self-describing now, and so a future
-- phase that does implement true historical replay cannot have its output silently mixed with
-- live-evaluation evidence in the accuracy report.
--
-- DEFAULT 'LIVE_EVALUATION' is correct for every row that exists before this migration runs (and
-- for the foreseeable future, since HISTORICAL_BACKTEST is not yet producible) - not a
-- placeholder guess.
ALTER TABLE prediction_snapshots
    ADD COLUMN evaluation_mode VARCHAR(20) NOT NULL DEFAULT 'LIVE_EVALUATION';
