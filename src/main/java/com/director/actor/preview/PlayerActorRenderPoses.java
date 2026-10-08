package com.director.actor.preview;

import com.director.actor.rig.skeleton.RigPose;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import java.util.*;

/** Ephemeral pose transfer; ordinary player states are explicitly removed on reuse. */
public final class PlayerActorRenderPoses {
    private static final Map<PlayerEntityRenderState,RigPose> poses=new WeakHashMap<>();
    private PlayerActorRenderPoses() {}
    public static void put(PlayerEntityRenderState state,RigPose pose) {
        if(pose==null) poses.remove(state); else poses.put(state,pose.copy());
    }
    public static RigPose get(PlayerEntityRenderState state) { return poses.get(state); }
}
