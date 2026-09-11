package com.railpredictor.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalAdjustmentSource;
import com.railpredictor.model.domain.PredictionEvaluationStatus;
import com.railpredictor.model.domain.PredictionSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChronologicalSplitterTest {

    private final ChronologicalSplitter splitter = new ChronologicalSplitter();

    private static PredictionSnapshot at(Instant predictionMadeAt) {
        return new PredictionSnapshot(
                1L, "12952", predictionMadeAt, "KOTA",
                5, 8, 10, Instant.parse("2026-09-09T12:00:00Z"),
                3, HistoricalAdjustmentSource.STATION_FALLBACK, DataProvenance.RAILRADAR,
                75.0, PredictionEvaluationStatus.EVALUATED_EXACT, 6, 2, Instant.parse("2026-09-09T11:00:00Z"));
    }

    @Test
    void everyTrainingInstantIsNoLaterThanEveryValidationInstant() {
        List<PredictionSnapshot> snapshots = IntStream.range(0, 10)
                .mapToObj(i -> at(Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i * 3600L)))
                .toList();

        ChronologicalSplitter.Split split = splitter.split(snapshots, 0.3);

        Instant latestTraining = split.training().stream()
                .map(PredictionSnapshot::predictionMadeAt).max(Comparator.naturalOrder()).orElseThrow();
        Instant earliestValidation = split.validation().stream()
                .map(PredictionSnapshot::predictionMadeAt).min(Comparator.naturalOrder()).orElseThrow();

        assertThat(latestTraining).isBeforeOrEqualTo(earliestValidation);
        assertThat(split.training().size() + split.validation().size()).isEqualTo(10);
    }

    @Test
    void splitIsUnaffectedByInputOrder() {
        List<PredictionSnapshot> chronological = IntStream.range(0, 10)
                .mapToObj(i -> at(Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i * 3600L)))
                .toList();
        List<PredictionSnapshot> shuffled = new java.util.ArrayList<>(chronological);
        java.util.Collections.reverse(shuffled);

        ChronologicalSplitter.Split split = splitter.split(shuffled, 0.3);

        assertThat(split.training().get(0).predictionMadeAt()).isEqualTo(chronological.get(0).predictionMadeAt());
        assertThat(split.validation().get(split.validation().size() - 1).predictionMadeAt())
                .isEqualTo(chronological.get(chronological.size() - 1).predictionMadeAt());
    }

    @Test
    void rejectsValidationSplitOutOfRange() {
        assertThatThrownBy(() -> splitter.split(List.of(), 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> splitter.split(List.of(), 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> splitter.split(List.of(), -0.2)).isInstanceOf(IllegalArgumentException.class);
    }
}
