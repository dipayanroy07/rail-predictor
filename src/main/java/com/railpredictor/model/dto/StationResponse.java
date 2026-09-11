package com.railpredictor.model.dto;

/**
 * A station as shown to a frontend. {@code latitude}/{@code longitude} are real data when
 * present (from RailRadar's route data), null when not known - never fabricated.
 */
public record StationResponse(String code, String name, Double latitude, Double longitude) {
}
