package com.railpredictor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalCollectionPropertiesTest {

    @Test
    void emptyTrainNumbersIsSafeByDefault() {
        HistoricalCollectionProperties properties =
                new HistoricalCollectionProperties(false, 3600000, 300000, List.of());

        assertThat(properties.trainNumbers()).isEmpty();
    }

    @Test
    void blankEntriesAreTolerated() {
        // A comma-separated property that binds to List.of("") when unset must not be rejected as
        // an invalid train number.
        HistoricalCollectionProperties properties =
                new HistoricalCollectionProperties(false, 3600000, 300000, List.of("", "  "));

        assertThat(properties.trainNumbers()).isEmpty();
    }

    @Test
    void duplicateTrainNumbersAreRemovedPreservingOrder() {
        HistoricalCollectionProperties properties =
                new HistoricalCollectionProperties(true, 3600000, 300000, List.of("12952", "12002", "12952"));

        assertThat(properties.trainNumbers()).containsExactly("12952", "12002");
    }

    @Test
    void whitespaceAroundATrainNumberIsTrimmed() {
        HistoricalCollectionProperties properties =
                new HistoricalCollectionProperties(true, 3600000, 300000, List.of(" 12952 "));

        assertThat(properties.trainNumbers()).containsExactly("12952");
    }

    @Test
    void rejectsATrainNumberThatIsNotExactlyFiveDigits() {
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, 3600000, 300000, List.of("123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, 3600000, 300000, List.of("ABCDE")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, 3600000, 300000, List.of("123456")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveIntervalMs() {
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, 0, 300000, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, -1, 300000, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeInitialDelayMs() {
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, 3600000, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullTrainNumbers() {
        assertThatThrownBy(() -> new HistoricalCollectionProperties(true, 3600000, 300000, null))
                .isInstanceOf(NullPointerException.class);
    }
}
