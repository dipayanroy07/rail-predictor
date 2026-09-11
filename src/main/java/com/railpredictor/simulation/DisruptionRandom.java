package com.railpredictor.simulation;

import com.railpredictor.model.enums.DisruptionType;
import java.util.Random;

/**
 * Derives a per-disruption-type {@link Random} from {@code SimulationContext.randomSeed()}, so
 * every model gets its own independent random stream from one shared seed (rather than every
 * model rolling the exact same sequence), while the same (seed, type) pair always yields the same
 * stream - required for reproducible simulations, never {@link Math#random()}.
 */
final class DisruptionRandom {

    private DisruptionRandom() {
    }

    static Random forModel(long baseSeed, DisruptionType type) {
        return new Random(baseSeed ^ type.name().hashCode());
    }
}
