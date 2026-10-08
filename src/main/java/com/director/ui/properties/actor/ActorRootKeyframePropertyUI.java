package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.actor.pose.ActorRootKeyframeEditing;
import imgui.ImGui;

/** Selected root reference only; clicking these controls does not author another clip. */
public final class ActorRootKeyframePropertyUI {
    private ActorRootKeyframePropertyUI() {}
    public static void render(EntityClip clip) {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        var wrapper = clip.getTimelineClip();
        var owner = wrapper == null ? null : wrapper.getParentTrack();
        if (model == null || owner == null) return;
        ImGui.separator();
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.root_keys.hint"));
        boolean editable = ActorRootKeyframeEditing.editable(model, owner, clip)
                && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                && !com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        ImGui.beginDisabled(!editable);
        boolean conversionBlocked = ActorRootKeyframeEditing.requiresLinearConversion(model, clip);
        ImGui.beginDisabled(conversionBlocked);
        if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.actor.root_keys.insert")))
            ActorRootKeyframeEditing.insert(model, owner, clip);
        ImGui.endDisabled();
        ImGui.sameLine();
        boolean hasKey = clip.getKeyframes().stream().anyMatch(k -> Math.abs(k.getTime() - model.getCurrentTime()) <= 1e-4f);
        ImGui.beginDisabled(!hasKey);
        if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.actor.root_keys.remove")))
            ActorRootKeyframeEditing.remove(model, owner, clip);
        ImGui.endDisabled(); ImGui.endDisabled();
        if (conversionBlocked) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.root_keys.smooth_conversion"));
        if (!editable) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.root_keys.readonly"));
    }
}
