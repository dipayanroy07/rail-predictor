package com.railpredictor.model.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RemainingRouteTest {

    private static Station station(String code) {
        return new Station(code, code + " Station");
    }

    @Test
    void unavailableMustCarryNoDestinationOrSections() {
        assertDoesNotThrow(() -> new RemainingRoute(station("A"), null, List.of(), RouteCompleteness.UNAVAILABLE));
    }

    @Test
    void unavailableWithADestinationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemainingRoute(station("A"), station("B"), List.of(), RouteCompleteness.UNAVAILABLE));
    }

    @Test
    void completeRequiresADestination() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemainingRoute(station("A"), null, List.of(), RouteCompleteness.COMPLETE));
    }

    @Test
    void completeWithZeroSectionsIsValid() {
        assertDoesNotThrow(() -> new RemainingRoute(station("A"), station("A"), List.of(), RouteCompleteness.COMPLETE));
    }
}
