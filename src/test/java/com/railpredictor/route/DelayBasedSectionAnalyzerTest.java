package com.railpredictor.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.config.SectionAnalysisProperties;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.SectionType;
import com.railpredictor.model.enums.TrainStatus;
import org.junit.jupiter.api.Test;

class DelayBasedSectionAnalyzerTest {

    private final DelayBasedSectionAnalyzer analyzer =
            new DelayBasedSectionAnalyzer(new SectionAnalysisProperties(10, 30));

    private static LiveTrainData trainWithDelay(int delayMinutes) {
        return new LiveTrainData(
                "12345", "Test Express", TrainStatus.RUNNING, delayMinutes,
                new Station("NDLS", "New Delhi"), new Station("GZB", "Ghaziabad"), 10.0, 25.0, 80.0);
    }

    @Test
    void belowNormalThresholdIsClear() {
        assertThat(analyzer.analyze(trainWithDelay(0))).isEqualTo(SectionType.CLEAR);
        assertThat(analyzer.analyze(trainWithDelay(9))).isEqualTo(SectionType.CLEAR);
    }

    @Test
    void atNormalThresholdIsNormalNotClear() {
        assertThat(analyzer.analyze(trainWithDelay(10))).isEqualTo(SectionType.NORMAL);
    }

    @Test
    void justBelowBusyThresholdIsStillNormal() {
        assertThat(analyzer.analyze(trainWithDelay(29))).isEqualTo(SectionType.NORMAL);
    }

    @Test
    void atOrAboveBusyThresholdIsBusy() {
        assertThat(analyzer.analyze(trainWithDelay(30))).isEqualTo(SectionType.BUSY);
        assertThat(analyzer.analyze(trainWithDelay(120))).isEqualTo(SectionType.BUSY);
    }

    @Test
    void rejectsNullTrain() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> analyzer.analyze(null));
    }
}
