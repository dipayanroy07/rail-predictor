package com.railpredictor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelayProfile;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HistoricalDelayProfileEntityMapperTest {

    private final HistoricalDelayProfileEntityMapper mapper = new HistoricalDelayProfileEntityMapper();

    @Test
    void roundTripsEveryFieldThroughTheEntityAndBackToDomain() {
        HistoricalDelayProfile original = new HistoricalDelayProfile(
                "12952", "KOTA", 10, 8.5, 6.0, 3.25, DataProvenance.RAILRADAR,
                Instant.parse("2026-09-10T09:00:00Z"));

        HistoricalDelayProfileEntity entity = mapper.toEntity(original);
        HistoricalDelayProfile roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void roundTripsANegativeAverageUnclamped() {
        HistoricalDelayProfile original = new HistoricalDelayProfile(
                "12952", "KOTA", 2, -3.0, -3.0, 1.0, DataProvenance.RAILRADAR,
                Instant.parse("2026-09-10T09:00:00Z"));

        HistoricalDelayProfileEntity entity = mapper.toEntity(original);

        assertThat(entity.getAverageArrivalDelayMinutes()).isEqualTo(-3.0);
        assertThat(mapper.toDomain(entity)).isEqualTo(original);
    }
}
