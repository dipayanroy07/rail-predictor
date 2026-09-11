-- Phase 17: records WeatherData.source() at the moment each prediction was made (e.g.
-- "mock-provider", "open-meteo"), or NULL when weather was unavailable for that request. Needed
-- for auditability: without this, there would be no way to later determine whether a given past
-- prediction was made with mock weather, real weather, or none at all - relevant once real weather
-- accuracy needs evaluating. Nullable (unlike historical_adjustment_provenance, which always has a
-- NONE/UNAVAILABLE fallback value) because weather genuinely has no fallback constant - it is
-- either available or it isn't, and NULL is the honest way to represent "wasn't available", never
-- a fabricated placeholder string.
ALTER TABLE prediction_snapshots
    ADD COLUMN weather_provenance VARCHAR(200);
