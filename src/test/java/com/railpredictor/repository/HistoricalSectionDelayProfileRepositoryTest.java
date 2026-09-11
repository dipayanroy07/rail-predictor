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
 * Exercises {@link HistoricalSectionDelayProfileRepository} against an embedded H2 database - same
 * documented tradeoff as {@link HistoricalObservationRepositoryTest} and
 * {@link HistoricalDelayProfileRepositoryTest} (no Docker/Testcontainers in this environment).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HistoricalSectionDelayProfileRepositoryTest {

    @Autowired
    private HistoricalSectionDelayProfileRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static HistoricalSectionDelayProfileEntity entity(
            String trainNumber, String from, String to, int sampleCount, double average) {
        return new HistoricalSectionDelayProfileEntity(
                trainNumber, from, to, sampleCount, average, average, 0.0,
                DataProvenance.RAILRADAR, Instant.parse("2026-09-09T09:00:00Z"));
    }

    @Test
    void persistsAndRetrievesASectionProfile() {
        HistoricalSectionDelayProfileEntity saved = repository.save(entity("12952", "A", "B", 3, 5.0));
        entityManager.flush();
        entityManager.clear();

        HistoricalSectionDelayProfileEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFromStationCode()).isEqualTo("A");
        assertThat(reloaded.getToStationCode()).isEqualTo("B");
        assertThat(reloaded.getSampleCount()).isEqualTo(3);
        assertThat(reloaded.getAverageDelayChangeMinutes()).isEqualTo(5.0);
    }

    @Test
    void findByNaturalKeyLocatesAnExistingRow() {
        repository.saveAndFlush(entity("12952", "A", "B", 3, 5.0));
        entityManager.clear();

        Optional<HistoricalSectionDelayProfileEntity> found =
                repository.findByTrainNumberAndFromStationCodeAndToStationCode("12952", "A", "B");

        assertThat(found).isPresent();
    }

    @Test
    void duplicateNaturalKeyInsertViolatesTheUniqueConstraint() {
        repository.saveAndFlush(entity("12952", "A", "B", 3, 5.0));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(entity("12952", "A", "B", 5, 6.0)));
    }

    @Test
    void reversingFromAndToIsADistinctRow() {
        repository.saveAndFlush(entity("12952", "A", "B", 3, 5.0));
        repository.saveAndFlush(entity("12952", "B", "A", 2, -1.0));
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void replacingAnExistingProfileUpdatesInPlaceRatherThanDuplicating() {
        HistoricalSectionDelayProfileEntity saved = repository.saveAndFlush(entity("12952", "A", "B", 3, 5.0));
        entityManager.clear();

        HistoricalSectionDelayProfileEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.updateFrom(entity("12952", "A", "B", 7, 8.5));
        repository.saveAndFlush(reloaded);
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(saved.getId()).orElseThrow().getSampleCount()).isEqualTo(7);
    }

    @Test
    void aZeroSampleProfilePersistsCorrectly() {
        HistoricalSectionDelayProfileEntity zeroSample = new HistoricalSectionDelayProfileEntity(
                "12952", "A", "B", 0, 0.0, 0.0, 0.0, DataProvenance.UNAVAILABLE, Instant.parse("2026-09-09T09:00:00Z"));

        HistoricalSectionDelayProfileEntity saved = repository.saveAndFlush(zeroSample);
        entityManager.clear();

        HistoricalSectionDelayProfileEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSampleCount()).isZero();
        assertThat(reloaded.getSource()).isEqualTo(DataProvenance.UNAVAILABLE);
    }
}
