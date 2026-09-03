package com.director.actor.rig.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/** Immutable local transform for one rig bone. */
public final class BoneTransform {
    private static final BoneTransform IDENTITY = new BoneTransform(
            new Vector3f(), new Quaternionf(), new Vector3f(1f));

    private final Vector3f translation;
    private final Quaternionf rotation;
    private final Vector3f scale;

    public BoneTransform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        this.translation = new Vector3f(Objects.requireNonNull(translation, "translation"));
        this.rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation")).normalize();
        this.scale = new Vector3f(Objects.requireNonNull(scale, "scale"));
        if (!this.translation.isFinite() || !this.rotation.isFinite() || !this.scale.isFinite()) {
            throw new IllegalArgumentException("Bone transform must contain finite values");
        }
    }

    public static BoneTransform identity() {
        return IDENTITY;
    }

    public static BoneTransform translation(float x, float y, float z) {
        return new BoneTransform(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(1f));
    }

    public Vector3f translation() { return new Vector3f(translation); }
    public Quaternionf rotation() { return new Quaternionf(rotation); }
    public Vector3f scale() { return new Vector3f(scale); }

    public Matrix4f toMatrix() {
        return new Matrix4f().translationRotateScale(translation, rotation, scale);
    }

    public BoneTransform withRotation(Quaternionf value) {
        return new BoneTransform(translation, value, scale);
    }
}
