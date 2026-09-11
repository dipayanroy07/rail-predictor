package com.railpredictor.model.enums;

/**
 * Estimated route-section condition. This is a model estimate (currently derived from delay
 * behaviour), not a measurement of actual occupancy/congestion - see docs/architecture.md.
 */
public enum SectionType {
    CLEAR,
    NORMAL,
    BUSY
}
