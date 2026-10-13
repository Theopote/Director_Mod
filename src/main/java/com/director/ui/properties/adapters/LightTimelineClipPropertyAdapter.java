package com.director.ui.properties.adapters;
import com.director.lighting.*;
import com.director.ui.lighting.*;
import com.director.ui.panels.LightsPanel;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.timeline.commands.*;
import com.director.ui.selection.SelectionType;
import imgui.ImGui;
/** Clip interval plus scene defaults; keyframes have their own property target. */
public final class LightTimelineClipPropertyAdapter implements IPropertyAdapter<LightTimelineClip> {
    private final PointLightPropertyAdapter baseProperties=new PointLightPropertyAdapter();
    @Override public String getAdapterName() {return LightsPanel.tr("clip_properties");}
    @Override public Class<LightTimelineClip> getTargetType() {return LightTimelineClip.class;}
    @Override public boolean renderProperties(LightTimelineClip clip) {
        var model=PointLightEditor.model();if(model==null) return false;
        var track=model.findTrackForClip(clip);if(!LightLifecycle.containsClip(model,clip)) return false;
        var base=model.getPointLight(clip.lightId());if(base==null) return false;
        boolean locked=track.isLocked() || com.director.export.video.VideoExportManager.getInstance().isExporting();
        ImGui.beginDisabled(locked);
        float[] start={clip.getStartTime()},end={clip.getEndTime()};
        boolean changed=ImGui.dragFloat(LightsPanel.tr("start"),start,.01f);
        changed |= ImGui.dragFloat(LightsPanel.tr("end"),end,.01f);
        if(changed && Float.isFinite(start[0]) && Float.isFinite(end[0]) && start[0]>=0 && end[0]>=start[0]+.05f) {
            PointLightParameterEditing.finish();
            var before=clip.captureState();
            float newStart=start[0],newEnd=end[0];
            clip.setTimeRange(newStart,newEnd);var after=clip.captureState();
            TimelineCommandBridge.execute(new StateChangeCommand(model,"Resize light clip",
                () -> {clip.restoreState(before);PointLightRuntime.update(model);},
                () -> {clip.restoreState(after);PointLightRuntime.update(model);}));
        }
        float time=model.getCurrentTime();
        ImGui.beginDisabled(time<clip.getStartTime() || time>clip.getEndTime());
        if(ImGui.button(LightsPanel.tr("add_key"))) {
            PointLightParameterEditing.finish();
            var key=clip.findKeyframeAt(time,1e-4f);
            if(key==null) {key=new LightKeyframe(time,clip.sample(base,time));TimelineCommandBridge.execute(new AddClipKeyframeCommand(model,track,clip,key));}
            com.director.core.DirectorServices.get().uiSelection().setSelection(SelectionType.KEYFRAME,key);
        }
        ImGui.endDisabled();ImGui.endDisabled();
        ImGui.separator();ImGui.textWrapped(LightsPanel.tr("base_hint"));
        ImGui.beginDisabled(locked);baseProperties.renderProperties(base);ImGui.endDisabled();
        return false;
    }
}
