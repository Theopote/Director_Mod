package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationImportExportTest {
    @TempDir Path directory;
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); }

    @Test void exportedAnimationCanBeImportedUnderNewIdAndUndone() throws IOException {
        var asset = new AnimationAsset("project:a", "Wave", HumanoidRig.DEFINITION, 3f, AnimationLoopMode.LOOP);
        asset.getOrCreateBoneTrack(HumanoidRig.HEAD).scale().put(1f, new Vector3f(2f), InterpolationMode.SMOOTH);
        asset.addMarker(new AnimationMarker(1f, "accent"));
        Path file = directory.resolve("wave.json");
        AnimationAssetPersistence.exportAsset(file, asset);
        String originalFile = Files.readString(file);
        assertThrows(IOException.class, () -> AnimationAssetPersistence.exportAsset(file, asset));
        assertEquals(originalFile, Files.readString(file));
        var library = new AnimationAssetLibrary();
        library.register(asset);
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        assertThrows(IllegalArgumentException.class, () -> ActorAnimationAssetEditing.importFile(model, library, file, ""));
        assertSame(asset, library.find(asset.id()).orElseThrow());
        var imported = ActorAnimationAssetEditing.importFile(model, library, file, "project:b");
        assertEquals(asset.markers(), imported.markers());
        assertEquals(AnimationLoopMode.LOOP, imported.loopMode());
        assertEquals(2f, imported.findBoneTrack(HumanoidRig.HEAD).orElseThrow().scale().findAt(1f).orElseThrow().value().x);
        assertTrue(history.undo());
        assertFalse(library.contains("project:b"));
        assertTrue(history.redo());
        assertSame(imported, library.find("project:b").orElseThrow());
    }

    @Test void rejectsUnknownRigWithoutChangingProject() throws IOException {
        var asset = new AnimationAsset("project:a", "Wave", HumanoidRig.DEFINITION, 3f, AnimationLoopMode.ONCE);
        Path file = directory.resolve("invalid.json");
        AnimationAssetPersistence.exportAsset(file, asset);
        Files.writeString(file, Files.readString(file).replace("director:humanoid", "unknown:rig"));
        var library = new AnimationAssetLibrary();
        library.register(asset);
        var model = new TimelineModel(new TimelineViewportState());
        assertThrows(IOException.class, () -> ActorAnimationAssetEditing.importFile(model, library, file, "project:b"));
        assertEquals(1, library.size());
        assertSame(asset, library.find(asset.id()).orElseThrow());
    }

    @Test void rejectsInvalidScaleBeforeWritingFile() {
        var asset = new AnimationAsset("project:a", "Wave", HumanoidRig.DEFINITION, 3f, AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack(HumanoidRig.HEAD).scale().put(1f, new Vector3f(0f), InterpolationMode.LINEAR);
        Path file = directory.resolve("invalid.json");
        assertThrows(IllegalArgumentException.class, () -> AnimationAssetPersistence.exportAsset(file, asset));
        assertFalse(Files.exists(file));
    }
}
