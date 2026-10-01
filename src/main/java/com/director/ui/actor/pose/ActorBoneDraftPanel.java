package com.director.ui.actor.pose;

import imgui.ImGui;
import net.minecraft.client.resource.language.I18n;

/** Shared pending gesture controls for the tool properties and timeline inspector. */
public final class ActorBoneDraftPanel {
    private ActorBoneDraftPanel() {}
    public static void render() {
        var draft = ActorBoneGizmoController.getInstance();
        if (!draft.hasPendingDraft()) return;
        ImGui.textWrapped(I18n.translate("gui.director.actor.bone_draft.pending") + " " + draft.draftDescription());
        if (ImGui.button(I18n.translate("gui.director.actor.bone_draft.apply") + "##applyBoneDraft")) draft.applyDraft();
        ImGui.sameLine();
        if (ImGui.button(I18n.translate("gui.director.actor.bone_draft.cancel") + "##cancelBoneDraft")) draft.cancelDraft();
    }
}
