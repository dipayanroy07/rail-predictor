package com.railpredictor.prediction;

import com.railpredictor.model.domain.SimulationResult;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Extracts the "predicted extra delay" figure from a {@link SimulationResult} for use in a
 * prediction. This is the seam between simulation-space and prediction-space: callers building a
 * {@code PredictionResult} go through here rather than reading {@code SimulationResult} fields
 * directly, so the "what does this number already include" question (see
 * docs/prediction-model.md) has exactly one place to be answered.
 */
@Component
public class DelayCalculator {

    public int predictedExtraDelayMinutes(SimulationResult simulationResult) {
        Objects.requireNonNull(simulationResult, "simulationResult");
        // netDelayMinutes is already (direct disruption delay + cascade delay - recovered delay),
        // clamped at 0 - see SimulationEngine. Nothing further to combine here.
        return simulationResult.netDelayMinutes();
    }
}
