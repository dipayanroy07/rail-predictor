package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PredictionResultTest {

    @Test
    void fixtureConstructsSuccessfully() {
        assertDoesNotThrow(DomainFixtures::predictionResult);
    }

    @Test
    void rejectsNullEta() {
        PredictionResult valid = DomainFixtures.predictionResult();
        assertThrows(IllegalArgumentException.class, () -> new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), null,
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings()));
    }

    @Test
    void rejectsNegativePredictedTotalDelay() {
        PredictionResult valid = DomainFixtures.predictionResult();
        assertThrows(IllegalArgumentException.class, () -> new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), -1, Instant.now(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings()));
    }

    @Test
    void allowsNegativeHistoricalAdjustmentSinceItIsAnAdjustmentNotARawDelay() {
        PredictionResult valid = DomainFixtures.predictionResult();
        assertDoesNotThrow(() -> new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), -3,
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings()));
    }

    @Test
    void theLegacyTwentyArgumentConstructorDefaultsToNoHistoricalResolution() {
        // Phase 16H-3: callers unaware of historical-source transparency get an explicit,
        // unmistakable "nothing resolved" default - never a fabricated real-looking source.
        PredictionResult result = DomainFixtures.predictionResult();

        assertThat(result.historicalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.NONE);
        assertThat(result.historicalAdjustmentResolution().provenance()).isEqualTo(DataProvenance.UNAVAILABLE);
    }

    @Test
    void theCanonicalConstructorPreservesAnExplicitlySuppliedResolution() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution resolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR);

        PredictionResult result = new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), resolution);

        assertThat(result.historicalAdjustmentResolution()).isEqualTo(resolution);
    }

    @Test
    void rejectsANullHistoricalAdjustmentResolution() {
        PredictionResult valid = DomainFixtures.predictionResult();
        assertThrows(IllegalArgumentException.class, () -> new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), null));
    }

    // --- Phase 17: weatherProvenance ---

    @Test
    void theLegacyTwentyOneArgumentConstructorDefaultsWeatherProvenanceToNull() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution resolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);

        PredictionResult result = new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), resolution);

        assertThat(result.weatherProvenance()).isNull();
    }

    @Test
    void theCanonicalConstructorPreservesAnExplicitWeatherProvenance() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution resolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);

        PredictionResult result = new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), resolution,
                DataProvenance.OPENMETEO);

        assertThat(result.weatherProvenance()).isEqualTo(DataProvenance.OPENMETEO);
    }

    // --- Phase 21: nextStationHistoricalAdjustment*/predictedNextStationDelayMinutes ---

    @Test
    void theLegacyTwentyThreeArgumentConstructorDefaultsPredictedNextStationDelayToCurrentPlusExtraPlusDisruption() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution resolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR);
        DisruptionImpact impact = new DisruptionImpact(
                RailwayDisruptionType.ENGINEERING_BLOCK, DisruptionImpactStatus.ESTIMATED, 4, "heuristic", DataProvenance.MOCK);
        DisruptionImpactAssessment assessment = new DisruptionImpactAssessment(
                DisruptionImpactStatus.ESTIMATED, 4, java.util.List.of(impact), false, CalibrationStatus.INSUFFICIENT_DATA);

        PredictionResult result = new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), resolution,
                DataProvenance.OPENMETEO, assessment);

        assertThat(result.nextStationHistoricalAdjustmentMinutes()).isZero();
        assertThat(result.nextStationHistoricalAdjustmentResolution().source()).isEqualTo(HistoricalAdjustmentSource.NONE);
        assertThat(result.predictedNextStationDelayMinutes())
                .isEqualTo(valid.currentDelayMinutes() + valid.predictedExtraDelayMinutes() + 4);
    }

    @Test
    void theCanonicalConstructorPreservesExplicitNextStationFieldsWithoutTouchingTheDestinationScopedOnes() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution destinationResolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.SECTION, DataProvenance.RAILRADAR);
        HistoricalAdjustmentResolution nextStationResolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.MOCK);

        PredictionResult result = new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), 99, // whole-route historical - deliberately distinct
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), destinationResolution,
                null, DisruptionImpactAssessment.unavailable(),
                5, nextStationResolution, 42);

        assertThat(result.historicalAdjustmentMinutes()).isEqualTo(99);
        assertThat(result.historicalAdjustmentResolution()).isEqualTo(destinationResolution);
        assertThat(result.nextStationHistoricalAdjustmentMinutes()).isEqualTo(5);
        assertThat(result.nextStationHistoricalAdjustmentResolution()).isEqualTo(nextStationResolution);
        assertThat(result.predictedNextStationDelayMinutes()).isEqualTo(42);
    }

    @Test
    void rejectsANegativePredictedNextStationDelay() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution resolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE);

        assertThrows(IllegalArgumentException.class, () -> new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), resolution,
                null, DisruptionImpactAssessment.unavailable(), 0, resolution, -1));
    }

    @Test
    void rejectsANullNextStationHistoricalAdjustmentResolution() {
        PredictionResult valid = DomainFixtures.predictionResult();
        HistoricalAdjustmentResolution resolution =
                new HistoricalAdjustmentResolution(HistoricalAdjustmentSource.NONE, DataProvenance.UNAVAILABLE);

        assertThrows(IllegalArgumentException.class, () -> new PredictionResult(
                valid.trainNumber(), valid.trainName(), valid.status(), valid.currentStation(),
                valid.nextStation(), valid.currentDelayMinutes(), valid.distanceFromOriginKm(), valid.remainingDistanceKm(),
                valid.estimatedSpeedKmh(), valid.sectionType(), valid.baseTravelTimeMinutes(),
                valid.predictedExtraDelayMinutes(), valid.historicalAdjustmentMinutes(),
                valid.recoveryMinutes(), valid.predictedTotalDelayMinutes(), valid.predictedEta(),
                valid.confidence(), valid.disruptions(), valid.cascadeEffects(), valid.warnings(), resolution,
                null, DisruptionImpactAssessment.unavailable(), 0, null, 0));
    }
}
