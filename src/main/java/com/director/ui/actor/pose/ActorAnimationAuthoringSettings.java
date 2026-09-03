package com.director.ui.actor.pose;

import com.director.actor.animation.asset.InterpolationMode;
import com.director.client.gizmo.GizmoMode;

/** Session-scoped settings shared by the Pose tool UI and bone Gizmo. */
public final class ActorAnimationAuthoringSettings {
    private static final ActorAnimationAuthoringSettings INSTANCE = new ActorAnimationAuthoringSettings();
    private boolean autoKeyframe;
    private String assetId = "project:actor_animation";
    private InterpolationMode interpolation = InterpolationMode.SMOOTH;
    private float newAssetDuration = 5f;
    private GizmoMode boneGizmoMode = GizmoMode.ROTATE;

    private ActorAnimationAuthoringSettings() {}
    public static ActorAnimationAuthoringSettings getInstance() { return INSTANCE; }
    public boolean isAutoKeyframe() { return autoKeyframe; }
    public void setAutoKeyframe(boolean value) { autoKeyframe = value; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String value) { if (value != null && !value.isBlank()) assetId = value.trim(); }
    public InterpolationMode getInterpolation() { return interpolation; }
    public void setInterpolation(InterpolationMode value) { interpolation = value == null ? InterpolationMode.SMOOTH : value; }
    public float getNewAssetDuration() { return newAssetDuration; }
    public void setNewAssetDuration(float value) { newAssetDuration = Math.max(0.1f, Math.min(600f, value)); }
    public GizmoMode getBoneGizmoMode() { return boneGizmoMode; }
    public void setBoneGizmoMode(GizmoMode value) {
        boneGizmoMode = value == GizmoMode.TRANSLATE ? GizmoMode.TRANSLATE : GizmoMode.ROTATE;
    }
}
