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
}
