package com.railpredictor.historical;

import com.railpredictor.config.HistoricalSectionProperties;
import com.railpredictor.model.domain.HistoricalObservation;
import com.railpredictor.model.domain.HistoricalSectionDelayProfile;
import com.railpredictor.model.domain.RouteSection;
import com.railpredictor.model.domain.SectionHistoricalDelayResult;
import com.railpredictor.model.domain.SectionHistoricalDelayStatus;
import com.railpredictor.repository.HistoricalObservationEntityMapper;
import com.railpredictor.repository.HistoricalObservationRepository;
import com.railpredictor.repository.HistoricalSectionDelayProfileEntityMapper;
import com.railpredictor.repository.HistoricalSectionDelayProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The {@link HistoricalSectionDelayProvider} backed by real persistence - only ever created when
 * {@code historical.section-provider=postgres} is explicitly set (see docs/configuration.md);
 * like {@link PostgresHistoricalDelayProvider}, its repository dependencies are <b>not</b>
 * {@code Optional} - asking for this provider without the "postgres" Spring profile also active
 * fails application startup outright, rather than silently falling back to mock data.
 *
 * <p><b>The central Phase 16G design question: can the materialized
 * {@code historical_section_delay_profiles} table answer a point-in-time request?</b> Only
 * sometimes - and this class is explicit about exactly when:
 *
 * <ul>
 *   <li><b>{@code referenceInstant} is "now" or later</b> (the overwhelmingly common case - a live
 *       prediction wanting "the best currently-known data"): the cached, materialized profile is
 *       read directly. This is safe because the cache is refreshed on a schedule using every
 *       observation that exists <em>at refresh time</em>, and no observation can ever have an
 *       {@code observedAt} later than genuine wall-clock "now" - so reading the cache for a
 *       "now-or-later" request can, at worst, be slightly <em>stale</em> (missing something
 *       recorded since the last refresh), never <em>leaky</em> (it can never include something
 *       that shouldn't yet be visible).</li>
 *   <li><b>{@code referenceInstant} is genuinely in the past</b> (a backtesting/point-in-time
 *       request - "what did we know as of this earlier moment"): the cache is <b>never</b> trusted
 *       for this, even if a cached row happens to exist. The cached row's {@code computedAt} only
 *       records when it was last (re)computed, not which raw observations went into it beyond
 *       "everything that existed by then" - and because the table is overwritten in place with no
 *       version history (Phase 16F's own design, deliberately not versioned), there is no way to
 *       recover "what this profile looked like as of an earlier moment" from the cache alone. So
 *       for any {@code referenceInstant} strictly before "now", this provider instead re-loads
 *       every raw observation for the train and re-runs
 *       {@link HistoricalSectionDelayProfileAggregator#aggregate} with that exact cutoff - the
 *       same aggregator Phase 16F already built and tested for this, just invoked directly instead
 *       of through the cache. This is slower (no index-only lookup) but is the only honest way to
 *       answer the question without introducing profile versioning - a larger schema change this
 *       phase avoids since the existing aggregator already solves it correctly on demand.</li>
 * </ul>
 *
 * <p>Never throws for the ordinary "no historical data for this section" case - returns a
 * {@link SectionHistoricalDelayStatus#NOT_FOUND} result instead, exactly like
 * {@link PostgresHistoricalDelayProvider}'s own "no data" convention.
 */
@Component
@ConditionalOnProperty(name = "historical.section-provider", havingValue = "postgres")
public class PostgresSectionHistoricalDelayProvider implements HistoricalSectionDelayProvider {

    private final HistoricalSectionDelayProfileRepository profileRepository;
    private final HistoricalSectionDelayProfileEntityMapper profileEntityMapper;
    private final HistoricalObservationRepository observationRepository;
    private final HistoricalObservationEntityMapper observationEntityMapper;
    private final HistoricalSectionDelayProfileAggregator aggregator;
    private final HistoricalSectionProperties sectionProperties;
    private final Clock clock;

    public PostgresSectionHistoricalDelayProvider(
            HistoricalSectionDelayProfileRepository profileRepository,
            HistoricalSectionDelayProfileEntityMapper profileEntityMapper,
            HistoricalObservationRepository observationRepository,
            HistoricalObservationEntityMapper observationEntityMapper,
            HistoricalSectionDelayProfileAggregator aggregator,
            HistoricalSectionProperties sectionProperties,
            Clock clock) {
        this.profileRepository = profileRepository;
        this.profileEntityMapper = profileEntityMapper;
        this.observationRepository = observationRepository;
        this.observationEntityMapper = observationEntityMapper;
        this.aggregator = aggregator;
        this.sectionProperties = sectionProperties;
        this.clock = clock;
    }

    @Override
    public SectionHistoricalDelayResult getSectionDelay(String trainNumber, RouteSection section, Instant referenceInstant) {
        Objects.requireNonNull(trainNumber, "trainNumber");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(referenceInstant, "referenceInstant");

        String fromStationCode = section.fromStation().code();
        String toStationCode = section.toStation().code();

        Optional<HistoricalSectionDelayProfile> profile = referenceInstant.isBefore(Instant.now(clock))
                ? aggregateOnDemand(trainNumber, fromStationCode, toStationCode, referenceInstant)
                : fromCache(trainNumber, fromStationCode, toStationCode);

        if (profile.isEmpty()) {
            return new SectionHistoricalDelayResult(
                    trainNumber, fromStationCode, toStationCode, SectionHistoricalDelayStatus.NOT_FOUND, null);
        }

        SectionHistoricalDelayStatus status = profile.get().sampleCount() >= sectionProperties.minimumSampleCount()
                ? SectionHistoricalDelayStatus.AVAILABLE
                : SectionHistoricalDelayStatus.INSUFFICIENT_SAMPLES;

        return new SectionHistoricalDelayResult(trainNumber, fromStationCode, toStationCode, status, profile.get());
    }

    /** The fast path: a single indexed lookup against the materialized cache - only safe for a
     * "now or later" {@code referenceInstant}, see this class's own Javadoc. */
    private Optional<HistoricalSectionDelayProfile> fromCache(String trainNumber, String fromStationCode, String toStationCode) {
        return profileRepository
                .findByTrainNumberAndFromStationCodeAndToStationCode(trainNumber, fromStationCode, toStationCode)
                .map(profileEntityMapper::toDomain);
    }

    /** The correct-but-slower path for a genuinely retrospective {@code referenceInstant}: never
     * trusts the cache, always re-derives directly from raw observations with the exact requested
     * cutoff. */
    private Optional<HistoricalSectionDelayProfile> aggregateOnDemand(
            String trainNumber, String fromStationCode, String toStationCode, Instant referenceInstant) {
        List<HistoricalObservation> observations = observationRepository.findByTrainNumber(trainNumber).stream()
                .map(observationEntityMapper::toDomain)
                .toList();
        return aggregator.aggregate(trainNumber, observations, referenceInstant).stream()
                .filter(p -> p.fromStationCode().equals(fromStationCode) && p.toStationCode().equals(toStationCode))
                .findFirst();
    }
}
