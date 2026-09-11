package com.railpredictor.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link HistoricalSectionDelayProfileEntity}. Only ever wired up when the
 * "postgres" profile is active - see application.properties and docs/configuration.md.
 */
public interface HistoricalSectionDelayProfileRepository extends JpaRepository<HistoricalSectionDelayProfileEntity, Long> {

    /** The natural key lookup used for idempotent re-aggregation (upsert). */
    Optional<HistoricalSectionDelayProfileEntity> findByTrainNumberAndFromStationCodeAndToStationCode(
            String trainNumber, String fromStationCode, String toStationCode);
}
