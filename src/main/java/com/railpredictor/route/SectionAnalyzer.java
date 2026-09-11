package com.railpredictor.route;

import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.enums.SectionType;

/**
 * Estimates the condition of the route section a train is currently on. This is a model
 * estimate, not a measurement of actual occupancy/congestion - see {@link SectionType} and
 * docs/architecture.md. Future implementations can incorporate real occupancy, headway, train
 * density, signalling, or traffic data without changing this interface.
 */
public interface SectionAnalyzer {

    SectionType analyze(LiveTrainData train);
}
