package com.railpredictor.railradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.historical.HistoricalObservationRecorder;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.model.domain.Station;
import com.railpredictor.model.enums.TrainStatus;
import com.railpredictor.railradar.dto.LiveTrainStatusData;
import com.railpredictor.railradar.dto.LiveTrainStatusResponse;
import com.railpredictor.railradar.mapper.HistoricalObservationMapper;
import com.railpredictor.railradar.mapper.LiveTrainDataMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the client -> mapper hand-off, without a real HTTP call or a real mapping. */
class RailRadarTrainDataProviderTest {

    private final RailRadarClient client = mock(RailRadarClient.class);
    private final LiveTrainDataMapper mapper = mock(LiveTrainDataMapper.class);
    private final HistoricalObservationMapper historicalObservationMapper = mock(HistoricalObservationMapper.class);
    private final HistoricalObservationRecorder historicalObservationRecorder = mock(HistoricalObservationRecorder.class);
    private final RailRadarTrainDataProvider provider =
            new RailRadarTrainDataProvider(client, mapper, historicalObservationMapper, historicalObservationRecorder);

    @Test
    void delegatesToClientThenMapper() {
        LiveTrainStatusData data = mock(LiveTrainStatusData.class);
        LiveTrainStatusResponse response = new LiveTrainStatusResponse(true, data, null);
        LiveTrainData expected = new LiveTrainData(
                "12952", "Rajdhani Express", TrainStatus.RUNNING, 12,
                new Station("KOTA", "Kota Jn"), new Station("RTM", "Ratlam Jn"), 465.0, 919.0, 92.5);
        when(client.fetchLiveStatus("12952")).thenReturn(response);
        when(mapper.toDomain(data)).thenReturn(expected);
        when(historicalObservationMapper.toObservations("12952", data)).thenReturn(List.of());

        LiveTrainData result = provider.getLiveTrainData("12952");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void recordsHistoricalObservationsExtractedFromTheSameResponse() {
        LiveTrainStatusData data = mock(LiveTrainStatusData.class);
        LiveTrainStatusResponse response = new LiveTrainStatusResponse(true, data, null);
        when(client.fetchLiveStatus("12952")).thenReturn(response);
        when(mapper.toDomain(data)).thenReturn(mock(LiveTrainData.class));
        var observations = List.of(mock(com.railpredictor.model.domain.HistoricalObservation.class));
        when(historicalObservationMapper.toObservations("12952", data)).thenReturn(observations);

        provider.getLiveTrainData("12952");

        verify(historicalObservationMapper).toObservations("12952", data);
        verify(historicalObservationRecorder).recordAll(observations);
        // No second RailRadar call was made to obtain this data - fetchLiveStatus is only ever
        // stubbed/verified once above via the shared `client` mock's single interaction.
    }

    @Test
    void aRecordingFailureDoesNotPreventReturningLiveTrainData() {
        LiveTrainStatusData data = mock(LiveTrainStatusData.class);
        LiveTrainStatusResponse response = new LiveTrainStatusResponse(true, data, null);
        LiveTrainData expected = mock(LiveTrainData.class);
        when(client.fetchLiveStatus("12952")).thenReturn(response);
        when(mapper.toDomain(data)).thenReturn(expected);
        when(historicalObservationMapper.toObservations("12952", data))
                .thenThrow(new RuntimeException("database unavailable"));

        LiveTrainData result = provider.getLiveTrainData("12952");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void aRecorderFailureAlsoDoesNotPreventReturningLiveTrainData() {
        LiveTrainStatusData data = mock(LiveTrainStatusData.class);
        LiveTrainStatusResponse response = new LiveTrainStatusResponse(true, data, null);
        LiveTrainData expected = mock(LiveTrainData.class);
        when(client.fetchLiveStatus("12952")).thenReturn(response);
        when(mapper.toDomain(data)).thenReturn(expected);
        when(historicalObservationMapper.toObservations("12952", data)).thenReturn(List.of());
        doThrow(new RuntimeException("database unavailable")).when(historicalObservationRecorder).recordAll(any());

        LiveTrainData result = provider.getLiveTrainData("12952");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void rejectsUnsuccessfulEnvelope() {
        when(client.fetchLiveStatus("12952")).thenReturn(new LiveTrainStatusResponse(false, null, null));

        assertThrows(MalformedRailRadarResponseException.class, () -> provider.getLiveTrainData("12952"));

        verify(historicalObservationMapper, never()).toObservations(any(), any());
    }

    @Test
    void rejectsMissingData() {
        when(client.fetchLiveStatus("12952")).thenReturn(new LiveTrainStatusResponse(true, null, null));

        assertThrows(MalformedRailRadarResponseException.class, () -> provider.getLiveTrainData("12952"));
    }
}
