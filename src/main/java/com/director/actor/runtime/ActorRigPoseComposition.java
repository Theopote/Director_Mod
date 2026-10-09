package com.director.actor.runtime;

import com.director.actor.rig.skeleton.RigPose;
import java.util.function.Consumer;

/** Procedural movement fills unkeyed channels; authored bone channels have priority. */
final class ActorRigPoseComposition {
    private ActorRigPoseComposition() {}

    static RigPose compose(RigPose procedural, RigPose authored, Consumer<RigPose> quickPose) {
        if (procedural == null) return authored.copy();
        RigPose base = procedural.copy();
        quickPose.accept(base);
        return base.overlay(authored);
    }
}
