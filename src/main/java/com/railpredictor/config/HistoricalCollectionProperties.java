package com.railpredictor.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the Phase 22B independent, scheduled real-data collection path
 * ({@code historical.collection.*}) - deliberately separate from
 * {@code prediction.evaluation.*}/{@code historical.profile-refresh.*}, since this phase's own
 * concern is fetching live RailRadar data for the sole purpose of recording historical
 * observations, never predicting or evaluating anything.
 *
 * <p><b>{@code enabled}</b> defaults to {@code false} - this makes real, scheduled RailRadar
 * requests (consuming real API quota) a genuine new side effect that requires an explicit opt-in,
 * exactly like {@code prediction.evaluation.enabled}'s own established convention.
 *
 * <p><b>{@code trainNumbers}</b> is the explicit, operator-configured list of trains to observe -
 * deliberately never auto-discovered, scraped, or algorithmically generated (there is no reliable
 * "list every currently-running train" RailRadar endpoint this codebase has ever verified - see
 * docs/historical-data-design.md's Phase 18 findings on the absence of a trustworthy bulk/discovery
 * source). Empty by default - the collector safely does nothing until an operator names specific
 * trains. Every entry is validated (exactly 5 digits, mirroring the existing
 * {@code PredictionController}/{@code EvaluationController} train-number pattern) and duplicates
 * are silently removed (order-preserving) - a config typo or a repeated entry must never double a
 * train's real polling frequency.
 */
@ConfigurationProperties(prefix = "historical.collection")
public record HistoricalCollectionProperties(
        boolean enabled, long intervalMs, long initialDelayMs, List<String> trainNumbers) {

    private static final Pattern TRAIN_NUMBER_PATTERN = Pattern.compile("\\d{5}");

    public HistoricalCollectionProperties {
        if (intervalMs < 1) {
            throw new IllegalArgumentException("historical.collection.interval-ms must be positive: " + intervalMs);
        }
        if (initialDelayMs < 0) {
            throw new IllegalArgumentException(
                    "historical.collection.initial-delay-ms must not be negative: " + initialDelayMs);
        }
        Objects.requireNonNull(trainNumbers, "trainNumbers");
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String raw : trainNumbers) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) {
                // Tolerates an unset/blank property binding to a single empty-string element,
                // rather than rejecting it as an "invalid train number".
                continue;
            }
            if (!TRAIN_NUMBER_PATTERN.matcher(trimmed).matches()) {
                throw new IllegalArgumentException(
                        "historical.collection.train-numbers must contain only 5-digit train numbers "
                                + "(e.g. 12952) - found: '" + trimmed + "'");
            }
            deduplicated.add(trimmed);
        }
        trainNumbers = List.copyOf(deduplicated);
    }
}
