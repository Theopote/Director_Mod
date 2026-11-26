package com.director.actor.runtime;

import com.director.actor.rig.skeleton.RigPose;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Dependency-inverted editor overrides. UI registers an implementation at startup;
 * runtime never imports ui.actor types.
 */
public interface ActorEditorOverrideProvider {
    ActorEditorOverrideProvider NONE = new ActorEditorOverrideProvider() {};

    default boolean isDraggingActor(UUID actorId) { return false; }
    default boolean isDraggingBone(UUID actorId) { return false; }
    default boolean isMarkerDragging() { return false; }
    default UUID gizmoTargetActorId() { return null; }
    default UUID boneGizmoActorId() { return null; }
    default Vector3f previewScale(UUID actorId) { return null; }
    default ActorAnimationDraftView boneDraft(UUID actorId, float timelineTime) { return null; }
    default RigPose previewAuthoredPose(UUID actorId, String animationId, RigPose sampled) { return sampled; }
    default Vec3d previewLookAtPoint(UUID actorId) { return null; }
}
