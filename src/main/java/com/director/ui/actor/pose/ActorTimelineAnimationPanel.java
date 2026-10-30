package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineModel;
import net.minecraft.client.resource.language.I18n;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/** Timeline-hosted animation detail view; shares the existing asset and bone selection models. */
public final class ActorTimelineAnimationPanel {
    private static boolean detailVisible;
    private static ActorAnimationDetailTarget previousTarget;
    private static void hideDetail() {
        previousTarget=null;
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
            var target=ActorAnimationDetailTarget.resolve(model);
            if(previousTarget!=null && (previousTarget.track()!=target.track()
                    || !java.util.Objects.equals(previousTarget.actorId(),target.actorId())
                    || previousTarget.actor()!=target.actor() || previousTarget.clip()!=target.clip()
                    || previousTarget.asset()!=target.asset() || previousTarget.status()!=target.status()))
                ActorAnimationEditingLifecycle.cancelTransientWork();
            previousTarget=target;
            if(target.track()!=null) {
                ImGui.textWrapped(I18n.translate("gui.director.actor.detail.target",
                        target.actor()==null ? "?" : target.actor().getName(),target.track().getName()));
            }
            if(target.clip()!=null) ImGui.textWrapped(I18n.translate("gui.director.actor.detail.reference",
                    target.clip().getName(),target.clip().getAnimationId()));
            if(target.asset()!=null) {
                var context=ActorAnimationEditContext.capture(model,target.track(),target.clip());
                if (context != null) {
                    ImGui.textDisabled(I18n.translate("gui.director.actor.detail.time",
                            model.getCurrentTime(),context.assetTime()));
                }
                int references=ActorAnimationAssetEditing.referenceCount(model,target.asset().id());
                if(references>1) ImGui.textWrapped(I18n.translate("gui.director.actor.detail.shared",references));
            }
            if(target.status()!=ActorAnimationDetailTarget.Status.READY)
                ImGui.textWrapped(I18n.translate("gui.director.actor.detail."+target.status().name().toLowerCase(java.util.Locale.ROOT)));
            if(target.status()==ActorAnimationDetailTarget.Status.NO_ANIMATION)
                ActorBonePropertiesPanel.renderGalleryInTimeline(model,target.actorId());
            if(target.status()==ActorAnimationDetailTarget.Status.READY
                    || target.status()==ActorAnimationDetailTarget.Status.READ_ONLY) {
                var context=ActorAnimationEditContext.resolve(model,target.actorId());
                // Muted references must not expose another overlapping reference's editors.
                if(context!=null && context.clip()==target.clip()) {
                    ImGui.separator();
                    if(target.status()==ActorAnimationDetailTarget.Status.READY)
                        ActorBonePropertiesPanel.renderInTimeline(model,target.actorId());
                    ActorAnimationDopeSheet.render(model,target.actorId());
                }
            }
        } finally {
            ImGui.endChild();
        }
    }
}
