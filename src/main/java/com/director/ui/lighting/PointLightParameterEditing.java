package com.director.ui.lighting;

import com.director.lighting.PointLight;
import com.director.lighting.PointLightRuntime;
import com.director.timeline.model.TimelineModel;

/** Shared gesture boundary for panel hiding, selection changes and Gizmo handoff. */
public final class PointLightParameterEditing {
    private static PointLightParameterEdit pending;
    private static java.util.function.BiConsumer<PointLight,PointLight> record;
    public static void previewKey(TimelineModel model,com.director.lighting.LightTimelineClip clip,com.director.lighting.LightKeyframe key,PointLight value) {
        if(pending!=null && !pending.belongsToKey(model,key)) finish();
        if(pending==null) {
            pending=new PointLightParameterEdit(model,clip,key);
            record=(before,after) -> com.director.ui.timeline.commands.TimelineCommandBridge.execute(
                new com.director.ui.timeline.commands.StateChangeCommand(model,"Edit light keyframe",
                    () -> {key.setValue(before);PointLightRuntime.update(model);},
                    () -> {key.setValue(after);PointLightRuntime.update(model);}));
        }
        if(pending.preview(value)) PointLightRuntime.update(model);
    }
    private PointLightParameterEditing() {}

    public static void preview(TimelineModel model, PointLight light) {
        if (pending != null && !pending.belongsTo(model, light.id())) finish();
        if (pending == null) {
            PointLight current = model.getPointLight(light.id());
            if (current == null) return;
            pending = new PointLightParameterEdit(model, current);
            record=(before,after) -> PointLightEditor.change(model,before,after);
        }
        if (pending.preview(light)) PointLightRuntime.update(model);
    }
    public static void finish() {
        PointLightParameterEdit edit = pending;
        var recorder=record;
        pending = null;record=null;
        if (edit != null && edit.model() == PointLightEditor.model())
            edit.commit(recorder);
    }
}
