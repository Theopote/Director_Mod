package com.director.ui.actor.pose;

/** Cancel transient work while retaining the shared key selection and clipboard. */
public final class ActorAnimationEditingLifecycle {
    private ActorAnimationEditingLifecycle() {}
    public static void cancelTransientWork() {
        com.director.ui.actor.lookat.ActorLookAtGizmoController.getInstance().cancelCreation();
        com.director.ui.timeline.render.ActorBoneTimelineRows.closeAllTransientEditors();
        ActorAnimationDopeSheet.cancelKeyInteraction();
        ActorKeyframeTimePanel.cancelAll();
        ActorBonePropertiesPanel.cancelNumericDraft();
        var gizmo=ActorBoneGizmoController.getInstance();
        if(gizmo.isDragging()) gizmo.cancelDraft();
    }
}
