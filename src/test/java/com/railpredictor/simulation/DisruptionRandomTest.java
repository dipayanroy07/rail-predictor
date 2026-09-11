package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.railpredictor.model.enums.DisruptionType;
import org.junit.jupiter.api.Test;

class DisruptionRandomTest {

    @Test
    void sameSeedAndTypeProduceTheSameStream() {
        var first = DisruptionRandom.forModel(42L, DisruptionType.HEAVY_RAIN);
        var second = DisruptionRandom.forModel(42L, DisruptionType.HEAVY_RAIN);

        assertThat(first.nextInt(1000)).isEqualTo(second.nextInt(1000));
    }

    @Test
    void differentTypesWithTheSameSeedProduceDifferentStreams() {
        var rain = DisruptionRandom.forModel(42L, DisruptionType.HEAVY_RAIN);
        var fog = DisruptionRandom.forModel(42L, DisruptionType.DENSE_FOG);

        assertThat(rain.nextInt(1_000_000)).isNotEqualTo(fog.nextInt(1_000_000));
    }
}
