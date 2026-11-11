package com.director.actor.animation.asset;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Standard adapters for Director animation channel types. */
public final class ChannelValueAdapters {
    public static final ChannelValueAdapter<Vector3f> VECTOR3 = new ChannelValueAdapter<>() {
        @Override public Vector3f copy(Vector3f value) { return new Vector3f(value); }
        @Override public Vector3f interpolate(Vector3f from, Vector3f to, float amount) {
            return new Vector3f(from).lerp(to, amount);
        }
    };

    public static final ChannelValueAdapter<Quaternionf> QUATERNION = new ChannelValueAdapter<>() {
        @Override public Quaternionf copy(Quaternionf value) { return new Quaternionf(value).normalize(); }
        @Override public Quaternionf interpolate(Quaternionf from, Quaternionf to, float amount) {
            return new Quaternionf(from).slerp(to, amount).normalize();
        }
    };

    public static final ChannelValueAdapter<Vector3f> SCALE = new ChannelValueAdapter<>() {
        @Override public Vector3f copy(Vector3f value) { return new Vector3f(value); }
        @Override public Vector3f interpolate(Vector3f from, Vector3f to, float amount) {
            Vector3f result = VECTOR3.interpolate(from, to, amount);
            // Positive authored scales must not turn inside out during an overshoot.
            if (from.x > 0 && to.x > 0) result.x = Math.max(0.0001f, result.x);
            if (from.y > 0 && to.y > 0) result.y = Math.max(0.0001f, result.y);
            if (from.z > 0 && to.z > 0) result.z = Math.max(0.0001f, result.z);
            return result;
        }
    };

    private ChannelValueAdapters() {}
}
