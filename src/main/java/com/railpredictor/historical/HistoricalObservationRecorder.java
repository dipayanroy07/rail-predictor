package com.railpredictor.historical;

import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.repository.HistoricalObservationEntity;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists {@link HistoricalObservation}s idempotently: one canonical row per
 * (trainNumber, journeyDate, stationCode, stationSequence), found-then-updated on each
 * repeated observation rather than inserted as a duplicate - see docs/historical-data-design.md.
 *
 * <p>The repository is optional by design: when the "postgres" profile isn't active, no
 * {@link HistoricalObservationRepository} bean exists, and this component simply does nothing -
 * exactly the same "optional data source degrades gracefully" pattern already used for weather
 * and historical-delay lookups (Phase 13), applied here to writing rather than reading.
 */
@Component
public class HistoricalObservationRecorder {

    private static final Logger log = LoggerFactory.getLogger(HistoricalObservationRecorder.class);

    private final Optional<HistoricalObservationRepository> repository;
    private final HistoricalObservationEntityMapper entityMapper;
    private final HistoricalDataMetrics metrics;

    public HistoricalObservationRecorder(
            Optional<HistoricalObservationRepository> repository,
            HistoricalObservationEntityMapper entityMapper,
            HistoricalDataMetrics metrics) {
        this.repository = repository;
        this.entityMapper = entityMapper;
        this.metrics = metrics;
    }

    @Transactional
    public void recordAll(List<HistoricalObservation> observations) {
        if (repository.isEmpty()) {
            log.debug("No historical observation database configured - {} observation(s) not persisted",
                    observations.size());
            return;
        }
        for (HistoricalObservation observation : observations) {
            recordOne(repository.get(), observation);
        }
    }

    private void recordOne(HistoricalObservationRepository repo, HistoricalObservation observation) {
        try {
            HistoricalObservationEntity latest = entityMapper.toEntity(observation);
            Optional<HistoricalObservationEntity> existing =
                    repo.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                            observation.trainNumber(), observation.journeyDate(), observation.stationCode(),
                            observation.stationSequence());
            if (existing.isEmpty()) {
                repo.save(latest);
                metrics.observationInserted();
            } else {
                existing.get().updateFrom(latest);
                repo.save(existing.get());
                metrics.observationUpdated();
            }
        } catch (DataIntegrityViolationException e) {
            // A concurrent request recorded the same canonical row first - the row exists
            // either way, so this is not a failure worth propagating.
            metrics.persistenceFailure();
            log.debug("Concurrent duplicate historical observation ignored for train {} station {}: {}",
                    observation.trainNumber(), observation.stationCode(), e.getMessage());
        }
    }
}
