package com.director.ui.properties.adapters;

import com.director.actor.*;
import com.director.actor.action.ActorActionType;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.ActorActionClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;

public class ActorActionClipPropertyAdapter implements IPropertyAdapter<ActorActionClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    @Override public Class<ActorActionClip> getTargetType() { return ActorActionClip.class; }
    @Override public boolean renderProperties(ActorActionClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof ActorActionClip clip)) { gesture.end(); return false; }
        var payload = source.getTimelineClip().getParentTrack().getActorTrackPayload();
        Actor actor = ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        UITheme.title(Icons.SMILE + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.TITLE));
        if (ImGui.collapsingHeader(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_SEMANTICS), ImGuiTreeNodeFlags.DefaultOpen)) {
            if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.ACTION_TYPE)
                    + "##actorActionType", ActorPropertyAdapterI18n.actionSemanticLabel(clip.getActionType()))) {
                for (var type : ActorActionType.values()) {
                    boolean supported = ActorBehaviorCapabilities.supportsAction(actor, type);
                    ImGui.beginDisabled(!supported);
                    String label = ActorPropertyAdapterI18n.actionSemanticLabel(type) + (supported ? ""
                            : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.NOT_SUPPORTED_SUFFIX));
                    if (ImGui.selectable(label, type == clip.getActionType())) {
                        clip.setActionType(type); gesture.requestCommit();
                    }
                    ImGui.endDisabled();
                    if (!supported && ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled))
                        ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.NOT_SUPPORTED_TOOLTIP));
                }
                ImGui.endCombo();
            }
            if (!ActorBehaviorCapabilities.supportsAction(actor, clip.getActionType()))
                ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.RIG_WARNING_HINT));
        }
        if (ImGui.collapsingHeader(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_INTENSITY), ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] intensity = {clip.getIntensity()};
            boolean changed = ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY)
                    + "##actorActionIntensity", intensity, 0, 1, "%.2f");
            if (changed) clip.setIntensity(intensity[0]);
            gesture.observe("intensity", changed);
            ImGui.textWrapped("此处设置基础动作语义，生效范围由动作片段决定。挥手、攻击等骨骼表演请在演员片段的“演员动作预设”中设置。");
            var playback=new com.director.timeline.playback.entity.EntityClipSampler(payload)
                    .sampleSnapshot(com.director.core.DirectorServices.get().containerTimeline().getTimelineModel().getCurrentTime());
            boolean locomotion=clip.getActionType()==com.director.actor.action.ActorActionType.WALK
                    || clip.getActionType()==com.director.actor.action.ActorActionType.RUN;
            if(locomotion) ImGui.textWrapped(playback.routeClip!=null
                    ?"沿路径走跑：片段只指定动作类型和幅度，步频由路径行进距离决定。"
                    :"原地走跑：只改变动作，不产生位移；需要移动时请绑定路径或设置整体位置关键帧。");
        }
        if (ImGui.collapsingHeader(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_TIME_EDIT), ImGuiTreeNodeFlags.DefaultOpen)) {
            var start = new ImFloat(clip.getStartTime()); var end = new ImFloat(clip.getEndTime());
            boolean changed = ImGui.inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_TIME)
                    + "##actorActionStart", start);
            if (changed) {
                if (Float.isFinite(start.get()) && start.get() >= 0 && clip.getEndTime() - start.get() >= .1f - 1e-5f)
                    clip.setTimeRange(start.get(), clip.getEndTime());
                else gesture.report(ActorPropertyAdapterI18n.tr("gui.director.actor.behavior.invalid_time"));
            }
            gesture.observe("start", changed);
            changed = ImGui.inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_TIME)
                    + "##actorActionEnd", end);
            if (changed) {
                if (Float.isFinite(end.get()) && end.get() - clip.getStartTime() >= .1f - 1e-5f) clip.setTimeRange(clip.getStartTime(), end.get());
                else gesture.report(ActorPropertyAdapterI18n.tr("gui.director.actor.behavior.invalid_time"));
            }
            gesture.observe("end", changed);
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
        }
        gesture.end(); return false;
    }
}
