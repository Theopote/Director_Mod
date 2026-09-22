package com.director.ui.actor.model;

import com.director.actor.Actor;
import com.director.actor.model.BoneCuboid;
import com.director.actor.model.CustomModelDefinition;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.model.texture.CuboidFaceUvs;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/** Property-panel UI for editing textured custom model cuboids. */
public final class CustomModelCuboidPropertyUI {
    private static final ImString textureId = new ImString("", 128);
    private static final ImFloat uv0 = new ImFloat(0f);
    private static final ImFloat uv1 = new ImFloat(0f);
    private static final ImFloat uv2 = new ImFloat(16f);
    private static final ImFloat uv3 = new ImFloat(16f);
    private static String activeModelId;
    private static int activeCuboidIndex = -1;
    private static String statusMessage = "";

    private CustomModelCuboidPropertyUI() {}

    public static void render(Actor actor, TimelineModel timeline) {
        if (actor == null || actor.getCustomModelId() == null || timeline == null) {
            return;
        }
        CustomModelDefinition model = CustomModelLibrary.getInstance()
                .find(actor.getCustomModelId())
                .orElse(null);
        if (model == null) {
            return;
        }

        List<Integer> texturedIndices = texturedCuboidIndices(model);
        if (texturedIndices.isEmpty()) {
            ImGui.textDisabled("当前模型没有可编辑贴图的 cuboid");
            return;
        }

        if (!ImGui.treeNode("贴图 / UV 编辑##customModelCuboids")) {
            return;
        }
        ImGui.textDisabled("修改贴图 ID 或 UV 后点击“应用”，将重建客户端资源。");
        for (int index : texturedIndices) {
            BoneCuboid cuboid = model.cuboids().get(index);
            String label = cuboid.boneId() + "##customCuboid:" + index;
            if (!ImGui.treeNode(label)) {
                continue;
            }
            syncDraft(model.id(), index, cuboid);
            ImGui.inputText("贴图 ID##texture:" + index, textureId);
            ImGui.inputFloat("UV U0##uv0:" + index, uv0);
            ImGui.inputFloat("UV V0##uv1:" + index, uv1);
            ImGui.inputFloat("UV U1##uv2:" + index, uv2);
            ImGui.inputFloat("UV V1##uv3:" + index, uv3);
            if (ImGui.button("应用##applyCuboid:" + index)) {
                applyEdit(timeline, model.id(), index);
            }
            ImGui.treePop();
        }
        if (!statusMessage.isEmpty()) {
            ImGui.textWrapped(statusMessage);
        }
        ImGui.treePop();
    }

    private static List<Integer> texturedCuboidIndices(CustomModelDefinition model) {
        List<Integer> indices = new ArrayList<>();
        List<BoneCuboid> cuboids = model.cuboids();
        for (int i = 0; i < cuboids.size(); i++) {
            BoneCuboid cuboid = cuboids.get(i);
            if (cuboid.usesCustomTexture()
                    || (cuboid.textureId() != null && !cuboid.textureId().isBlank())
                    || (cuboid.faceUvs() != null && cuboid.faceUvs().hasAnyFace())) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static void syncDraft(String modelId, int cuboidIndex, BoneCuboid cuboid) {
        if (modelId.equals(activeModelId) && cuboidIndex == activeCuboidIndex) {
            return;
        }
        activeModelId = modelId;
        activeCuboidIndex = cuboidIndex;
        textureId.set(cuboid.textureId() == null ? "" : cuboid.textureId());
        float[] uv = primaryUv(cuboid.faceUvs());
        uv0.set(uv[0]);
        uv1.set(uv[1]);
        uv2.set(uv[2]);
        uv3.set(uv[3]);
        statusMessage = "";
    }

    private static float[] primaryUv(CuboidFaceUvs faceUvs) {
        if (faceUvs == null) {
            return new float[] {0f, 0f, 16f, 16f};
        }
        float[] candidate = firstNonNull(
                faceUvs.north(),
                faceUvs.south(),
                faceUvs.east(),
                faceUvs.west(),
                faceUvs.up(),
                faceUvs.down());
        if (candidate == null || candidate.length < 4) {
            return new float[] {0f, 0f, 16f, 16f};
        }
        return candidate;
    }

    private static float[] firstNonNull(float[]... values) {
        for (float[] value : values) {
            if (value != null && value.length >= 4) {
                return value;
            }
        }
        return null;
    }

    private static void applyEdit(TimelineModel timeline, String modelId, int cuboidIndex) {
        try {
            CuboidFaceUvs faceUvs = CuboidFaceUvs.uniform(new float[] {
                    uv0.get(),
                    uv1.get(),
                    uv2.get(),
                    uv3.get()
            });
            CustomModelAssetEditing.updateTexturedCuboid(
                    timeline,
                    modelId,
                    cuboidIndex,
                    textureId.get().trim(),
                    faceUvs);
            statusMessage = "已应用 cuboid 贴图/UV，可撤销。";
        } catch (IllegalArgumentException e) {
            statusMessage = e.getMessage();
        } catch (RuntimeException e) {
            statusMessage = "应用失败：" + e.getMessage();
        }
    }
}
