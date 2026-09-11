package com.railpredictor.historical;

import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import java.time.Instant;

/**
 * The only way the rest of the application accesses section-level (delay-<b>change</b>) historical
 * statistics - a deliberately separate capability from {@link HistoricalDelayProvider} (Phase
 * 16G design decision, see docs/historical-data-design.md's Phase 16G notes for the full
 * reasoning): {@link HistoricalDelayProvider} answers "how delayed has this train historically
 * been by the time it reaches this station" (an arrival-delay average, always non-negative by its
 * own domain contract); this interface answers "how much extra delay (or recovery) does this
 * specific section tend to add" (a delay-<em>change</em> average, which must be allowed negative).
 * Forcing both semantics into one interface/return type would make every existing consumer of
 * {@link HistoricalDelayProvider} (today, only {@code HistoricalDelayCalculator}) reason about
 * section-profile semantics it doesn't need, and would require loosening
 * {@link com.railpredictor.model.domain.HistoricalDelay}'s existing non-negative invariant just
 * for this one new caller - so a separate interface it is.
 *
 * <p>{@code referenceInstant} is a required parameter, not an optional overload: every caller must
 * be explicit about the point in time this lookup means. See each implementation's own Javadoc for
 * exactly how (and whether) it can honour a cutoff that is genuinely in the past - the materialized
 * profile cache and true point-in-time correctness are not automatically the same thing (Phase
 * 16G's central design question; see docs/historical-data-design.md).
 *
 * <p>Nothing in the application calls this interface yet - Phase 16G only establishes it and its
 * PostgreSQL/mock implementations; {@code PredictionEngine}, {@code PredictionService}, and
 * {@code HistoricalDelayCalculator} remain completely unaware of it, deliberately, until a future
 * phase designs how prediction should actually consume it.
 */
public interface HistoricalSectionDelayProvider {

    /**
     * @param section only {@code fromStation().code()}/{@code toStation().code()} are used - the
     *                 section-level profile is keyed on plain station codes, not full
     *                 {@link com.railpredictor.model.domain.Station} values
     * @param referenceInstant the point in time this lookup is "as of" - see this interface's own
     *                 Javadoc and each implementation for what honouring this actually means
     */
    SectionHistoricalDelayResult getSectionDelay(String trainNumber, RouteSection section, Instant referenceInstant);
}
