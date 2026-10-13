package com.director.lighting;

import java.util.List;

/** Main-thread batch gate: keep only the latest request while terrain is being built. */
public final class LightTerrainRefresh {
    private final LightBuildSnapshots builds;
    private volatile List<PointLight> requested = List.of();
    private boolean renderRequired;
    private boolean terrainChanged;
    public LightTerrainRefresh(LightBuildSnapshots builds) { this.builds = builds; }
    public List<PointLight> requested() { return requested; }
    public void request(List<PointLight> lights) { requested = List.copyOf(lights); }
    public void reset() { builds.reset(); requested = List.of(); renderRequired = false; terrainChanged = false; }
    public void rendered() { renderRequired = false; }
    public void requireRender() { renderRequired = true; terrainChanged = true; }
    public boolean pending() { return !requested.equals(builds.lights()); }
    public boolean hasTerrainWork() { return pending() || renderRequired || terrainChanged; }
    public boolean awaiting(boolean settled) {
        if (settled && !renderRequired) terrainChanged = false;
        return renderRequired || pending() || terrainChanged;
    }
    /** null means held; an empty list means published without any lighting changes. */
    public List<PointLightChanges.Bounds> publish(boolean settled) {
        if (renderRequired || requested.equals(builds.lights()) || !settled) return null;
        var previous = builds.lights();
        if (!builds.publish(requested)) return null;
        return PointLightChanges.affected(previous, requested);
    }
}
