package com.railpredictor.repository;

import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationMode;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import org.springframework.stereotype.Component;

/**
 * Explicit mapping between the framework-free {@link PredictionSnapshot} domain record and the
 * JPA {@link PredictionSnapshotEntity} - mirrors every other entity mapper in this codebase
 * (e.g. {@code HistoricalObservationEntityMapper}).
 */
@Component
public class PredictionSnapshotEntityMapper {

    public PredictionSnapshotEntity toEntity(PredictionSnapshot snapshot) {
        return new PredictionSnapshotEntity(
                snapshot.trainNumber(),
                snapshot.predictionMadeAt(),
                snapshot.targetStationCode(),
                snapshot.currentDelayMinutes(),
                snapshot.predictedNextStationDelayMinutes(),
                snapshot.predictedTotalDelayMinutes(),
                snapshot.predictedEta(),
                snapshot.historicalAdjustmentMinutes(),
                snapshot.historicalAdjustmentSource().name(),
                snapshot.historicalAdjustmentProvenance(),
                snapshot.confidenceScore(),
                snapshot.evaluationStatus().name(),
                snapshot.actualDelayMinutes(),
                snapshot.errorMinutes(),
                snapshot.evaluatedAt(),
                snapshot.evaluationMode().name(),
                snapshot.weatherProvenance(),
                snapshot.disruptionImpactMinutes(),
                snapshot.nextStationHistoricalAdjustmentMinutes(),
                snapshot.nextStationHistoricalAdjustmentSource().name(),
                snapshot.nextStationHistoricalAdjustmentProvenance(),
                snapshot.predictedExtraDelayMinutes());
    }

    public PredictionSnapshot toDomain(PredictionSnapshotEntity entity) {
        return new PredictionSnapshot(
                entity.getId(),
                entity.getTrainNumber(),
                entity.getPredictionMadeAt(),
                entity.getTargetStationCode(),
                entity.getCurrentDelayMinutes(),
                entity.getPredictedNextStationDelayMinutes(),
                entity.getPredictedTotalDelayMinutes(),
                entity.getPredictedEta(),
                entity.getHistoricalAdjustmentMinutes(),
                HistoricalAdjustmentSource.valueOf(entity.getHistoricalAdjustmentSource()),
                entity.getHistoricalAdjustmentProvenance(),
                entity.getConfidenceScore(),
                PredictionEvaluationStatus.valueOf(entity.getEvaluationStatus()),
                entity.getActualDelayMinutes(),
                entity.getErrorMinutes(),
                entity.getEvaluatedAt(),
                PredictionEvaluationMode.valueOf(entity.getEvaluationMode()),
                entity.getWeatherProvenance(),
                entity.getDisruptionImpactMinutes(),
                entity.getNextStationHistoricalAdjustmentMinutes(),
                HistoricalAdjustmentSource.valueOf(entity.getNextStationHistoricalAdjustmentSource()),
                entity.getNextStationHistoricalAdjustmentProvenance(),
                entity.getPredictedExtraDelayMinutes());
    }
}
