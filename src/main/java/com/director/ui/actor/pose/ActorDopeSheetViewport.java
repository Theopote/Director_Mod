package com.director.ui.actor.pose;

import java.util.List;

/** Frozen mapping and row identities for one canvas gesture. */
public record ActorDopeSheetViewport(float x,float y,float timeX,float width,float height,
        float pixelsPerSecond,float startTime,float frameRate,List<?> rows) {
    public ActorDopeSheetViewport { rows=List.copyOf(rows); }
    public boolean valid() {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(timeX)
                && Float.isFinite(width) && width>0 && Float.isFinite(height) && height>0
                && Float.isFinite(pixelsPerSecond) && pixelsPerSecond>0
                && Float.isFinite(startTime) && Float.isFinite(frameRate) && frameRate>0;
    }
    public boolean matches(ActorDopeSheetViewport current) { return valid() && current!=null && current.valid() && equals(current); }
}
