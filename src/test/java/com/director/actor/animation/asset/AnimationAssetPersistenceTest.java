package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.HumanoidRig;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationAssetPersistenceTest {
    @TempDir Path tempDir;
    private final AnimationAssetLibrary library = AnimationAssetLibrary.getInstance();

    @AfterEach void cleanUp() { library.clear(); }

    @Test
    void roundTripsSparseChannelsMarkersAndInterpolation() throws Exception {
        AnimationAsset asset = asset("project:wave", "Wave");
        BoneTrack arm = asset.getOrCreateBoneTrack(HumanoidRig.LEFT_ARM);
        arm.translation().put(0f, new Vector3f(1f, 2f, 3f), InterpolationMode.STEP);
        arm.rotation().put(0.5f, new Quaternionf().rotateXYZ(0.1f, 0.2f, 0.3f), InterpolationMode.SMOOTH);
        arm.scale().put(1f, new Vector3f(1.1f, 1.2f, 1.3f), InterpolationMode.LINEAR);
        asset.addMarker(new AnimationMarker(0.75f, "sound:whoosh"));
        library.register(asset);
        Path file = tempDir.resolve("animation_assets.json");

        AnimationAssetPersistence.save(file, library);
        library.clear();
        AnimationAssetPersistence.load(file, library);

        AnimationAsset restored = library.find("project:wave").orElseThrow();
        BoneTrack restoredArm = restored.findBoneTrack(HumanoidRig.LEFT_ARM).orElseThrow();
        assertEquals(InterpolationMode.STEP, restoredArm.translation().keyframes().getFirst().interpolation());
        assertEquals(0.2f, restoredArm.rotation().keyframes().getFirst().value()
                .getEulerAnglesXYZ(new Vector3f()).y, 1.0e-4f);
        assertEquals(1.3f, restoredArm.scale().keyframes().getFirst().value().z, 1.0e-5f);
        assertEquals("sound:whoosh", restored.markers().getFirst().name());
    }

    @Test
    void fallsBackToLastCommittedBackupWhenMainFileIsCorrupt() throws Exception {
        Path file = tempDir.resolve("animation_assets.json");
        library.register(asset("project:first", "First"));
        AnimationAssetPersistence.save(file, library);
        library.clear();
        library.register(asset("project:second", "Second"));
        AnimationAssetPersistence.save(file, library);
        Files.writeString(file, "{ broken json");

        library.clear();
        AnimationAssetPersistence.load(file, library);

        assertTrue(library.contains("project:first"));
        assertEquals(1, library.size());
    }

    private static AnimationAsset asset(String id, String name) {
        return new AnimationAsset(id, name, HumanoidRig.DEFINITION, 1f, AnimationLoopMode.LOOP);
    }
}
