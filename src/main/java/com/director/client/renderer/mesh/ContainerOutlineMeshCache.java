package com.director.client.renderer.mesh;

import com.director.core.container.Container;

/** Avoids reading or hashing every position when only container metadata has changed. */
public final class ContainerOutlineMeshCache {
    private final BlockOutlineMeshCache builder = new BlockOutlineMeshCache();
    private Container container;
    private long revision = -1;
    private BlockOutlineMeshCache.Mesh mesh;

    public BlockOutlineMeshCache.Mesh getOrCreateMesh(Container source) {
        long current = source.getGeometryRevision();
        if (container == source && revision == current && mesh != null) return mesh;
        // The position revision is authoritative; a hash collision must not reuse stale geometry.
        builder.clear();
        mesh = builder.getOrCreateMesh(source.getBlocksView());
        container = source;
        revision = current;
        return mesh;
    }

    public void clear() {
        container = null;
        revision = -1;
        mesh = null;
        builder.clear();
    }
}
