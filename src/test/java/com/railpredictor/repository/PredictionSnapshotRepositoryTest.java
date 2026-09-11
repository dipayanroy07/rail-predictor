package com.railpredictor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

/**
 * Exercises {@link PredictionSnapshotRepository} against an embedded H2 database - same
 * documented tradeoff as every other repository test in this codebase (no Docker/Testcontainers
 * in this environment).
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PredictionSnapshotRepositoryTest {

    @Autowired
    private PredictionSnapshotRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static PredictionSnapshotEntity pendingEntity(String trainNumber, String stationCode) {
        return new PredictionSnapshotEntity(
                trainNumber, Instant.parse("2026-09-09T10:00:00Z"), stationCode,
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, "STATION_FALLBACK", DataProvenance.RAILRADAR, 75.0,
                "PENDING", null, null, null);
    }

    @Test
    void persistsAndRetrievesASnapshot() {
        PredictionSnapshotEntity saved = repository.save(pendingEntity("12952", "KOTA"));
        entityManager.flush();
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTrainNumber()).isEqualTo("12952");
        assertThat(reloaded.getTargetStationCode()).isEqualTo("KOTA");
        assertThat(reloaded.getEvaluationStatus()).isEqualTo("PENDING");
        assertThat(reloaded.getActualDelayMinutes()).isNull();
    }

    @Test
    void theLegacyConstructorPersistsLiveEvaluationModeByDefault() {
        // Phase 16H-7: every snapshot ever created before this phase (and every one the live
        // recorder creates today) is a live evaluation - proves that default actually round-trips
        // through the database, not just through the in-memory entity.
        PredictionSnapshotEntity saved = repository.save(pendingEntity("12952", "KOTA"));
        entityManager.flush();
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getEvaluationMode()).isEqualTo("LIVE_EVALUATION");
    }

    @Test
    void theLegacyConstructorPersistsANullWeatherProvenanceByDefault() {
        PredictionSnapshotEntity saved = repository.save(pendingEntity("12952", "KOTA"));
        entityManager.flush();
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWeatherProvenance()).isNull();
    }

    @Test
    void anExplicitWeatherProvenanceRoundTripsThroughTheDatabase() {
        PredictionSnapshotEntity entity = new PredictionSnapshotEntity(
                "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, "STATION_FALLBACK", DataProvenance.RAILRADAR, 75.0,
                "PENDING", null, null, null, "LIVE_EVALUATION", DataProvenance.OPENMETEO);
        PredictionSnapshotEntity saved = repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWeatherProvenance()).isEqualTo(DataProvenance.OPENMETEO);
    }

    @Test
    void theLegacyConstructorPersistsANullDisruptionImpactMinutesByDefault() {
        PredictionSnapshotEntity saved = repository.save(pendingEntity("12952", "KOTA"));
        entityManager.flush();
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDisruptionImpactMinutes()).isNull();
    }

    @Test
    void anExplicitDisruptionImpactMinutesRoundTripsThroughTheDatabase() {
        PredictionSnapshotEntity entity = new PredictionSnapshotEntity(
                "12952", Instant.parse("2026-09-09T10:00:00Z"), "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, "STATION_FALLBACK", DataProvenance.RAILRADAR, 75.0,
                "PENDING", null, null, null, "LIVE_EVALUATION", DataProvenance.MOCK, 15);
        PredictionSnapshotEntity saved = repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDisruptionImpactMinutes()).isEqualTo(15);
    }

    @Test
    void findByEvaluationStatusReturnsOnlyMatchingRows() {
        repository.save(pendingEntity("12952", "KOTA"));
        PredictionSnapshotEntity evaluated = pendingEntity("12952", "RTM");
        evaluated.applyEvaluation("EVALUATED_EXACT", 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
        repository.save(evaluated);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByEvaluationStatus("PENDING")).hasSize(1);
        assertThat(repository.findByEvaluationStatus("EVALUATED_EXACT")).hasSize(1);
    }

    @Test
    void findByEvaluationStatusInReturnsBothEvaluatedVariants() {
        PredictionSnapshotEntity exact = pendingEntity("12952", "KOTA");
        exact.applyEvaluation("EVALUATED_EXACT", 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
        PredictionSnapshotEntity approximate = pendingEntity("12952", "RTM");
        approximate.applyEvaluation("EVALUATED_APPROXIMATE", 9, -1, Instant.parse("2026-09-09T11:30:00Z"));
        repository.save(exact);
        repository.save(approximate);
        repository.save(pendingEntity("12952", "BCT"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByEvaluationStatusIn(java.util.List.of("EVALUATED_EXACT", "EVALUATED_APPROXIMATE")))
                .hasSize(2);
    }

    @Test
    void applyEvaluationUpdatesTheExistingRowInPlace() {
        PredictionSnapshotEntity saved = repository.saveAndFlush(pendingEntity("12952", "KOTA"));
        entityManager.clear();

        PredictionSnapshotEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.applyEvaluation("EVALUATED_EXACT", 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
        repository.saveAndFlush(reloaded);
        entityManager.clear();

        assertThat(repository.findAll()).hasSize(1);
        PredictionSnapshotEntity updated = repository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getEvaluationStatus()).isEqualTo("EVALUATED_EXACT");
        assertThat(updated.getActualDelayMinutes()).isEqualTo(6);
        assertThat(updated.getErrorMinutes()).isEqualTo(2);
    }
}
