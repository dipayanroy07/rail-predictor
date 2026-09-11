package com.railpredictor.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalObservation;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HistoricalObservationEntityMapperTest {

    private final HistoricalObservationEntityMapper mapper = new HistoricalObservationEntityMapper();

    @Test
    void roundTripsEveryFieldThroughTheEntityAndBackToDomain() {
        HistoricalObservation original = new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", 5,
                "20:39", "20:51", "20:41", "20:53", 12, 12,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);

        HistoricalObservationEntity entity = mapper.toEntity(original);
        HistoricalObservation roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void roundTripsNullableFieldsAsNull() {
        HistoricalObservation original = new HistoricalObservation(
                "12952", LocalDate.of(2026, 9, 9), "KOTA", null,
                null, null, null, null, null, null,
                Instant.parse("2026-09-09T15:00:00Z"), DataProvenance.RAILRADAR);

        HistoricalObservationEntity entity = mapper.toEntity(original);

        assertThat(entity.getStationSequence()).isNull();
        assertThat(entity.getScheduledArrival()).isNull();
        assertThat(entity.getArrivalDelayMinutes()).isNull();
        assertThat(mapper.toDomain(entity)).isEqualTo(original);
    }
}
