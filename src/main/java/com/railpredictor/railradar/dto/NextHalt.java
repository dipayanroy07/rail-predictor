package com.railpredictor.railradar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The train's next scheduled stop. Absent (null) when there is none, e.g. a terminated train. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NextHalt(String stationCode, String stationName, Integer sequence, Double distance) {
}
