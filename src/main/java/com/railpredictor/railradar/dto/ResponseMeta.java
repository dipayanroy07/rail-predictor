package com.railpredictor.railradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The {@code meta} block every RailRadar response envelope carries. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResponseMeta(String traceId, Long executionTime, String timestamp) {
}
