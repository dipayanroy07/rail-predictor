package com.railpredictor.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.model.domain.DataProvenance;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/**
 * Exercises {@link HistoricalDelayProfileRepository} against embedded H2 - see
 * {@code HistoricalObservationRepositoryTest} for why (no Docker/Testcontainers-PostgreSQL
 * available in this environment) and what that does/doesn't verify.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HistoricalDelayProfileRepositoryTest {

    @Autowired
    private HistoricalDelayProfileRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static HistoricalDelayProfileEntity entity(String trainNumber, String stationCode) {
        return new HistoricalDelayProfileEntity(
                trainNumber, stationCode, 10, 8.5, 6.0, 3.25, DataProvenance.RAILRADAR,
                Instant.parse("2026-09-10T09:00:00Z"));
    }

    @Test
    void persistsANewProfileSuccessfully() {
        HistoricalDelayProfileEntity saved = repository.save(entity("12952", "KOTA"));
        entityManager.flush();
        entityManager.clear();

        Optional<HistoricalDelayProfileEntity> reloaded = repository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getSampleCount()).isEqualTo(10);
        assertThat(reloaded.get().getAverageArrivalDelayMinutes()).isEqualTo(8.5);
    }

    @Test
    void findByNaturalKeyLocatesAnExistingProfileForIdempotentReAggregation() {
        repository.save(entity("12952", "KOTA"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByTrainNumberAndStationCode("12952", "KOTA")).isPresent();
    }

    @Test
    void duplicateNaturalKeyInsertViolatesTheUniqueConstraint() {
        repository.saveAndFlush(entity("12952", "KOTA"));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(entity("12952", "KOTA")));
    }

    @Test
    void reAggregationUpdatesTheExistingRowRatherThanCreatingASecondOne() {
        HistoricalDelayProfileEntity saved = repository.saveAndFlush(entity("12952", "KOTA"));
        entityManager.clear();

        HistoricalDelayProfileEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.updateFrom(new HistoricalDelayProfileEntity(
                "12952", "KOTA", 11, 9.0, 7.0, 3.5, DataProvenance.RAILRADAR,
                Instant.parse("2026-09-10T10:00:00Z")));
        repository.saveAndFlush(reloaded);
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(saved.getId()).orElseThrow().getSampleCount()).isEqualTo(11);
    }

    @Test
    void storesANegativeAverageDelayUnclamped() {
        HistoricalDelayProfileEntity negative = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 2, -3.0, -3.0, 1.0, DataProvenance.RAILRADAR,
                Instant.parse("2026-09-10T09:00:00Z"));

        HistoricalDelayProfileEntity saved = repository.saveAndFlush(negative);
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getAverageArrivalDelayMinutes()).isEqualTo(-3.0);
    }

    @Test
    void persistsAndRetrievesAMixedProvenanceLabel() {
        HistoricalDelayProfileEntity mixed = new HistoricalDelayProfileEntity(
                "12952", "KOTA", 2, 8.0, 8.0, 1.0, "mixed(mock-provider,railradar)",
                Instant.parse("2026-09-10T09:00:00Z"));

        repository.saveAndFlush(mixed);
        entityManager.clear();

        assertThat(repository.findAll().get(0).getSource()).isEqualTo("mixed(mock-provider,railradar)");
    }

    @Test
    void differentStationsForTheSameTrainAreDistinctRows() {
        repository.save(entity("12952", "KOTA"));
        repository.save(entity("12952", "NDLS"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(2);
    }
}
