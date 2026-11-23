package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.HumanoidRig;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyframeTrackNotificationTest {
    @Test void channelEditsNotifyOnlyWhenTheCurveChanges() {
        int[] hits = {0};
        var track = new KeyframeTrack<>(ChannelValueAdapters.VECTOR3);
        track.onChanged(() -> hits[0]++);
        track.put(0f, new Vector3f(1, 0, 0), InterpolationMode.LINEAR);
        assertEquals(1, hits[0]);
        assertFalse(track.removeAt(1f));
        assertEquals(1, hits[0]);
        assertTrue(track.removeAt(0f));
        assertEquals(2, hits[0]);
    }

    @Test void boneAndAssetEditsReachTheLibraryUntilTheTrackIsRemoved() {
        int[] hits = {0};
        var bone = new BoneTrack();
        bone.onChanged(() -> hits[0]++);
        bone.translation().put(0f, new Vector3f(), InterpolationMode.LINEAR);
        bone.scale().put(0f, new Vector3f(1, 1, 1), InterpolationMode.LINEAR);
        assertEquals(2, hits[0]);

        var library = new AnimationAssetLibrary();
        var asset = new AnimationAsset("wave-test", "Wave", HumanoidRig.DEFINITION, 1.2f, AnimationLoopMode.ONCE);
        assertTrue(library.addIfAbsent(asset));
        long revision = library.revision();
        var rotation = asset.getOrCreateBoneTrack(HumanoidRig.RIGHT_ARM).rotation();
        rotation.put(0f, new Quaternionf(), InterpolationMode.LINEAR);
        assertEquals(revision + 1, library.revision());
        asset.setDuration(1.2f);
        assertEquals(revision + 1, library.revision());
        asset.setDuration(2f);
        asset.setLoopMode(AnimationLoopMode.LOOP);
        asset.addMarker(new AnimationMarker(0.1f, "hit"));
        assertEquals(revision + 4, library.revision());
        var detached = asset.findBoneTrack(HumanoidRig.RIGHT_ARM).orElseThrow();
        assertTrue(asset.removeBoneTrack(HumanoidRig.RIGHT_ARM));
        assertEquals(revision + 5, library.revision());
        detached.rotation().put(0.2f, new Quaternionf(), InterpolationMode.LINEAR);
        assertEquals(revision + 5, library.revision());
    }
}
