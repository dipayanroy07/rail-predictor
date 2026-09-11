package com.railpredictor.simulation;

import com.railpredictor.model.domain.CascadeEffect;
import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;
import com.railpredictor.model.domain.SimulationResult;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the disruption models: runs each against the given context, sums the delay from
 * whichever ones triggered, feeds that into the cascade engine, applies the recovery model, and
 * produces one {@link SimulationResult}. Deliberately holds none of the individual models' logic
 * - Spring supplies every registered {@link DisruptionModel} bean here automatically, so adding
 * or removing a model never requires touching this class.
 */
@Component
public class SimulationEngine {

    private final List<DisruptionModel> disruptionModels;
    private final CascadeEngine cascadeEngine;
    private final RecoveryModel recoveryModel;

    public SimulationEngine(List<DisruptionModel> disruptionModels, CascadeEngine cascadeEngine, RecoveryModel recoveryModel) {
        this.disruptionModels = List.copyOf(disruptionModels);
        this.cascadeEngine = cascadeEngine;
        this.recoveryModel = recoveryModel;
    }

    public SimulationResult simulate(SimulationContext context) {
        Objects.requireNonNull(context, "context");
        LiveTrainData train = context.train();
        RouteSection section = context.section();

        List<DisruptionResult> disruptions = disruptionModels.stream()
                .map(model -> model.simulate(train, section, context))
                .toList();

        int directDisruptionDelayMinutes = disruptions.stream()
                .filter(DisruptionResult::triggered)
                .mapToInt(DisruptionResult::delayMinutes)
                .sum();

        List<CascadeEffect> cascadeEffects = cascadeEngine.cascade(directDisruptionDelayMinutes);
        int cascadeDelayMinutes = cascadeEffects.stream().mapToInt(CascadeEffect::additionalDelayMinutes).sum();

        int recoveredDelayMinutes = recoveryModel.recover(directDisruptionDelayMinutes + cascadeDelayMinutes, context);

        int netDelayMinutes = Math.max(0, directDisruptionDelayMinutes + cascadeDelayMinutes - recoveredDelayMinutes);

        return new SimulationResult(
                disruptions, cascadeEffects, directDisruptionDelayMinutes, cascadeDelayMinutes,
                recoveredDelayMinutes, netDelayMinutes);
    }
}
