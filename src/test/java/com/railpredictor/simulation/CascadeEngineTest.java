package com.railpredictor.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railpredictor.config.CascadeProperties;
import com.railpredictor.model.domain.CascadeEffect;
import java.util.List;
import org.junit.jupiter.api.Test;

class CascadeEngineTest {

    @Test
    void belowTriggerThresholdCascadesNothing() {
        CascadeEngine engine = new CascadeEngine(new CascadeProperties(10, 0.3, 3, 30, 3));

        assertThat(engine.cascade(9)).isEmpty();
    }

    @Test
    void atOrAboveThresholdProducesADecayingChain() {
        // 50 -> depth1: round(50*0.3)=15, depth2: round(15*0.3)=5 (4.5 rounds up), depth3: round(5*0.3)=2
        CascadeEngine engine = new CascadeEngine(new CascadeProperties(10, 0.3, 3, 30, 3));

        List<CascadeEffect> effects = engine.cascade(50);

        assertThat(effects).hasSize(3);
        assertThat(effects.get(0).depth()).isEqualTo(1);
        assertThat(effects.get(0).additionalDelayMinutes()).isEqualTo(15);
        assertThat(effects.get(1).depth()).isEqualTo(2);
        assertThat(effects.get(1).additionalDelayMinutes()).isEqualTo(5);
        assertThat(effects.get(2).depth()).isEqualTo(3);
        assertThat(effects.get(2).additionalDelayMinutes()).isEqualTo(2);
    }

    @Test
    void stopsAtMaxDepthEvenIfDelayIsStillMeaningful() {
        CascadeEngine engine = new CascadeEngine(new CascadeProperties(0, 0.9, 1, 1000, 1));

        List<CascadeEffect> effects = engine.cascade(100);

        assertThat(effects).hasSize(1);
    }

    @Test
    void clampsTheLastEffectToStayWithinTheTotalCascadeDelayCap() {
        // depth1 would be round(100*0.5)=50, but the cap is 30.
        CascadeEngine engine = new CascadeEngine(new CascadeProperties(0, 0.5, 5, 30, 5));

        List<CascadeEffect> effects = engine.cascade(100);

        assertThat(effects).hasSize(1);
        assertThat(effects.get(0).additionalDelayMinutes()).isEqualTo(30);
    }

    @Test
    void zeroCascadeFactorProducesNoEffects() {
        CascadeEngine engine = new CascadeEngine(new CascadeProperties(0, 0.0, 3, 30, 3));

        assertThat(engine.cascade(1000)).isEmpty();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new CascadeProperties(-1, 0.3, 3, 30, 3));
        assertThrows(IllegalArgumentException.class, () -> new CascadeProperties(0, 1.1, 3, 30, 3));
        assertThrows(IllegalArgumentException.class, () -> new CascadeProperties(0, 0.3, 0, 30, 3));
        assertThrows(IllegalArgumentException.class, () -> new CascadeProperties(0, 0.3, 3, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new CascadeProperties(0, 0.3, 3, 30, 0));
    }
}
