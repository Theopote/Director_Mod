package com.director.client.renderer;

import com.director.actor.path.ActorPathDefinition;
import com.director.actor.path.ActorPathLibraryManager;
import com.director.client.DirectorClientController;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.playback.entity.ActorRouteSampler;
import com.director.ui.actor.path.ActorPathController;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class ActorPathRenderer {

    private static final ActorPathRenderer INSTANCE = new ActorPathRenderer();
    private static final float LINE_WIDTH = 2.5f;
    private static final double ANCHOR_HALF_SIZE = 0.1;
    private static final float LIB_PATH_R = 0.35f;
    private static final float LIB_PATH_G = 0.65f;
    private static final float LIB_PATH_B = 0.95f;
    private static final float LIB_PATH_A = 0.55f;
    private static final float DRAFT_PATH_R = 0.2f;
    private static final float DRAFT_PATH_G = 0.85f;
    private static final float DRAFT_PATH_B = 0.35f;
    private static final float DRAFT_PATH_A = 0.9f;
    private static final float ANCHOR_R = 0.95f;
    private static final float ANCHOR_G = 0.55f;
    private static final float ANCHOR_B = 0.1f;
    private static final float SELECTED_R = 1.0f;
    private static final float SELECTED_G = 0.95f;
    private static final float SELECTED_B = 0.2f;

    private ActorPathRenderer() {
    }

    public static ActorPathRenderer getInstance() {
        return INSTANCE;
    }

    public void render(WorldRenderContext context) {
        if (!DirectorClientController.getInstance().isDirectorModeActive()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || context.consumers() == null) {
            return;
        }
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        MatrixStack matrices = context.matrices();
        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(false);
        RenderSystem.lineWidth(LINE_WIDTH);

        VertexConsumerProvider consumers = context.consumers();
        ActorPathController controller = ActorPathController.getInstance();
        UUID editingId = controller.getEditingLibraryPathId();

        for (ActorPathDefinition path : ActorPathLibraryManager.getAllPaths()) {
            if (!path.isVisible() || path.getWaypoints().size() < 2) {
                continue;
            }
            boolean isEditing = controller.isActive() && path.getId().equals(editingId);
            if (isEditing && !controller.getAnchorPositions().isEmpty()) {
                continue;
            }
            renderStoredPath(consumers, matrix, cameraPos, path, false);
        }

        if (controller.isActive() && !controller.getAnchorPositions().isEmpty()) {
            renderDraftPath(consumers, matrix, cameraPos, controller);
        }

        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        matrices.pop();
    }

    private void renderStoredPath(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos,
                                  ActorPathDefinition path, boolean selected) {
        List<Vec3d> curve = ActorRouteSampler.sampleCurvePositions(path.getWaypoints(), path.getInterpolationMode());
        if (curve.size() >= 2) {
            renderPathLine(consumers, matrix, cameraPos, curve, LIB_PATH_R, LIB_PATH_G, LIB_PATH_B, LIB_PATH_A);
        }
        for (Vector3f waypoint : path.getWaypoints()) {
            Vec3d anchor = new Vec3d(waypoint.x, waypoint.y, waypoint.z);
            renderAnchorBox(consumers, matrix, cameraPos, anchor, selected, ANCHOR_R, ANCHOR_G, ANCHOR_B, 0.75f);
        }
    }

    private void renderDraftPath(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos,
                                 ActorPathController controller) {
        List<Vec3d> anchors = controller.getAnchorPositions();
        List<Vector3f> waypointVectors = controller.getWaypointVectors();
        List<Vec3d> curve = ActorRouteSampler.sampleCurvePositions(
                waypointVectors, controller.getInterpolationMode());
        if (curve.size() >= 2) {
            renderPathLine(consumers, matrix, cameraPos, curve, DRAFT_PATH_R, DRAFT_PATH_G, DRAFT_PATH_B, DRAFT_PATH_A);
        }
        int selected = controller.getSelectedAnchorIndex();
        for (int i = 0; i < anchors.size(); i++) {
            boolean isSelected = i == selected;
            float r = isSelected ? SELECTED_R : ANCHOR_R;
            float g = isSelected ? SELECTED_G : ANCHOR_G;
            float b = isSelected ? SELECTED_B : ANCHOR_B;
            float a = isSelected ? 1.0f : 0.85f;
            renderAnchorBox(consumers, matrix, cameraPos, anchors.get(i), isSelected, r, g, b, a);
        }
    }

    private void renderPathLine(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos,
                                List<Vec3d> points, float r, float g, float b, float a) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d from = points.get(i);
            Vec3d to = points.get(i + 1);
            consumer.vertex(matrix, (float) (from.x - cameraPos.x), (float) (from.y - cameraPos.y), (float) (from.z - cameraPos.z))
                    .color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
            consumer.vertex(matrix, (float) (to.x - cameraPos.x), (float) (to.y - cameraPos.y), (float) (to.z - cameraPos.z))
                    .color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        }
    }

    private void renderAnchorBox(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos,
                                 Vec3d anchor, boolean selected, float r, float g, float b, float a) {
        Box box = new Box(
                anchor.x - ANCHOR_HALF_SIZE, anchor.y - ANCHOR_HALF_SIZE, anchor.z - ANCHOR_HALF_SIZE,
                anchor.x + ANCHOR_HALF_SIZE, anchor.y + ANCHOR_HALF_SIZE, anchor.z + ANCHOR_HALF_SIZE
        ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        emitBoxEdges(consumer, matrix, box, r, g, b, a);
    }

    private static void emitBoxEdges(VertexConsumer consumer, Matrix4f matrix, Box box,
                                     float r, float g, float b, float a) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        line(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
        line(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);
        line(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        consumer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
        consumer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0.0f, 1.0f, 0.0f);
    }
}
