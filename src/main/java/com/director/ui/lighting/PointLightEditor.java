package com.director.ui.lighting;

import com.director.core.DirectorServices;
import com.director.lighting.PointLight;
import com.director.lighting.PointLightRuntime;
import com.director.timeline.model.TimelineModel;
import com.director.ui.properties.PropertyContextService;
import com.director.ui.selection.SelectionType;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import java.util.UUID;

/** All edits share the timeline undo stack and the scene persistence transaction. */
public final class PointLightEditor {
    private PointLightEditor() {}
    public static TimelineModel model() { return DirectorServices.get().containerTimeline().getTimelineModel(); }
    public static PointLight selected() {
        var selection=DirectorServices.get().uiSelection();
        return selection.getSelectionType()==SelectionType.LIGHT && selection.getSelectionPayload() instanceof UUID id
                && model()!=null ? model().getPointLight(id) : null;
    }
    public static void select(PointLight light) {
        PointLightParameterEditing.finish();
        DirectorServices.get().uiSelection().setSelection(SelectionType.LIGHT,light.id());
        PropertyContextService.get().onWorldSelectionChanged(light.id());
    }
    public static void change(TimelineModel model, PointLight before, PointLight after) {
        PointLightParameterEditing.finish();
        if (model==null || java.util.Objects.equals(before,after)) return;
        UUID id=after!=null ? after.id() : before.id();
        TimelineCommandBridge.execute(new StateChangeCommand(model,"Edit point light",
                () -> apply(model,id,before), () -> apply(model,id,after)));
    }
    private static void apply(TimelineModel model, UUID id, PointLight light) {
        model.replacePointLight(id,light);
        if(model()==model) PointLightRuntime.update(model);
    }
}
