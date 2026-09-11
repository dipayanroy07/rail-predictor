package com.railpredictor.simulation;

import com.railpredictor.config.CascadeProperties;
import com.railpredictor.model.domain.CascadeEffect;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Simulates secondary delay effects from one train's primary delay: increased section occupancy
 * cascading to a following train, then to the next, and so on, each level's delay decaying by a
 * configured factor from the previous one. This is necessarily a simulation - real network-wide
 * occupancy/headway data (which actual trains are nearby, how they're affected) isn't available -
 * so its output is simulated data, never to be presented as a real railway effect.
 *
 * <p>Modelled as one affected entity per depth level (a linear chain), so
 * {@code maxDepth}/{@code maxAffectedEntities} bound the same loop here; they're kept as separate
 * configuration knobs because a richer future model (e.g. multiple entities affected at the same
 * depth) could make them genuinely different limits.
 */
@Component
public class CascadeEngine {

    private final CascadeProperties properties;

    public CascadeEngine(CascadeProperties properties) {
        this.properties = properties;
    }

    public List<CascadeEffect> cascade(int primaryDelayMinutes) {
        if (primaryDelayMinutes < properties.minimumTriggerDelayMinutes()) {
            return List.of();
        }

        List<CascadeEffect> effects = new ArrayList<>();
        double previousLevelDelay = primaryDelayMinutes;
        int totalCascadeDelay = 0;
        int maxEntities = Math.min(properties.maxDepth(), properties.maxAffectedEntities());

        for (int depth = 1; depth <= maxEntities; depth++) {
            int delayMinutes = (int) Math.round(previousLevelDelay * properties.cascadeFactor());
            if (delayMinutes <= 0) {
                break; // propagated delay has decayed to nothing - no further effect to simulate
            }
            if (totalCascadeDelay + delayMinutes > properties.maxTotalCascadeDelayMinutes()) {
                delayMinutes = properties.maxTotalCascadeDelayMinutes() - totalCascadeDelay;
                if (delayMinutes <= 0) {
                    break;
                }
            }

            effects.add(new CascadeEffect(
                    "Simulated: increased section occupancy propagates delay to affected entity #" + depth,
                    depth,
                    delayMinutes));
            totalCascadeDelay += delayMinutes;
            previousLevelDelay = delayMinutes;

            if (totalCascadeDelay >= properties.maxTotalCascadeDelayMinutes()) {
                break;
            }
        }
        return List.copyOf(effects);
    }
}
