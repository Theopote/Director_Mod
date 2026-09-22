package com.director.ui.actor.model;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorType;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.rig.skeleton.CustomRigLibrary;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbModelAssetEditingTest {
    private static final String MINIMAL = """
            {
              "elements": [
                { "uuid": "e1", "name": "box", "from": [0, 0, 0], "to": [16, 16, 16] }
              ],
              "outliner": [
                {
                  "name": "root",
                  "uuid": "b0",
                  "origin": [0, 0, 0],
                  "children": ["e1"]
                }
              ]
            }
            """;

    @TempDir Path tempDir;

    @AfterEach
    void cleanup() {
        TimelineCommandBridge.bind(null);
        CustomRigLibrary.getInstance().clear();
        CustomModelLibrary.getInstance().clear();
    }

    @Test
    void importRegistersAssetsBindsActorAndUndoes() throws Exception {
        Path file = Files.writeString(tempDir.resolve("robot.bbmodel"), MINIMAL);
        var model = new TimelineModel(new TimelineViewportState());
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        Actor actor = new ActorRegistry().createActor("Robot", ActorType.CUSTOM);

        var result = BbModelAssetEditing.importFile(
                model,
                file,
                "project:robot",
                "project:robot_model",
                actor);

        assertTrue(CustomRigLibrary.getInstance().contains(result.rig().id()));
        assertTrue(CustomModelLibrary.getInstance().contains(result.model().id()));
        assertEquals("project:robot", actor.getCustomRigId());
        assertEquals("project:robot_model", actor.getCustomModelId());

        assertTrue(history.undo());
        assertFalse(CustomRigLibrary.getInstance().contains("project:robot"));
        assertFalse(CustomModelLibrary.getInstance().contains("project:robot_model"));
        assertNull(actor.getCustomRigId());
        assertNull(actor.getCustomModelId());

        assertTrue(history.redo());
        assertEquals("project:robot", actor.getCustomRigId());
        assertEquals("project:robot_model", actor.getCustomModelId());
    }

    @Test
    void rejectsDuplicateIdsWithoutChangingLibraries() throws Exception {
        Path file = Files.writeString(tempDir.resolve("robot.bbmodel"), MINIMAL);
        var model = new TimelineModel(new TimelineViewportState());
        TimelineCommandBridge.bind(new TimelineCommandHistory());

        BbModelAssetEditing.importFile(model, file, "project:robot", "project:robot_model", null);
        int rigCountAfterFirstImport = CustomRigLibrary.getInstance().all().size();

        assertThrows(IllegalArgumentException.class, () ->
                BbModelAssetEditing.importFile(model, file, "project:robot", "project:robot_model_2", null));
        assertEquals(rigCountAfterFirstImport, CustomRigLibrary.getInstance().all().size());
        assertFalse(CustomModelLibrary.getInstance().contains("project:robot_model_2"));
    }

    @Test
    void suggestsStableIdsFromFileName() {
        assertEquals("project:my_robot", BbModelAssetEditing.suggestRigId(Path.of("My Robot.bbmodel")));
        assertEquals("project:my_robot_model", BbModelAssetEditing.suggestModelId(Path.of("My Robot.bbmodel")));
    }
}
