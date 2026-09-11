package com.railpredictor.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.domain.DataProvenance;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * Exercises {@link HistoricalObservationRepository} against a real (embedded) relational
 * database - H2, not PostgreSQL/Testcontainers. Docker isn't available in this environment (no
 * `docker` binary, nothing listening on 5432), so Testcontainers-Postgres can't run here; see
 * docs/historical-data-design.md for the tradeoff this implies (the hand-written Flyway
 * migration's exact SQL is therefore not exercised by this test - only the JPA entity mapping
 * and repository query semantics are, via Hibernate's own schema generation for this test only).
 *
 * <p>Re-enables the JPA/DataSource auto-configuration excluded by default in
 * application.properties, and swaps Flyway for Hibernate's {@code create-drop} against H2 -
 * production still uses Flyway-managed PostgreSQL exclusively (see
 * application-postgres.properties).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HistoricalObservationRepositoryTest {

    @Autowired
    private HistoricalObservationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static HistoricalObservationEntity entity(String trainNumber, LocalDate journeyDate, String stationCode, Integer sequence) {
        return new HistoricalObservationEntity(
                trainNumber, journeyDate, stationCode, sequence,
                "20:39", "20:51", "20:41", "20:53", 12, 12,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);
    }

    @Test
    void persistsANewObservationSuccessfully() {
        HistoricalObservationEntity saved = repository.save(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        Optional<HistoricalObservationEntity> reloaded = repository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStationCode()).isEqualTo("KOTA");
        assertThat(reloaded.get().getArrivalDelayMinutes()).isEqualTo(12);
    }

    @Test
    void findByNaturalKeyLocatesAnExistingRowForIdempotentUpdate() {
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        entityManager.flush();
        entityManager.clear();

        Optional<HistoricalObservationEntity> found = repository
                .findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                        "12952", LocalDate.of(2026, 9, 9), "KOTA", 5);

        assertThat(found).isPresent();
    }

    @Test
    void duplicateNaturalKeyInsertViolatesTheUniqueConstraint() {
        repository.saveAndFlush(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        HistoricalObservationEntity duplicate = entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }

    @Test
    void updatingAnExistingRowNeverCreatesASecondRowForTheSameKey() {
        HistoricalObservationEntity saved = repository.saveAndFlush(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        entityManager.clear();

        HistoricalObservationEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.updateFrom(new HistoricalObservationEntity(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5,
                "20:39", "20:52", "20:41", "20:54", 13, 13,
                Instant.parse("2026-09-09T15:05:00Z"), DataProvenance.RAILRADAR));
        repository.saveAndFlush(reloaded);
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(saved.getId()).orElseThrow().getArrivalDelayMinutes()).isEqualTo(13);
    }

    @Test
    void storesAndRetrievesMultipleStationsForTheSameJourneyInSequenceOrder() {
        LocalDate journeyDate = LocalDate.of(2026, 9, 9);
        repository.save(entity("12952", journeyDate, "KOTA", 5));
        repository.save(entity("12952", journeyDate, "NDLS", 1));
        entityManager.flush();
        entityManager.clear();

        List<HistoricalObservationEntity> stations =
                repository.findByTrainNumberAndJourneyDateOrderByStationSequenceAsc("12952", journeyDate);

        assertThat(stations).extracting(HistoricalObservationEntity::getStationCode).containsExactly("NDLS", "KOTA");
    }

    @Test
    void sameTrainOnDifferentJourneyDatesAreDistinctRows() {
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 10), "KOTA", 5));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5)).isPresent();
        assertThat(repository.findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                "12952", LocalDate.of(2026, 9, 10), "KOTA", 5)).isPresent();
    }

    @Test
    void allowsNullableScheduledActualAndDelayValues() {
        HistoricalObservationEntity entity = new HistoricalObservationEntity(
                "12952", LocalDate.of(2026, 9, 9), "NDLS", 1,
                null, null, null, null, null, null,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);

        HistoricalObservationEntity saved = repository.saveAndFlush(entity);
        entityManager.clear();
        HistoricalObservationEntity reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getScheduledArrival()).isNull();
        assertThat(reloaded.getActualArrival()).isNull();
        assertThat(reloaded.getScheduledDeparture()).isNull();
        assertThat(reloaded.getActualDeparture()).isNull();
        assertThat(reloaded.getArrivalDelayMinutes()).isNull();
        assertThat(reloaded.getDepartureDelayMinutes()).isNull();
    }

    @Test
    void persistsAndRetrievesSourceProvenance() {
        repository.saveAndFlush(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        entityManager.clear();

        assertThat(repository.findAll().get(0).getSource()).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void findByTrainNumberAndStationCodeReturnsEveryJourneyDateForThatStation() {
        // The raw input HistoricalDelayProfileAggregator (Phase 16B) reads via this finder.
        repository.save(entity("12952", LocalDate.of(2026, 9, 8), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "NDLS", 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByTrainNumberAndStationCode("12952", "KOTA")).hasSize(2);
        assertThat(repository.findByTrainNumberAndStationCode("12952", "NDLS")).hasSize(1);
        assertThat(repository.findByTrainNumberAndStationCode("99999", "KOTA")).isEmpty();
    }

    @Test
    void findDistinctTrainStationKeysCollapsesRepeatedObservationsForTheSamePair() {
        // HistoricalDelayProfileRefreshScheduler's discovery query - one key per (train, station)
        // regardless of how many journey dates/sequences contributed observations.
        repository.save(entity("12952", LocalDate.of(2026, 9, 8), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "NDLS", 1));
        entityManager.flush();
        entityManager.clear();

        List<TrainStationKey> keys = repository.findDistinctTrainStationKeys();

        assertThat(keys).containsExactlyInAnyOrder(
                new TrainStationKey("12952", "KOTA"), new TrainStationKey("12952", "NDLS"));
    }

    @Test
    void findDistinctTrainStationKeysIsEmptyWhenNoObservationsExist() {
        assertThat(repository.findDistinctTrainStationKeys()).isEmpty();
    }

    // --- Phase 16E: origin observations, null station_sequence, natural-key review ---

    private static HistoricalObservationEntity originEntity(
            String trainNumber, LocalDate journeyDate, String stationCode, Integer sequence) {
        return new HistoricalObservationEntity(
                trainNumber, journeyDate, stationCode, sequence,
                null, null, "08:00", "08:05", null, 5,
                Instant.parse("2026-09-09T02:35:00Z"), DataProvenance.RAILRADAR);
    }

    @Test
    void persistsAnOriginObservationWithNoArrivalFields() {
        HistoricalObservationEntity saved = repository.saveAndFlush(originEntity("12952", LocalDate.of(2026, 9, 9), "NDLS", 1));
        entityManager.clear();

        HistoricalObservationEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getActualArrival()).isNull();
        assertThat(reloaded.getScheduledArrival()).isNull();
        assertThat(reloaded.getArrivalDelayMinutes()).isNull();
        assertThat(reloaded.getActualDeparture()).isEqualTo("08:05");
        assertThat(reloaded.getDepartureDelayMinutes()).isEqualTo(5);
    }

    @Test
    void findByNaturalKeyLocatesAnExistingRowWhenStationSequenceIsNull() {
        // Proves, against a real relational engine, that Spring Data JPA's derived query
        // translates a null stationSequence argument into "station_sequence IS NULL" rather than
        // failing to match - this is what makes repeated polling of a null-sequence station (e.g.
        // an origin RailRadar never reports a sequence for) idempotent at the application level.
        repository.saveAndFlush(originEntity("12952", LocalDate.of(2026, 9, 9), "NDLS", null));
        entityManager.clear();

        Optional<HistoricalObservationEntity> found = repository
                .findByTrainNumberAndJourneyDateAndStationCodeAndStationSequence(
                        "12952", LocalDate.of(2026, 9, 9), "NDLS", null);

        assertThat(found).isPresent();
    }

    @Test
    void repeatedStationCodeWithDistinctSequencesPersistsAsTwoRows() {
        // A legitimate revisit of the same station code within one journey (e.g. a loop route) -
        // the natural key's inclusion of station_sequence keeps these distinct.
        LocalDate journeyDate = LocalDate.of(2026, 9, 9);
        repository.saveAndFlush(entity("12952", journeyDate, "KOTA", 5));
        repository.saveAndFlush(entity("12952", journeyDate, "KOTA", 12));
        entityManager.clear();

        assertThat(repository.findByTrainNumberAndStationCode("12952", "KOTA")).hasSize(2);
    }

    @Test
    void repeatedStationCodeWithoutSequenceIsNotProtectedByADatabaseUniqueConstraint() {
        // Documented, accepted residual limitation (see docs/historical-data-design.md's Phase
        // 16E "natural key" section): SQL NULL is never equal to NULL in a unique constraint, so
        // two null-sequence rows for the same (train, date, station) are NOT rejected at the DB
        // level the way two identical non-null-sequence rows would be (see
        // duplicateNaturalKeyInsertViolatesTheUniqueConstraint above). The application-level
        // idempotent upsert (HistoricalObservationRecorder) avoids this in the common,
        // non-concurrent case by finding the existing null-sequence row first (see
        // findByNaturalKeyLocatesAnExistingRowWhenStationSequenceIsNull) - only a genuine
        // concurrent race would still produce a duplicate row here.
        LocalDate journeyDate = LocalDate.of(2026, 9, 9);
        repository.saveAndFlush(originEntity("12952", journeyDate, "NDLS", null));

        assertThatCode(() -> repository.saveAndFlush(originEntity("12952", journeyDate, "NDLS", null)))
                .doesNotThrowAnyException();
    }

    // --- Phase 16F: section-profile discovery queries ---

    @Test
    void findByTrainNumberReturnsEveryObservationAcrossAllStationsAndJourneyDates() {
        repository.save(entity("12952", LocalDate.of(2026, 9, 8), "NDLS", 1));
        repository.save(entity("12952", LocalDate.of(2026, 9, 8), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "NDLS", 1));
        repository.save(entity("99999", LocalDate.of(2026, 9, 9), "NDLS", 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByTrainNumber("12952")).hasSize(3);
        assertThat(repository.findByTrainNumber("99999")).hasSize(1);
        assertThat(repository.findByTrainNumber("00000")).isEmpty();
    }

    @Test
    void findDistinctTrainNumbersCollapsesRepeatedObservationsForTheSameTrain() {
        repository.save(entity("12952", LocalDate.of(2026, 9, 8), "KOTA", 5));
        repository.save(entity("12952", LocalDate.of(2026, 9, 9), "NDLS", 1));
        repository.save(entity("99999", LocalDate.of(2026, 9, 9), "NDLS", 1));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findDistinctTrainNumbers()).containsExactlyInAnyOrder("12952", "99999");
    }

    @Test
    void findDistinctTrainNumbersIsEmptyWhenNoObservationsExist() {
        assertThat(repository.findDistinctTrainNumbers()).isEmpty();
    }
}
