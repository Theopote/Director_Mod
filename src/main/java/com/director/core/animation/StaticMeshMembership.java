package com.director.core.animation;

import com.director.core.container.visibility.BlockVisualStateManager.RenderEntry;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Exact membership, independent of hash collisions and per-frame RenderEntry identities. */
final class StaticMeshMembership {
    private final Map<Long, Set<BlockPos>> positions = new HashMap<>();
    StaticMeshMembership(Map<Long, List<RenderEntry>> groups) {
        for (var group : groups.entrySet()) {
            var members = new HashSet<BlockPos>();
            for (var entry : group.getValue()) members.add(entry.pos());
            positions.put(group.getKey(), members);
        }
    }
    boolean matches(Map<Long, List<RenderEntry>> groups) {
        if (!positions.keySet().equals(groups.keySet())) return false;
        for (var group : groups.entrySet()) {
            var members = positions.get(group.getKey());
            if (members.size() != group.getValue().size()) return false;
            for (var entry : group.getValue()) if (!members.contains(entry.pos())) return false;
        }
        return true;
    }
}
