package com.railpredictor.model.domain;

/**
 * Which of two fundamentally different processes produced a {@link PredictionSnapshot} (Phase
 * 16H-7). This distinction matters because the two are <b>not</b> equivalent evidence, even though
 * both eventually compare a predicted value against a real observed outcome:
 *
 * <ul>
 *   <li>{@link #LIVE_EVALUATION} - the prediction was made <em>now</em>, by the live
 *       {@code PredictionService} request path, using whatever data was actually available at that
 *       moment (current live train state, current historical profiles, current weather/route
 *       resolution). Its outcome is checked later, once a real observation appears. This is the
 *       <em>only</em> mode any code in this application currently produces -
 *       {@code PredictionSnapshotRecorder} always stamps this.</li>
 *   <li>{@link #HISTORICAL_BACKTEST} - a prediction <em>reconstructed</em> as if it had been made
 *       at some earlier historical timestamp T, using only information that would genuinely have
 *       been available at T (historical profiles rebuilt with a T-cutoff, a train's live
 *       state/route/weather/disruptions as they stood at T). This is a strictly harder guarantee
 *       than "the historical inputs happen to support a cutoff" - it additionally requires
 *       reconstructing {@code LiveTrainData}, route position, and disruption/weather context as of
 *       T, none of which this application currently archives. Phase 16H-7's backtest-readiness
 *       assessment concluded this is <b>not yet possible</b> (see docs/historical-data-design.md's
 *       Phase 16H-7 notes) - this value exists so a future phase that <em>does</em> implement true
 *       historical replay has an explicit, unambiguous way to mark its output, rather than having
 *       it silently mixed in with live-evaluation evidence. No code path in this application
 *       produces this value yet; it is deliberately reserved, not fabricated.</li>
 * </ul>
 *
 * <p>A live evaluation whose historical inputs merely <em>happen</em> to support a point-in-time
 * cutoff (see {@code HistoricalDelayProfileAggregator}/{@code HistoricalSectionDelayProfileAggregator})
 * is still {@link #LIVE_EVALUATION} - cutoff-capable aggregation is a prerequisite for a true
 * backtest, not the same thing as one. Never infer {@link #HISTORICAL_BACKTEST} from the mere
 * presence of a cutoff-aware historical calculation.
 */
public enum PredictionEvaluationMode {

    /** A prediction made by the live request path, evaluated later against a real observation. */
    LIVE_EVALUATION,

    /** A prediction reconstructed as of a historical timestamp, using only inputs that would have
     * been genuinely available at that time. Not yet producible by any code in this application -
     * see this enum's own Javadoc. */
    HISTORICAL_BACKTEST
}
