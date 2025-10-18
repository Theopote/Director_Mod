package com.chronoblocks.core.container;

import java.util.HashSet;
import java.util.Set;

/**
 * BlockContainer - Represents a group of blocks to be animated together.
 */
public class BlockContainer {
    private final String id;
    private final Set<Long> blockPositions = new HashSet<>(); // packed block pos (x,y,z)

    public BlockContainer(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public Set<Long> getBlockPositions() { return blockPositions; }
}
