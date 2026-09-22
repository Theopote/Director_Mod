package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelResourceBuilderTest {
    @Test
    void buildsItemModelJsonWithElementsAndFaces() {
        CuboidFaceUvs faceUvs = new CuboidFaceUvs(
                new float[] {0, 8, 6, 15},
                new float[] {6, 8, 12, 15},
                new float[] {0, 8, 4, 15},
                new float[] {0, 8, 4, 15},
                new float[] {0, 0, 6, 4},
                new float[] {6, 0, 12, 4});
        BoneCuboid cuboid = new BoneCuboid(
                "torso",
                new Vector3f(-0.1875f, -0.75f, -0.125f),
                new Vector3f(0.375f, 0.4375f, 0.25f),
                "minecraft:white_concrete",
                "director:custom_model_piece/test",
                "0",
                faceUvs);

        GeneratedItemModel generated = CustomModelResourceBuilder.build(
                "project:robot", "test", cuboid, "0", 64, 64);
        JsonObject root = JsonParser.parseString(generated.modelJson()).getAsJsonObject();

        assertEquals("director:custom_model/project_robot/0", root.getAsJsonObject("textures").get("0").getAsString());
        assertTrue(root.getAsJsonArray("elements").size() == 1);
        JsonObject north = root.getAsJsonArray("elements").get(0).getAsJsonObject()
                .getAsJsonObject("faces").getAsJsonObject("north");
        assertEquals(49f, north.getAsJsonArray("uv").get(1).getAsFloat(), 1.0e-4f);
    }

    @Test
    void pieceKeyIsStableForSameGeometry() {
        BoneCuboid cuboid = new BoneCuboid(
                "torso",
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                "minecraft:white_concrete",
                "director:custom_model_piece/test",
                "0",
                CuboidFaceUvs.uniform(new float[] {0, 0, 16, 16}));
        String first = CustomModelResourceBuilder.pieceKey(cuboid, "project:robot", "0");
        String second = CustomModelResourceBuilder.pieceKey(cuboid, "project:robot", "0");
        assertEquals(first, second);
    }
}
