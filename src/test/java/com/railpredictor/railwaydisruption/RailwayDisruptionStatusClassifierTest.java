package com.railpredictor.railwaydisruption;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.RailwayDisruption;
import com.railpredictor.model.domain.RailwayDisruptionStatus;
import com.railpredictor.model.domain.RailwayDisruptionType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RailwayDisruptionStatusClassifierTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private static RailwayDisruption disruption(Instant effectiveFrom, Instant effectiveUntil) {
        return new RailwayDisruption(
                RailwayDisruptionType.TEMPORARY_SPEED_RESTRICTION, "12952", "KOTA", "RTM",
                40.0, null, effectiveFrom, effectiveUntil, OBSERVED_AT, DataProvenance.MOCK, null);
    }

    @Test
    void activeWhenReferenceInstantIsWithinAnOpenEndedWindow() {
        RailwayDisruption d = disruption(Instant.parse("2026-09-10T00:00:00Z"), null);

        RailwayDisruptionStatus status =
                RailwayDisruptionStatusClassifier.classify(d, Instant.parse("2026-09-10T12:00:00Z"));

        assertThat(status).isEqualTo(RailwayDisruptionStatus.ACTIVE);
    }

    @Test
    void activeWhenReferenceInstantIsWithinAClosedWindow() {
        RailwayDisruption d = disruption(
                Instant.parse("2026-09-10T00:00:00Z"), Instant.parse("2026-09-12T00:00:00Z"));

        RailwayDisruptionStatus status =
                RailwayDisruptionStatusClassifier.classify(d, Instant.parse("2026-09-11T00:00:00Z"));

        assertThat(status).isEqualTo(RailwayDisruptionStatus.ACTIVE);
    }

    @Test
    void activeExactlyAtEffectiveFrom() {
        Instant from = Instant.parse("2026-09-10T00:00:00Z");
        RailwayDisruption d = disruption(from, null);

        assertThat(RailwayDisruptionStatusClassifier.classify(d, from)).isEqualTo(RailwayDisruptionStatus.ACTIVE);
    }

    @Test
    void activeExactlyAtEffectiveUntil() {
        Instant until = Instant.parse("2026-09-12T00:00:00Z");
        RailwayDisruption d = disruption(Instant.parse("2026-09-10T00:00:00Z"), until);

        assertThat(RailwayDisruptionStatusClassifier.classify(d, until)).isEqualTo(RailwayDisruptionStatus.ACTIVE);
    }

    @Test
    void futureEffectiveWhenReferenceInstantIsBeforeEffectiveFrom() {
        RailwayDisruption d = disruption(Instant.parse("2026-09-10T00:00:00Z"), null);

        RailwayDisruptionStatus status =
                RailwayDisruptionStatusClassifier.classify(d, Instant.parse("2026-09-09T23:59:59Z"));

        assertThat(status).isEqualTo(RailwayDisruptionStatus.FUTURE_EFFECTIVE);
    }

    @Test
    void expiredWhenReferenceInstantIsAfterEffectiveUntil() {
        RailwayDisruption d = disruption(
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-05T00:00:00Z"));

        RailwayDisruptionStatus status =
                RailwayDisruptionStatusClassifier.classify(d, Instant.parse("2026-09-06T00:00:00Z"));

        assertThat(status).isEqualTo(RailwayDisruptionStatus.EXPIRED);
    }

    @Test
    void unknownValidityWhenEffectiveFromIsMissing() {
        RailwayDisruption d = disruption(null, null);

        assertThat(RailwayDisruptionStatusClassifier.classify(d, Instant.parse("2026-09-10T00:00:00Z")))
                .isEqualTo(RailwayDisruptionStatus.UNKNOWN_VALIDITY);
    }

    @Test
    void unknownValidityWhenEffectiveFromIsMissingEvenIfEffectiveUntilIsPresent() {
        // Never assume active just because an end date happens to exist without a start date.
        RailwayDisruption d = disruption(null, Instant.parse("2026-09-20T00:00:00Z"));

        assertThat(RailwayDisruptionStatusClassifier.classify(d, Instant.parse("2026-09-10T00:00:00Z")))
                .isEqualTo(RailwayDisruptionStatus.UNKNOWN_VALIDITY);
    }
}
