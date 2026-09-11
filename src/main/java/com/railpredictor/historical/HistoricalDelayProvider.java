package com.railpredictor.historical;

import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.RouteSection;

/**
 * The only way the rest of the application accesses historical delay statistics. Callers depend
 * on this interface, never on how the data is stored, so the mock implementation can be replaced
 * by a PostgreSQL-backed one (Phase 16) without touching callers.
 */
public interface HistoricalDelayProvider {

    /**
     * @return statistics for this train on this section, for the current day/month/time-period
     *         bucket; {@code sampleCount() == 0} means no historical data was available
     */
    HistoricalDelay getHistoricalDelay(String trainNumber, RouteSection section);
}
