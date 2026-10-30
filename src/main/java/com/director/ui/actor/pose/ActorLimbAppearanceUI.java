package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.rig.skeleton.LimbBendMode;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;

/** Nearby shortcut to the actor-wide appearance setting; never rewrites joint keys. */
final class ActorLimbAppearanceUI {
    private ActorLimbAppearanceUI() {}
    static void render(TimelineModel model,Actor actor) {
        if(actor==null || actor.getType()!=ActorType.PLAYER) return;
        ImGui.textDisabled("整个演员的肘膝外观（共用现有关键帧）");
        ImGui.beginDisabled(model.isPlaying() || model.isScrubbing() || ActorBonePropertiesPanel.hasNumericDraft()
                || ActorBoneGizmoController.getInstance().hasPendingDraft()
                || com.director.export.video.VideoExportManager.getInstance().isExporting());
        for(var mode:LimbBendMode.values()) {
            if(mode!=LimbBendMode.values()[0]) ImGui.sameLine();
            if(ImGui.radioButton((mode==LimbBendMode.SMOOTH?"平滑弯曲":"直接折弯")+"##jointAppearance",actor.getLimbBendMode()==mode))
                com.director.ui.actor.ActorProjectEditing.appearance(model,ActorRegistryManager.getInstance(),actor,
                        com.director.ui.actor.ActorProjectEditing.Appearance.capture(actor).withBend(mode),
                        () -> com.director.actor.runtime.ActorRuntimeController.getInstance().refreshPosePreview(actor.getActorId()));
        }
        ImGui.endDisabled();
    }
}
