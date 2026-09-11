package com.railpredictor.historical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HistoricalDataMetricsTest {

    @Test
    void countersStartAtZeroAndIncrementIndependently() {
        HistoricalDataMetrics metrics = new HistoricalDataMetrics();

        assertThat(metrics.observationsReceived()).isZero();
        assertThat(metrics.observationsRejected()).isZero();
        assertThat(metrics.observationsInserted()).isZero();
        assertThat(metrics.observationsUpdated()).isZero();
        assertThat(metrics.persistenceFailures()).isZero();
        assertThat(metrics.profilesGenerated()).isZero();
        assertThat(metrics.profileRefreshFailures()).isZero();

        metrics.observationReceived();
        metrics.observationReceived();
        metrics.observationRejected();
        metrics.observationInserted();
        metrics.observationUpdated();
        metrics.persistenceFailure();
        metrics.profileGenerated();
        metrics.profileRefreshFailure();

        assertThat(metrics.observationsReceived()).isEqualTo(2);
        assertThat(metrics.observationsRejected()).isEqualTo(1);
        assertThat(metrics.observationsInserted()).isEqualTo(1);
        assertThat(metrics.observationsUpdated()).isEqualTo(1);
        assertThat(metrics.persistenceFailures()).isEqualTo(1);
        assertThat(metrics.profilesGenerated()).isEqualTo(1);
        assertThat(metrics.profileRefreshFailures()).isEqualTo(1);
    }
}
