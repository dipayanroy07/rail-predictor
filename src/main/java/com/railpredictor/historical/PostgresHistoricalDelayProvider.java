package com.railpredictor.historical;

import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.HistoricalDelay;
import com.railpredictor.model.domain.HistoricalDelayProfile;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.repository.HistoricalDelayProfileEntity;
import com.railpredictor.repository.HistoricalDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalDelayProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The {@link HistoricalDelayProvider} backed by {@link HistoricalDelayProfileRepository} - reads
 * the profile for {@code section.toStation()} (see {@code HistoricalDelayProfile}'s Javadoc on
 * why the lookup is station-, not section-, keyed) and adapts it into the existing
 * {@link HistoricalDelay} shape. No interface change was needed: {@code getHistoricalDelay}
 * already took a {@link RouteSection}, which already carries the station this profile is keyed
 * on.
 *
 * <p>Only ever created when {@code historical.provider=postgres} is explicitly set (see
 * application.properties/docs/configuration.md) - unlike most components in this codebase, its
 * {@link HistoricalDelayProfileRepository} dependency is <b>not</b> optional. If someone asks for
 * the postgres provider without the "postgres" Spring profile (and therefore the repository bean)
 * also being active, Spring fails to start the application rather than silently falling back to
 * mock data - exactly the "no silent fallback" behaviour required (see docs/historical-data-
 * design.md's provider-selection rules).
 */
@Component
@ConditionalOnProperty(name = "historical.provider", havingValue = "postgres")
public class PostgresHistoricalDelayProvider implements HistoricalDelayProvider {

    private final HistoricalDelayProfileRepository profileRepository;
    private final HistoricalDelayProfileEntityMapper profileEntityMapper;
    private final Clock clock;

    public PostgresHistoricalDelayProvider(
            HistoricalDelayProfileRepository profileRepository,
            HistoricalDelayProfileEntityMapper profileEntityMapper,
            Clock clock) {
        this.profileRepository = profileRepository;
        this.profileEntityMapper = profileEntityMapper;
        this.clock = clock;
    }

    @Override
    public HistoricalDelay getHistoricalDelay(String trainNumber, RouteSection section) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(section, "section");

        LocalDate today = LocalDate.now(clock);
        Optional<HistoricalDelayProfileEntity> entity =
                profileRepository.findByTrainNumberAndStationCode(trainNumber, section.toStation().code());

        if (entity.isEmpty()) {
            return new HistoricalDelay(
                    trainNumber, section, today.getDayOfWeek(), today.getMonth(), null,
                    0.0, 0.0, 0.0, 0, DataProvenance.UNAVAILABLE);
        }

        HistoricalDelayProfile profile = profileEntityMapper.toDomain(entity.get());
        return new HistoricalDelay(
                trainNumber,
                section,
                today.getDayOfWeek(),
                today.getMonth(),
                null,
                clampToNonNegative(profile.averageArrivalDelayMinutes()),
                clampToNonNegative(profile.medianArrivalDelayMinutes()),
                profile.standardDeviationMinutes(),
                profile.sampleCount(),
                profile.source());
    }

    /**
     * {@link HistoricalDelay} (Phase 2) requires non-negative average/median delay - a
     * constraint that predates any real (possibly-negative, early-running) historical data.
     * Clamping here, at this adapter boundary, avoids loosening that domain constraint (and by
     * extension {@code HistoricalDelayCalculator}/{@code PredictionEngine}, both unchanged by
     * this phase) just for this one caller - mirrors {@code LiveTrainDataMapper}'s identical
     * clamp for {@code currentDelayMinutes}. {@link HistoricalDelayProfile} itself keeps the
     * unclamped, honest value.
     */
    private static double clampToNonNegative(double value) {
        return Math.max(0.0, value);
    }
}
