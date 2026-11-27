package com.director.ui.layout;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutPersistenceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void switchingWorldsSavesEachLayoutToItsOwnDirectory() throws Exception {
        Path worldA = Files.createDirectory(tempDir.resolve("world-a"));
        Path worldB = Files.createDirectory(tempDir.resolve("world-b"));
        LayoutPersistenceManager manager = manager(tempDir.resolve("presets"), new AtomicReference<>(""));

        manager.reloadForWorld("world-a", worldA, "test");
        manager.updatePanelVisibility("timeline", true);
        manager.saveLayout();

        manager.reloadForWorld("world-b", worldB, "test");
        manager.updatePanelVisibility("timeline", false);
        manager.saveLayout();

        String worldALayout = Files.readString(worldA.resolve("director/layouts/ui_layout.json"));
        String worldBLayout = Files.readString(worldB.resolve("director/layouts/ui_layout.json"));
        JsonObject layoutA = JsonParser.parseString(worldALayout).getAsJsonObject();
        JsonObject layoutB = JsonParser.parseString(worldBLayout).getAsJsonObject();
        assertTrue(layoutA.getAsJsonObject("panels").getAsJsonObject("timeline").get("visible").getAsBoolean());
        assertFalse(layoutB.getAsJsonObject("panels").getAsJsonObject("timeline").get("visible").getAsBoolean());
        assertTrue(layoutA.get("worldFingerprint").getAsString().contains("world-a"));
        assertFalse(layoutA.get("worldFingerprint").getAsString().contains("world-b"));

        manager.reloadForWorld("world-a", worldA, "test");
        assertEquals(Boolean.TRUE, manager.getStoredPanelVisibility("timeline"));
    }

    @Test
    void sameWorldIdAtDifferentPathsDoesNotReuseTheOldLayout() throws Exception {
        Path worldA = Files.createDirectory(tempDir.resolve("first"));
        Path worldB = Files.createDirectory(tempDir.resolve("second"));
        LayoutPersistenceManager manager = manager(tempDir.resolve("presets"), new AtomicReference<>(""));

        manager.reloadForWorld("same-id", worldA, "test");
        manager.updatePanelVisibility("timeline", true);
        manager.saveLayout();
        manager.reloadForWorld("same-id", worldB, "test");

        assertNull(manager.getStoredPanelVisibility("timeline"));
        assertTrue(Files.exists(worldA.resolve("director/layouts/ui_layout.json")));
        assertFalse(Files.exists(worldB.resolve("director/layouts/ui_layout.json")));
    }

    @Test
    void unicodePresetNamesRemainDistinctAndCarryFullGeometry() throws Exception {
        Path world = Files.createDirectory(tempDir.resolve("world"));
        AtomicReference<String> geometry = new AtomicReference<>("[Window]\nPos=10,20\n");
        List<String> restored = new ArrayList<>();
        LayoutPersistenceManager manager = new LayoutPersistenceManager(
                tempDir.resolve("presets"), geometry::get, restored::add);
        manager.reloadForWorld("world", world, "test");
        manager.updatePanelVisibility("timeline", true);

        assertTrue(manager.saveLayoutPreset("角色 预设"));
        assertTrue(manager.saveLayoutPreset("角色!预设"));

        List<String> names = manager.listLayoutPresets();
        assertEquals(2, names.size());
        assertTrue(names.contains("角色 预设"));
        assertTrue(names.contains("角色!预设"));

        Path otherWorld = Files.createDirectory(tempDir.resolve("other-world"));
        manager.reloadForWorld("other-world", otherWorld, "test");
        manager.updatePanelVisibility("timeline", false);
        manager.saveLayout();
        geometry.set("[Window]\nPos=99,99\n");

        assertTrue(manager.loadLayoutPreset("角色 预设"));
        assertEquals(Boolean.TRUE, manager.getStoredPanelVisibility("timeline"));
        assertEquals(List.of("[Window]\nPos=10,20\n"), restored);
        assertEquals("[Window]\nPos=10,20\n", Files.readString(otherWorld.resolve("director/layouts/imgui.ini")));
    }

    @Test
    void failedGeometryCaptureDoesNotCreateAnIncompletePreset() throws Exception {
        Path world = Files.createDirectory(tempDir.resolve("world"));
        Path presetsDir = tempDir.resolve("presets");
        LayoutPersistenceManager manager = new LayoutPersistenceManager(
                presetsDir, () -> { throw new IllegalStateException("capture failed"); }, ignored -> { });
        manager.reloadForWorld("world", world, "test");

        assertFalse(manager.saveLayoutPreset("完整布局"));
        assertTrue(manager.listLayoutPresets().isEmpty());
        try (var files = Files.list(presetsDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void legacyPresetsRemainLoadable() throws Exception {
        Path world = Files.createDirectory(tempDir.resolve("world"));
        Path presetsDir = tempDir.resolve("presets");
        Files.createDirectories(presetsDir);
        Files.writeString(presetsDir.resolve("legacy.json"), """
                {
                  "version": "1.0",
                  "panels": {
                    "timeline": {
                      "panelName": "timeline",
                      "windowName": "Timeline",
                      "visible": true
                    }
                  }
                }
                """);
        List<String> restored = new ArrayList<>();
        LayoutPersistenceManager manager = new LayoutPersistenceManager(
                presetsDir, () -> "", restored::add);
        manager.reloadForWorld("world", world, "test");

        assertEquals(List.of("legacy"), manager.listLayoutPresets());
        assertTrue(manager.loadLayoutPreset("legacy"));
        assertEquals(Boolean.TRUE, manager.getStoredPanelVisibility("timeline"));
        assertTrue(restored.isEmpty());
        assertNotNull(manager.getLayoutFile());
    }

    private LayoutPersistenceManager manager(Path presetsDir, AtomicReference<String> geometry) {
        return new LayoutPersistenceManager(presetsDir, geometry::get, ignored -> { });
    }
}
