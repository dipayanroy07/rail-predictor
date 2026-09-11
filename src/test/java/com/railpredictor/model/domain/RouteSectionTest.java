package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RouteSectionTest {

    @Test
    void allowsNullDistanceWhenNotDerivable() {
        assertDoesNotThrow(() ->
                new RouteSection(DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), null));
    }

    @Test
    void allowsZeroDistance() {
        assertDoesNotThrow(() ->
                new RouteSection(DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), 0.0));
    }

    @Test
    void rejectsNegativeDistance() {
        assertThrows(IllegalArgumentException.class, () ->
                new RouteSection(DomainFixtures.station("NDLS"), DomainFixtures.station("GZB"), -1.0));
    }

    @Test
    void rejectsNullStations() {
        assertThrows(IllegalArgumentException.class, () ->
                new RouteSection(null, DomainFixtures.station("GZB"), 25.0));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteSection(DomainFixtures.station("NDLS"), null, 25.0));
    }
}
