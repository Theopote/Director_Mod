package com.director.ui.actor.edit;

import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.joml.Vector3f;

/** Per-actor editor preference; locking preserves existing proportions. */
public final class ActorScaleEditing {
    private static final Set<UUID> locked = new HashSet<>();
    private ActorScaleEditing() {}
    public static boolean isLocked(UUID actor) { return locked.contains(actor); }
    public static void setLocked(UUID actor, boolean value) {
        if (actor == null) return;
        if (value) locked.add(actor); else locked.remove(actor);
    }
    public static Vector3f proportional(Vector3f base, int axis, float requested) {
        if (!base.isFinite() || base.x <= 0 || base.y <= 0 || base.z <= 0 || !Float.isFinite(requested))
            return new Vector3f(base);
        float factor = requested / base.get(axis);
        float min = .01f / Math.min(base.x, Math.min(base.y, base.z));
        float max = 100f / Math.max(base.x, Math.max(base.y, base.z));
        return new Vector3f(base).mul(Math.max(min, Math.min(max, factor)));
    }
}
