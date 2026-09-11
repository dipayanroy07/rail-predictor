package com.railpredictor.route;

import com.railpredictor.config.SectionAnalysisProperties;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.enums.SectionType;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Estimates section condition purely from the train's own current delay, against configurable
 * thresholds. A train's delay does not prove the section itself is congested (it could be a
 * late departure, a mechanical issue, etc.) - this is a starting heuristic, to be replaced or
 * augmented once real occupancy/headway/traffic data is available.
 */
@Component
public class DelayBasedSectionAnalyzer implements SectionAnalyzer {

    private final SectionAnalysisProperties properties;

    public DelayBasedSectionAnalyzer(SectionAnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public SectionType analyze(LiveTrainData train) {
        Objects.requireNonNull(train, "train");
        int delay = train.currentDelayMinutes();
        if (delay >= properties.busyThresholdMinutes()) {
            return SectionType.BUSY;
        }
        if (delay >= properties.normalThresholdMinutes()) {
            return SectionType.NORMAL;
        }
        return SectionType.CLEAR;
    }
}
