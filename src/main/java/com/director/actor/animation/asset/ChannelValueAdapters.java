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

    private ChannelValueAdapters() {}
}
