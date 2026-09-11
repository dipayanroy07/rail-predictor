package com.railpredictor.model.domain;

/**
 * The result of a {@code HistoricalSectionDelayProvider} lookup (Phase 16G) - deliberately richer
 * than a bare {@link HistoricalSectionDelayProfile} or a nullable/{@code Optional} return, so a
 * caller can distinguish {@link SectionHistoricalDelayStatus#AVAILABLE},
 * {@link SectionHistoricalDelayStatus#INSUFFICIENT_SAMPLES}, and
 * {@link SectionHistoricalDelayStatus#NOT_FOUND} rather than receiving one ambiguous "no data"
 * signal for all three.
 *
 * <p>{@code profile} is {@code null} only when {@code status} is {@code NOT_FOUND} - otherwise
 * (whether the sample count is sufficient or not) the full, honest profile is always attached,
 * including its own {@code source()} provenance - never hidden. This provider makes no
 * provenance-based acceptability decision (e.g. rejecting a mock- or mixed-sourced profile
 * outright); that policy is left to whichever future phase actually consumes this result for
 * prediction (see docs/historical-data-design.md's Phase 16G notes).
 *
 * <p>{@code trainNumber}/{@code fromStationCode}/{@code toStationCode} are always present (even
 * for {@code NOT_FOUND}, where they are the only way to know which section was asked about) - they
 * are redundant with {@code profile}'s own identity fields when a profile is present, kept for a
 * uniform, self-describing result regardless of status.
 */
public record SectionHistoricalDelayResult(
        String trainNumber,
        String fromStationCode,
        String toStationCode,
        SectionHistoricalDelayStatus status,
        HistoricalSectionDelayProfile profile) {

    public SectionHistoricalDelayResult {
        trainNumber = Guard.requireNonBlank(trainNumber, "trainNumber");
        fromStationCode = Guard.requireNonBlank(fromStationCode, "fromStationCode");
        toStationCode = Guard.requireNonBlank(toStationCode, "toStationCode");
        status = Guard.requireNonNull(status, "status");
        if (status == SectionHistoricalDelayStatus.NOT_FOUND && profile != null) {
            throw new IllegalArgumentException("profile must be null when status is NOT_FOUND");
        }
        if (status != SectionHistoricalDelayStatus.NOT_FOUND && profile == null) {
            throw new IllegalArgumentException("profile must not be null unless status is NOT_FOUND");
        }
    }
}
