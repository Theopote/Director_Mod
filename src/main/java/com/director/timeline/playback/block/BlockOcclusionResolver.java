package com.director.timeline.playback.block;

import com.director.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.lang.ref.WeakReference;
import java.util.*;

/** Container-level fading uses existence of a position inside the target bounds. */
public class BlockOcclusionResolver {
    private static final int MAX_CACHED_PAIRS = 1024;
    private record Pair(UUID target, UUID candidate) {}
    private record Hit(WeakReference<Container> target, WeakReference<Container> candidate, long targetRevision,
                       long candidateRevision, boolean occluding) {
        boolean matches(Container a, Container b) {
            return target.get() == a && candidate.get() == b && targetRevision == a.getGeometryRevision()
                    && candidateRevision == b.getGeometryRevision();
        }
    }

    // One bounded entry per pair, rather than a new large position set per version.
    private final Map<Pair, Hit> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Pair, Hit> eldest) {
            return size() > MAX_CACHED_PAIRS;
        }
    };

    public Set<UUID> resolveOccludingContainers(Container target, Collection<Container> candidates) {
        Set<UUID> result = new HashSet<>();
        if (target == null || candidates == null || candidates.isEmpty()) return result;
        Container.BoundingBox targetBox = target.getBoundingBox();
        if (targetBox == null) return result;
        for (Container candidate : candidates) {
            if (candidate == null || candidate.getId() == null) continue;
            Pair pair = new Pair(target.getId(), candidate.getId());
            Hit hit = cache.get(pair);
            if (hit == null || !hit.matches(target, candidate)) {
                boolean occluding = intersectsPositions(targetBox, candidate);
                hit = new Hit(new WeakReference<>(target), new WeakReference<>(candidate), target.getGeometryRevision(),
                        candidate.getGeometryRevision(), occluding);
                cache.put(pair, hit);
            }
            if (hit.occluding()) result.add(candidate.getId());
        }
        return result;
    }

    private boolean intersectsPositions(Container.BoundingBox target, Container candidate) {
        Container.BoundingBox box = candidate.getBoundingBox();
        if (box == null || !boxesIntersect(target, box)) return false;
        for (BlockPos pos : candidate.getBlocksView()) {
            if (target.contains(pos)) return true;
        }
        return false;
    }

    private boolean boxesIntersect(Container.BoundingBox a, Container.BoundingBox b) {
        return !(a.getMax().getX() < b.getMin().getX() || b.getMax().getX() < a.getMin().getX()
                || a.getMax().getY() < b.getMin().getY() || b.getMax().getY() < a.getMin().getY()
                || a.getMax().getZ() < b.getMin().getZ() || b.getMax().getZ() < a.getMin().getZ());
    }
}
