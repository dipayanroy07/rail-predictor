package com.railpredictor.railradar;

import com.railpredictor.exception.MalformedRailRadarResponseException;
import com.railpredictor.historical.HistoricalObservationRecorder;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.LiveTrainData;
import com.railpredictor.railradar.dto.LiveTrainStatusData;
import com.railpredictor.railradar.dto.LiveTrainStatusResponse;
import com.railpredictor.railradar.mapper.HistoricalObservationMapper;
import com.railpredictor.railradar.mapper.LiveTrainDataMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The {@link TrainDataProvider} backed by the real RailRadar API. */
@Component
class RailRadarTrainDataProvider implements TrainDataProvider {

    private static final Logger log = LoggerFactory.getLogger(RailRadarTrainDataProvider.class);

    private final RailRadarClient client;
    private final LiveTrainDataMapper mapper;
    private final HistoricalObservationMapper historicalObservationMapper;
    private final HistoricalObservationRecorder historicalObservationRecorder;

    RailRadarTrainDataProvider(
            RailRadarClient client,
            LiveTrainDataMapper mapper,
            HistoricalObservationMapper historicalObservationMapper,
            HistoricalObservationRecorder historicalObservationRecorder) {
        this.client = client;
        this.mapper = mapper;
        this.historicalObservationMapper = historicalObservationMapper;
        this.historicalObservationRecorder = historicalObservationRecorder;
    }

    @Override
    public LiveTrainData getLiveTrainData(String trainNumber) {
        LiveTrainStatusResponse response = client.fetchLiveStatus(trainNumber);
        if (response == null || !response.success() || response.data() == null) {
            throw new MalformedRailRadarResponseException(
                    "RailRadar response for train " + trainNumber + " was unsuccessful or had no data");
        }
        recordHistoricalObservationsSafely(trainNumber, response.data());
        return mapper.toDomain(response.data());
    }

    /**
     * Extracts and persists historical observations from the same response already fetched above
     * - never a second RailRadar call. Best-effort: a failure here (e.g. no database configured,
     * or the database unavailable) must never prevent returning live train data, so it's caught
     * and logged, never propagated.
     */
    private void recordHistoricalObservationsSafely(String trainNumber, LiveTrainStatusData data) {
        try {
            List<HistoricalObservation> observations = historicalObservationMapper.toObservations(trainNumber, data);
            historicalObservationRecorder.recordAll(observations);
        } catch (RuntimeException e) {
            log.warn("Could not record historical observations for train {}: {}", trainNumber, e.getMessage());
        }
    }
}
