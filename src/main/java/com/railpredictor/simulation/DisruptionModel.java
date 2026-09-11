package com.railpredictor.simulation;

import com.railpredictor.model.domain.DisruptionResult;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SimulationContext;

/**
 * One disruption type's simulation logic. Each implementation must be independently testable and
 * must document its own simulation assumptions - its output is simulated data, never to be
 * presented as a real railway statistic. Orchestrating multiple models together is
 * {@code SimulationEngine}'s job (Phase 8), not this interface's.
 */
public interface DisruptionModel {

    DisruptionResult simulate(LiveTrainData train, RouteSection section, SimulationContext context);
}
