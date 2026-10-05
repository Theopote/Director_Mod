package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineModel;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** Timeline-hosted animation detail view; shares the existing asset and bone selection models. */
public final class ActorTimelineAnimationPanel {
    private static boolean detailVisible;
    private static void hideDetail() {
        if(detailVisible) { detailVisible=false; ActorAnimationEditingLifecycle.cancelTransientWork(); }
    }
    private ActorTimelineAnimationPanel() {}

    public static void render(TimelineModel model) {
        if (model == null) { hideDetail(); return; }
        if (!ImGui.collapsingHeader("演员骨骼动画##timelineActorAnimation")) { hideDetail(); return; }
        float available = ImGui.getContentRegionAvailY();
        if (available < 100f) {
            hideDetail();
            ImGui.textDisabled("请增大时间线面板以显示骨骼编辑区域。");
            return;
        }
        detailVisible=true;
        ImGui.beginChild("##TimelineActorAnimationDetail", 0f,
                Math.min(320f, available * 0.5f), true, ImGuiWindowFlags.None);
        try {
            var selectedId = ActorSelectionHub.getSelectedActorId();
            boolean hasActor = false;
            for (var track : model.getAllTracks()) {
                var payload = track.getActorTrackPayload();
                if (payload == null || payload.getActorRef() == null || payload.getActorRef().getActorId() == null) continue;
                hasActor = true;
                var actorId = payload.getActorRef().getActorId();
                if (ImGui.selectable(track.getName() + "##animationActor:" + actorId,
                        actorId.equals(selectedId))) {
                    ActorSelectionHub.selectActor(actorId);
                    selectedId = actorId;
                }
            }
            if (!hasActor) {
                ImGui.textWrapped("先创建演员轨道，再在这里选择演员并编辑身体部位。");
                return;
            }
            if (selectedId == null) {
                ImGui.textDisabled("请选择一个演员。");
                return;
            }
            ImGui.separator();
            ImGui.textWrapped("主时间线编排动作片段；下方编辑播放头处的动画资产。Ctrl+点击骨骼可多选部位。");
            ActorBonePropertiesPanel.renderInTimeline(model, selectedId);
            ActorAnimationDopeSheet.render(model, selectedId);
        } finally {
            ImGui.endChild();
        }
    }
}
