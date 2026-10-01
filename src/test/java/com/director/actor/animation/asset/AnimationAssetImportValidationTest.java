package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.*;
import com.google.gson.*;
import org.joml.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnimationAssetImportValidationTest {
    @TempDir Path temp;
    private JsonObject portable() throws Exception {
        var asset = new AnimationAsset("project:test", "Test", HumanoidRig.DEFINITION, 2, AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack("head").scale().put(1, new Vector3f(1), InterpolationMode.LINEAR);
        asset.getOrCreateBoneTrack("head").rotation().put(1, new Quaternionf(), InterpolationMode.LINEAR);
        var file = temp.resolve("export_" + System.nanoTime() + ".json"); AnimationAssetPersistence.exportAsset(file, asset);
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
    private JsonArray channel(JsonObject root, String name) {
        return root.getAsJsonObject("asset").getAsJsonObject("boneTracks").getAsJsonObject("head").getAsJsonArray(name);
    }
    private void reject(JsonObject root) throws Exception {
        var file = temp.resolve("invalid.json"); Files.writeString(file, root.toString());
        assertThrows(IOException.class, () -> AnimationAssetPersistence.importAsset(file));
    }
    @Test void duplicateAndNearDuplicateTimesAreRejectedInsteadOfSilentlyOverwritten() throws Exception {
        var root = portable(); var keys = channel(root, "scale"); keys.add(keys.get(0).deepCopy()); reject(root);
        root = portable(); keys = channel(root, "scale"); var duplicate = keys.get(0).deepCopy().getAsJsonObject();
        duplicate.addProperty("time", 1.000001f); keys.add(duplicate); reject(root);
    }
    @Test void invalidValuesCannotBeHiddenByLaterKeysAtTheSameTime() throws Exception {
        var root = portable(); var keys = channel(root, "scale"); keys.add(keys.get(0).deepCopy());
        keys.get(0).getAsJsonObject().addProperty("x", -1); reject(root);
        root = portable(); channel(root, "scale").get(0).getAsJsonObject().addProperty("x", "NaN"); reject(root);
    }
    @Test void zeroAndOverflowQuaternionMagnitudesAreRejectedBeforeNormalization() throws Exception {
        var root = portable(); channel(root, "rotation").get(0).getAsJsonObject().addProperty("w", 0); reject(root);
        root = portable(); channel(root, "rotation").get(0).getAsJsonObject().addProperty("x", Float.MAX_VALUE); reject(root);
    }
    @Test void invalidDurationBoneAndOutOfRangeKeysAreRejected() throws Exception {
        var root = portable(); root.getAsJsonObject("asset").addProperty("duration", -1); reject(root);
        root = portable(); channel(root, "rotation").get(0).getAsJsonObject().addProperty("time", 3); reject(root);
        root = portable(); var tracks = root.getAsJsonObject("asset").getAsJsonObject("boneTracks");
        tracks.add("missing", tracks.remove("head")); reject(root);
    }
    @Test void duplicateAssetIdsInProjectFileDoNotReplaceTheExistingLibrary() throws Exception {
        var root = portable(); var project = new JsonObject(); project.addProperty("schemaVersion", 1);
        var assets = new JsonArray(); assets.add(root.get("asset")); assets.add(root.get("asset").deepCopy()); project.add("assets", assets);
        var file = temp.resolve("library.json"); Files.writeString(file, project.toString());
        var library = new AnimationAssetLibrary();
        var existing = new AnimationAsset("project:existing", "Existing", HumanoidRig.DEFINITION, 1, AnimationLoopMode.ONCE); library.register(existing);
        assertThrows(IllegalArgumentException.class, () -> AnimationAssetPersistence.load(file, library));
        assertEquals(1, library.size()); assertSame(existing, library.find(existing.id()).orElseThrow());
    }
    @Test void customAnimationRoundTripRequiresItsRigToBeLoadedFirst() throws Exception {
        var rig = new RigDefinition("test:import_dependency", "Rig", List.of(
                new BoneDefinition("custom_root", "Root", -1, BoneTransform.identity(), BoneCapabilities.full())));
        var rigs = CustomRigLibrary.getInstance(); rigs.register(rig);
        try {
            var source = new AnimationAsset("project:custom", "Custom", rig, 2, AnimationLoopMode.ONCE);
            source.getOrCreateBoneTrack("custom_root").rotation().put(1, new Quaternionf().rotateX(.4f), InterpolationMode.SMOOTH);
            var animations = new AnimationAssetLibrary(); animations.register(source);
            var rigFile = temp.resolve("rigs.json"); CustomRigPersistence.save(rigFile, rigs);
            var file = temp.resolve("animations.json"); AnimationAssetPersistence.save(file, animations);
            rigs.remove(rig.id()); animations.clear();
            assertThrows(IllegalArgumentException.class, () -> AnimationAssetPersistence.load(file, animations));
            CustomRigPersistence.load(rigFile, rigs); AnimationAssetPersistence.load(file, animations);
            assertEquals(rig.id(), animations.find(source.id()).orElseThrow().rigId());
            assertTrue(animations.find(source.id()).orElseThrow().findBoneTrack("custom_root").orElseThrow().rotation().findAt(1).isPresent());
        } finally { rigs.remove(rig.id()); }
    }
}
