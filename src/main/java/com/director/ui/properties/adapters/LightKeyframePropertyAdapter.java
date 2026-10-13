package com.director.ui.properties.adapters;
import com.director.lighting.*;
import com.director.ui.lighting.*;
import com.director.ui.panels.LightsPanel;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.timeline.commands.*;
import com.director.timeline.model.TimelineInterpolation;
import imgui.ImGui;
import imgui.type.ImInt;
public final class LightKeyframePropertyAdapter implements IPropertyAdapter<LightKeyframe> {
    private final LightParameterControls controls=new LightParameterControls();
    @Override public String getAdapterName() {return LightsPanel.tr("key_properties");}
    @Override public Class<LightKeyframe> getTargetType() {return LightKeyframe.class;}
    @Override public boolean renderProperties(LightKeyframe key) {
        var model=PointLightEditor.model();if(model==null) return false;
        LightTimelineClip owner=null;
        for(var track:model.getTracks()) for(var clip:track.getClips())
            if(clip instanceof LightTimelineClip light && light.getClipKeyframes().contains(key)) owner=light;
        if(owner==null) {PointLightParameterEditing.finish();return false;}
        var clip=owner;var track=model.findTrackForClip(clip);
        boolean locked=track.isLocked() || com.director.export.video.VideoExportManager.getInstance().isExporting()
            || com.director.client.gizmo.GizmoManager.get().isDraggingHandle();
        ImGui.textWrapped(LightsPanel.tr("key_hint"));ImGui.beginDisabled(locked);
        float[] time={key.getTime()};
        if(ImGui.dragFloat(LightsPanel.tr("time"),time,.01f) && Float.isFinite(time[0])) {
            PointLightParameterEditing.finish();
            TimelineCommandBridge.execute(new MoveClipKeyframeCommand(model,track,clip,key,key.getTime(),Math.max(clip.getStartTime(),Math.min(clip.getEndTime(),time[0]))));
        }
        var modes=TimelineInterpolation.values();String[] labels=java.util.Arrays.stream(modes).map(TimelineInterpolation::getDisplayName).toArray(String[]::new);
        var mode=new ImInt(key.getInterpolation().ordinal());
        if(ImGui.combo(LightsPanel.tr("interpolation"),mode,labels)) {
            PointLightParameterEditing.finish();TimelineCommandBridge.execute(new ChangeClipKeyframeInterpolationCommand(model,track,clip,key,modes[mode.get()]));
        }
        ImGui.endDisabled();
        controls.render(key.value(),locked,value -> PointLightParameterEditing.previewKey(model,clip,key,value),PointLightParameterEditing::finish);
        return false;
    }
}
