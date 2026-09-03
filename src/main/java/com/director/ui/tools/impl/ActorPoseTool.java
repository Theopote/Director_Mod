package com.director.ui.tools.impl;

import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.actor.lookat.ActorLookAtGizmoController;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.actor.pose.ActorBoneSelectionController;
import com.director.ui.actor.pose.ActorBoneGizmoController;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolLifecycle;

import java.util.UUID;

public final class ActorPoseTool extends AbstractActorTool implements ToolLifecycle {
    public static final String ID = "actor_pose";

    public ActorPoseTool() {
        super(ID, Icons.ANIMATION);
    }

    @Override
    public void onActivate() {
        UUID actorId = ActorSelectionHub.getSelectedActorId();
        // Pose 模式拥有骨骼选择上下文，不与整体 Transform / LookAt Gizmo 争抢输入。
        ActorGizmoController.getInstance().clearTarget();
        ActorLookAtGizmoController.getInstance().clearTarget();
        ActorBoneSelectionController.getInstance().activate(actorId);
    }

    @Override
    public void onDeactivate() {
        ActorBoneGizmoController.getInstance().deactivate();
        ActorBoneSelectionController.getInstance().deactivate();
    }
}
