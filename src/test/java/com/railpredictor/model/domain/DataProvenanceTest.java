package com.railpredictor.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataProvenanceTest {

    @Test
    void aSingleSourcePassesThroughUnchanged() {
        assertThat(DataProvenance.combine(Set.of(DataProvenance.RAILRADAR))).isEqualTo(DataProvenance.RAILRADAR);
    }

    @Test
    void mixedSourcesProduceAnExplicitCompositeLabelRatherThanCollapsingToOne() {
        Set<String> sources = new LinkedHashSet<>();
        sources.add(DataProvenance.RAILRADAR);
        sources.add(DataProvenance.MOCK);

        String combined = DataProvenance.combine(sources);

        assertThat(combined).isNotEqualTo(DataProvenance.RAILRADAR);
        assertThat(combined).isNotEqualTo(DataProvenance.MOCK);
        assertThat(combined).contains(DataProvenance.RAILRADAR).contains(DataProvenance.MOCK).startsWith("mixed(");
    }

    @Test
    void combiningIsOrderIndependent() {
        String first = DataProvenance.combine(new LinkedHashSet<>(Set.of(DataProvenance.RAILRADAR, DataProvenance.MOCK)));
        String second = DataProvenance.combine(new LinkedHashSet<>(Set.of(DataProvenance.MOCK, DataProvenance.RAILRADAR)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void rejectsAnEmptySourceSet() {
        assertThrows(IllegalArgumentException.class, () -> DataProvenance.combine(Set.of()));
    }
}
