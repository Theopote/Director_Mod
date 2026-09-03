package com.director.actor.animation.asset;

/** Copies and interpolates the mutable value type stored by a keyframe track. */
public interface ChannelValueAdapter<T> {
    T copy(T value);
    T interpolate(T from, T to, float amount);
}
