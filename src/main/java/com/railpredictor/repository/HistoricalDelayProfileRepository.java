package com.railpredictor.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link HistoricalDelayProfileEntity}. Only ever wired up when the "postgres"
 * profile is active - see application.properties and docs/configuration.md.
 */
public interface HistoricalDelayProfileRepository extends JpaRepository<HistoricalDelayProfileEntity, Long> {

    /** The natural key lookup used both for idempotent re-aggregation (upsert) and for
     * {@code PostgresHistoricalDelayProvider}'s read path. */
    Optional<HistoricalDelayProfileEntity> findByTrainNumberAndStationCode(String trainNumber, String stationCode);
}
