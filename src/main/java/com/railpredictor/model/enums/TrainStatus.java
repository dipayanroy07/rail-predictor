package com.railpredictor.model.enums;

/** High-level train status, mapped from whatever raw status string RailRadar reports. */
public enum TrainStatus {
    SCHEDULED,
    RUNNING,
    DELAYED,
    TERMINATED,
    CANCELLED,
    UNKNOWN
}
