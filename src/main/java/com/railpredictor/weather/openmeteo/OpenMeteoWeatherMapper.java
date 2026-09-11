package com.railpredictor.weather.openmeteo;

import com.railpredictor.config.OpenMeteoProperties;
import com.railpredictor.exception.WeatherUnavailableException;
import com.railpredictor.model.domain.DataProvenance;
import com.railpredictor.model.domain.WeatherData;
import com.railpredictor.model.enums.WeatherCondition;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentConditions;
import com.railpredictor.weather.openmeteo.dto.OpenMeteoCurrentWeatherResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Translates Open-Meteo's raw {@link OpenMeteoCurrentWeatherResponse} into the internal,
 * framework-free {@link WeatherData} - Open-Meteo DTOs must never be visible outside the {@code
 * weather.openmeteo} package, mirroring {@code LiveTrainDataMapper}'s own boundary rule.
 *
 * <p><b>Units require no conversion.</b> Open-Meteo's default units already match this
 * application's domain units exactly: {@code temperature_2m} in {@code °C}, {@code precipitation}
 * in {@code mm}, {@code visibility} in meters - the same units {@link WeatherData} has always used
 * (see {@code WeatherMockProperties}). No scaling/conversion math exists in this class because none
 * is needed; this is a deliberate, verified fact (see this class's own tests), not an oversight.
 *
 * <p><b>Classifying into {@link WeatherCondition}</b> (a capability that did not exist anywhere in
 * this codebase before Phase 17 - {@code MockWeatherProvider} only ever echoes a pre-configured
 * enum, never derives one): primarily from Open-Meteo's own WMO weather code, an international
 * standard (not an invented threshold) - see {@link #classify}. The one genuinely new number this
 * phase introduces is {@code weather.openmeteo.dense-fog-visibility-meters} (default 200m): the WMO
 * code has a single "fog" value with no light/dense distinction, so only {@code visibility} can
 * supply that severity split - 200m mirrors the India Meteorological Department's own "dense fog"
 * definition (visibility 51-200m), a documented, non-arbitrary convention (see
 * docs/prediction-model.md's Phase 17 notes). {@code HeavyRainModel}/{@code DenseFogModel}
 * themselves are unchanged - they still only ever read {@code WeatherData.condition()}.
 *
 * <p><b>{@code observedAt}</b> is parsed from {@code current.time} - Open-Meteo's own genuine
 * observation/model-valid timestamp, always requested in UTC ({@code OpenMeteoClient} passes
 * {@code timezone=UTC}), so the returned local-time string (no offset) is interpreted as UTC here.
 */
@Component
class OpenMeteoWeatherMapper {

    /** WMO codes 51,53,55 (drizzle), 61,63 (rain), 56,57,66,67 (freezing drizzle/rain), 80,81
     * (rain showers) - light-to-moderate rain, not (yet) heavy. */
    private static final Set<Integer> RAIN_CODES = Set.of(51, 53, 55, 56, 57, 61, 63, 66, 67, 80, 81);

    /** WMO codes 65 (heavy rain), 82 (violent rain showers), 95, 96, 99 (thunderstorm, with/without
     * hail) - rain severe enough to be treated as certain heavy rain, mirroring how
     * {@code HeavyRainModel} already treats a {@code HEAVY_RAIN} reading as certain rather than
     * probabilistic. */
    private static final Set<Integer> HEAVY_RAIN_CODES = Set.of(65, 82, 95, 96, 99);

    /** WMO codes 45 (fog), 48 (depositing rime fog) - severity (FOG vs. DENSE_FOG) is decided by
     * {@code visibility}, since the WMO code itself carries no such distinction. */
    private static final Set<Integer> FOG_CODES = Set.of(45, 48);

    /** WMO codes 0, 1 (clear, mainly clear). Partly-cloudy/overcast (2, 3) and every snow code are
     * deliberately left to fall through to {@link WeatherCondition#UNKNOWN} below - this enum has
     * no CLOUDY or SNOW value, and inventing one is out of this phase's scope (see
     * docs/prediction-model.md's Phase 17 notes on this limitation). */
    private static final Set<Integer> CLEAR_CODES = Set.of(0, 1);

    private final OpenMeteoProperties properties;

    OpenMeteoWeatherMapper(OpenMeteoProperties properties) {
        this.properties = properties;
    }

    WeatherData toWeatherData(OpenMeteoCurrentWeatherResponse response) {
        Objects.requireNonNull(response, "response");
        OpenMeteoCurrentConditions current = response.current();
        if (current == null) {
            throw new WeatherUnavailableException("Open-Meteo response has no 'current' block");
        }

        WeatherCondition condition = classify(current.weatherCode(), current.visibility());
        Instant observedAt = parseObservedAt(current.time());

        return new WeatherData(
                condition,
                current.temperature2m(),
                current.visibility(),
                current.precipitation(),
                DataProvenance.OPENMETEO,
                observedAt);
    }

    private WeatherCondition classify(Integer weatherCode, Double visibilityMeters) {
        if (weatherCode == null) {
            return WeatherCondition.UNKNOWN;
        }
        if (HEAVY_RAIN_CODES.contains(weatherCode)) {
            return WeatherCondition.HEAVY_RAIN;
        }
        if (RAIN_CODES.contains(weatherCode)) {
            return WeatherCondition.RAIN;
        }
        if (FOG_CODES.contains(weatherCode)) {
            if (visibilityMeters != null && visibilityMeters <= properties.denseFogVisibilityMeters()) {
                return WeatherCondition.DENSE_FOG;
            }
            // No visibility figure to judge severity by - the conservative (non-certain) FOG
            // classification, never assumed dense without evidence.
            return WeatherCondition.FOG;
        }
        if (CLEAR_CODES.contains(weatherCode)) {
            return WeatherCondition.CLEAR;
        }
        return WeatherCondition.UNKNOWN;
    }

    /** {@code time} is Open-Meteo's local-time-with-no-offset string (e.g.
     * {@code "2026-09-10T18:00"}) - safe to interpret as UTC only because {@link OpenMeteoClient}
     * always requests {@code timezone=UTC}. {@code null}/unparseable never fabricates a timestamp -
     * it simply leaves {@code observedAt} absent, exactly like every other "don't know" case in
     * this codebase. */
    private static Instant parseObservedAt(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
