package com.director.actor.preview;

import com.director.actor.rig.skeleton.RigPose;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;

import java.util.Map;
import java.util.WeakHashMap;

/** Pose copied from a native-mob preview onto the render state its model will read. */
public final class NativeActorRenderPoses {
    private static final Map<LivingEntityRenderState, RigPose> poses = new WeakHashMap<>();

    private NativeActorRenderPoses() {}

    public static void put(LivingEntityRenderState state, RigPose pose) {
        if (state == null) return;
        if (pose == null) remove(state);
        else poses.put(state, pose.copy());
    }

    public static void remove(LivingEntityRenderState state) {
        if (state != null) poses.remove(state);
    }

    public static RigPose get(LivingEntityRenderState state) {
        return state == null ? null : poses.get(state);
    }
}
