package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.BoneTransform;
import com.director.actor.rig.skeleton.RigPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Sparse translation, rotation and scale channels for one bone. */
public final class BoneTrack {
    private final KeyframeTrack<Vector3f> translation = new KeyframeTrack<>(ChannelValueAdapters.VECTOR3);
    private final KeyframeTrack<Quaternionf> rotation = new KeyframeTrack<>(ChannelValueAdapters.QUATERNION);
    private final KeyframeTrack<Vector3f> scale = new KeyframeTrack<>(ChannelValueAdapters.VECTOR3);

    public KeyframeTrack<Vector3f> translation() { return translation; }
    public KeyframeTrack<Quaternionf> rotation() { return rotation; }
    public KeyframeTrack<Vector3f> scale() { return scale; }

    public boolean isEmpty() { return translation.isEmpty() && rotation.isEmpty() && scale.isEmpty(); }

    public int authoredChannels() {
        return (translation.isEmpty() ? 0 : RigPose.TRANSLATION)
                | (rotation.isEmpty() ? 0 : RigPose.ROTATION)
                | (scale.isEmpty() ? 0 : RigPose.SCALE);
    }

    public BoneTransform sample(float time, BoneTransform rest) {
        return new BoneTransform(
                translation.sample(time, rest.translation()),
                rotation.sample(time, rest.rotation()),
                scale.sample(time, rest.scale()));
    }
}
