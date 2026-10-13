package com.director.ui.lighting;

import com.director.lighting.PointLight;
import com.director.lighting.PointLightRuntime;
import com.director.timeline.model.TimelineModel;

/** Shared gesture boundary for panel hiding, selection changes and Gizmo handoff. */
public final class PointLightParameterEditing {
    private static PointLightParameterEdit pending;
    private PointLightParameterEditing() {}

    public static void preview(TimelineModel model, PointLight light) {
        if (pending != null && !pending.belongsTo(model, light.id())) finish();
        if (pending == null) {
            PointLight current = model.getPointLight(light.id());
            if (current == null) return;
            pending = new PointLightParameterEdit(model, current);
        }
        if (pending.preview(light)) PointLightRuntime.update(model);
    }
    public static void finish() {
        PointLightParameterEdit edit = pending;
        pending = null;
        if (edit != null && edit.model() == PointLightEditor.model())
            edit.commit((before, after) -> PointLightEditor.change(edit.model(), before, after));
    }
}
