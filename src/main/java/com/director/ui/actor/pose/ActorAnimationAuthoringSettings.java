package com.director.ui.actor.pose;

import com.director.actor.animation.asset.InterpolationMode;
import com.director.client.gizmo.GizmoMode;

/** Session-scoped settings shared by the Pose tool UI and bone Gizmo. */
public final class ActorAnimationAuthoringSettings {
    private static final ActorAnimationAuthoringSettings INSTANCE = new ActorAnimationAuthoringSettings();
    public enum EditTarget { ANIMATION, QUICK_POSE }
    private long revision;
    public long getRevision() { return revision; }
    private EditTarget editTarget = EditTarget.ANIMATION;
    public EditTarget getEditTarget() { return editTarget; }
    public void setEditTarget(EditTarget value) { var next=value == null ? EditTarget.ANIMATION : value; if(next!=editTarget) revision++; editTarget=next; }
    private boolean autoKeyframe;
    private String assetId = "project:actor_animation";
    private InterpolationMode interpolation = InterpolationMode.SMOOTH;
    private float newAssetDuration = 5f;
    private GizmoMode boneGizmoMode = GizmoMode.ROTATE;

    private ActorAnimationAuthoringSettings() {}
    public static ActorAnimationAuthoringSettings getInstance() { return INSTANCE; }
    public boolean isAutoKeyframe() { return autoKeyframe; }
    public void setAutoKeyframe(boolean value) { if(autoKeyframe!=value) revision++; autoKeyframe = value; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String value) { if (value != null && !value.isBlank()) { var next=value.trim(); if(!next.equals(assetId)) revision++; assetId=next; } }
    public InterpolationMode getInterpolation() { return interpolation; }
    public void setInterpolation(InterpolationMode value) { var next=value == null ? InterpolationMode.SMOOTH : value; if(next!=interpolation) revision++; interpolation=next; }
    public float getNewAssetDuration() { return newAssetDuration; }
    public void setNewAssetDuration(float value) { if (Float.isFinite(value)) { float next=Math.max(0.1f, Math.min(600f, value)); if(next!=newAssetDuration) revision++; newAssetDuration=next; } }
    public GizmoMode getBoneGizmoMode() { return boneGizmoMode; }
    public void setBoneGizmoMode(GizmoMode value) {
        var next=value == GizmoMode.TRANSLATE || value == GizmoMode.SCALE ? value : GizmoMode.ROTATE;
        if(next!=boneGizmoMode) revision++; boneGizmoMode=next;
    }
}
