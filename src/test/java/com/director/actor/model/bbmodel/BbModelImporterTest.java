package com.director.actor.model.bbmodel;

import com.director.actor.model.BoneCuboid;
import com.director.actor.rig.skeleton.BoneDefinition;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbModelImporterTest {
    @TempDir Path tempDir;

    private static final String MINIMAL = """
            {
              "meta": { "format_version": "4.5", "model_format": "free" },
              "resolution": { "width": 64, "height": 64 },
              "elements": [
                {
                  "uuid": "e1",
                  "name": "torso_box",
                  "from": [-3, 0, -2],
                  "to": [3, 7, 2],
                  "origin": [0, 0, 0]
                },
                {
                  "uuid": "e2",
                  "name": "head_box",
                  "from": [-2, 0, -2],
                  "to": [2, 4, 2],
                  "origin": [0, 0, 0],
                  "rotation": [0, 45, 0]
                }
              ],
              "outliner": [
                {
                  "name": "root",
                  "uuid": "b0",
                  "origin": [0, 0, 0],
                  "children": [
                    {
                      "name": "torso",
                      "uuid": "b1",
                      "origin": [0, 12, 0],
                      "children": [
                        "e1",
                        {
                          "name": "head",
                          "uuid": "b2",
                          "origin": [0, 19, 0],
                          "children": ["e2"]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String MULTI_ROOT = """
            {
              "elements": [
                { "uuid": "e1", "name": "a", "from": [0, 0, 0], "to": [16, 16, 16] },
                { "uuid": "e2", "name": "b", "from": [0, 0, 0], "to": [16, 16, 16] }
              ],
              "outliner": [
                {
                  "name": "left",
                  "uuid": "b1",
                  "origin": [-8, 8, 0],
                  "children": ["e1"]
                },
                {
                  "name": "right",
                  "uuid": "b2",
                  "origin": [8, 8, 0],
                  "children": ["e2"]
                }
              ]
            }
            """;

    private static final String DUPLICATE_BONES = """
            {
              "elements": [
                { "uuid": "e1", "name": "a", "from": [0, 0, 0], "to": [16, 16, 16] },
                { "uuid": "e2", "name": "b", "from": [0, 0, 0], "to": [16, 16, 16] }
              ],
              "outliner": [
                {
                  "name": "Root",
                  "uuid": "b0",
                  "origin": [0, 0, 0],
                  "children": [
                    { "name": "Arm", "uuid": "b1", "origin": [0, 8, 0], "children": ["e1"] },
                    { "name": "Arm", "uuid": "b2", "origin": [0, 16, 0], "children": ["e2"] }
                  ]
                }
              ]
            }
            """;

    private static final String INVALID_CUBOID = """
            {
              "elements": [
                { "uuid": "e1", "name": "good", "from": [0, 0, 0], "to": [16, 16, 16] },
                { "uuid": "e2", "name": "bad", "from": [0, 0, 0], "to": [0, 16, 16] }
              ],
              "outliner": [
                {
                  "name": "root",
                  "uuid": "b0",
                  "origin": [0, 0, 0],
                  "children": ["e1", "e2"]
                }
              ]
            }
            """;

    @Test
    void convertsMinimalModel() throws Exception {
        Path file = writeModel("minimal.bbmodel", MINIMAL);
        BbModelImportResult result = BbModelImporter.importFile(
                file, "test:rig", "test:model", "minecraft:white_concrete");

        assertEquals("test:rig", result.rig().id());
        assertEquals("test:model", result.model().id());
        assertEquals("test:rig", result.model().rigId());
        assertEquals(3, result.rig().boneCount());
        assertEquals(2, result.model().cuboids().size());

        BoneDefinition root = result.rig().bone(0);
        BoneDefinition torso = result.rig().bone(1);
        BoneDefinition head = result.rig().bone(2);
        assertEquals("root", root.id());
        assertEquals(-1, root.parentIndex());
        assertEquals("torso", torso.id());
        assertEquals(0, torso.parentIndex());
        assertEquals("head", head.id());
        assertEquals(1, head.parentIndex());

        assertVector3f(new Vector3f(0f, 0.75f, 0f), torso.restLocal().translation());
        assertVector3f(new Vector3f(0f, 0.4375f, 0f), head.restLocal().translation());

        BoneCuboid torsoCuboid = findCuboid(result.model().cuboids(), "torso");
        assertVector3f(new Vector3f(-0.1875f, -0.75f, -0.125f), torsoCuboid.offset());
        assertVector3f(new Vector3f(0.375f, 0.4375f, 0.25f), torsoCuboid.size());
        assertEquals("minecraft:white_concrete", torsoCuboid.blockStateId());

        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("cuboid 自身旋转")));
    }

    @Test
    void insertsSyntheticRootForMultipleTopLevelGroups() throws Exception {
        Path file = writeModel("multi_root.bbmodel", MULTI_ROOT);
        BbModelImportResult result = BbModelImporter.importFile(
                file, "test:rig", "test:model", "minecraft:white_concrete");

        assertEquals(3, result.rig().boneCount());
        assertEquals("root", result.rig().bone(0).id());
        assertEquals(-1, result.rig().bone(0).parentIndex());
        assertEquals("left", result.rig().bone(1).id());
        assertEquals("right", result.rig().bone(2).id());
        assertEquals(0, result.rig().bone(1).parentIndex());
        assertEquals(0, result.rig().bone(2).parentIndex());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("合成 root")));
    }

    @Test
    void renamesDuplicateBoneIds() throws Exception {
        Path file = writeModel("duplicate_bones.bbmodel", DUPLICATE_BONES);
        BbModelImportResult result = BbModelImporter.importFile(
                file, "test:rig", "test:model", "minecraft:white_concrete");

        assertEquals("arm", result.rig().bone(1).id());
        assertEquals("arm_2", result.rig().bone(2).id());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Arm") && w.contains("arm_2")));
    }

    @Test
    void parsesFaceUvsAndAssignsItemModelId() throws Exception {
        String textured = """
                {
                  "resolution": { "width": 64, "height": 64 },
                  "elements": [
                    {
                      "uuid": "e1",
                      "name": "torso_box",
                      "from": [-3, 0, -2],
                      "to": [3, 7, 2],
                      "faces": {
                        "north": { "uv": [0, 8, 6, 15], "texture": 0 },
                        "south": { "uv": [6, 8, 12, 15], "texture": 0 }
                      }
                    }
                  ],
                  "textures": [
                    { "id": "0", "name": "skin.png", "source": "data:image/png;base64,iVBORw0KGgo=" }
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
        Path file = writeModel("textured.bbmodel", textured);
        BbModelImportResult result = BbModelImporter.importFile(
                file, "test:rig", "test:model", "minecraft:white_concrete");

        BoneCuboid cuboid = result.model().cuboids().getFirst();
        assertTrue(cuboid.usesCustomTexture());
        assertEquals("0", cuboid.textureId());
        assertTrue(cuboid.itemModelId().startsWith("director:custom_model_piece/"));
        assertEquals(64, result.textureAtlasWidth());
    }

    @Test
    void skipsInvalidCuboidsWithWarning() throws Exception {
        Path file = writeModel("invalid_cuboid.bbmodel", INVALID_CUBOID);
        BbModelImportResult result = BbModelImporter.importFile(
                file, "test:rig", "test:model", "minecraft:white_concrete");

        assertEquals(1, result.model().cuboids().size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("无效 cuboid")));
    }

    private Path writeModel(String name, String json) throws Exception {
        return Files.writeString(tempDir.resolve(name), json);
    }

    private static BoneCuboid findCuboid(List<BoneCuboid> cuboids, String boneId) {
        return cuboids.stream()
                .filter(c -> c.boneId().equals(boneId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertVector3f(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0e-4f);
        assertEquals(expected.y, actual.y, 1.0e-4f);
        assertEquals(expected.z, actual.z, 1.0e-4f);
    }
}
