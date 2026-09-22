package com.director.ui.actor.model;

import com.director.actor.model.BoneCuboid;
import com.director.actor.model.CustomModelDefinition;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.model.texture.CuboidFaceUvs;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelAssetEditingTest {
    @AfterEach
    void cleanup() {
        TimelineCommandBridge.bind(null);
        CustomModelLibrary.getInstance().clear();
    }

    @Test
    void updatesCuboidUvAndSupportsUndo() {
        BoneCuboid cuboid = new BoneCuboid(
                "torso",
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                "minecraft:white_concrete",
                "director:custom_model_piece/old",
                "0",
                CuboidFaceUvs.uniform(new float[] {0, 0, 16, 16}));
        CustomModelDefinition model = new CustomModelDefinition(
                "project:robot_model",
                "Robot",
                "project:robot",
                List.of(cuboid));
        CustomModelLibrary.getInstance().register(model);

        TimelineModel timeline = new TimelineModel(new TimelineViewportState());
        TimelineCommandHistory history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);

        CustomModelAssetEditing.updateTexturedCuboid(
                timeline,
                "project:robot_model",
                0,
                "0",
                CuboidFaceUvs.uniform(new float[] {2, 2, 14, 14}));

        BoneCuboid updated = CustomModelLibrary.getInstance()
                .find("project:robot_model")
                .orElseThrow()
                .cuboids()
                .get(0);
        assertEquals("0", updated.textureId());
        assertNotEquals("director:custom_model_piece/old", updated.itemModelId());

        assertTrue(history.undo());
        BoneCuboid restored = CustomModelLibrary.getInstance()
                .find("project:robot_model")
                .orElseThrow()
                .cuboids()
                .get(0);
        assertEquals("director:custom_model_piece/old", restored.itemModelId());

        assertTrue(history.redo());
        assertNotEquals("director:custom_model_piece/old", CustomModelLibrary.getInstance()
                .find("project:robot_model")
                .orElseThrow()
                .cuboids()
                .get(0)
                .itemModelId());
    }
}
