package com.director.timeline.playback.block;

import net.minecraft.util.math.BlockPos;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Immutable frame snapshots shared with the server's asynchronous visibility work. */
final class BlockPositionSets {
    private BlockPositionSets() {}

    static Set<BlockPos> immutableCopy(Collection<BlockPos> positions) {
        // Set.copyOf uses linear probing. Dense 3D BlockPos hashes form long clusters,
        // making both construction and each frame's contains calls effectively quadratic.
        // HashSet spreads hashes and treeifies collisions; the wrapper keeps old frames safe.
        return Collections.unmodifiableSet(new HashSet<>(positions));
    }
}
