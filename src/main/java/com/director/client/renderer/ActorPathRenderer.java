package com.director.client.renderer;

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
import org.lwjgl.opengl.GL11;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class ActorPathRenderer {

    private static final ActorPathRenderer INSTANCE = new ActorPathRenderer();
    private static final float LINE_WIDTH = 2.5f;
    private static final double ANCHOR_HALF_SIZE = 0.1;
    private static final float PATH_R = 0.2f;
    private static final float PATH_G = 0.85f;
    private static final float PATH_B = 0.35f;
    private static final float PATH_A = 0.9f;
    private static final float ANCHOR_R = 0.95f;
    private static final float ANCHOR_G = 0.55f;
    private static final float ANCHOR_B = 0.1f;
    private static final float SELECTED_R = 1.0f;
    private static final float SELECTED_G = 0.95f;
    private static final float SELECTED_B = 0.2f;

    private boolean enabled = false;

    private ActorPathRenderer() {
    }

    public static ActorPathRenderer getInstance() {
        return INSTANCE;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void render(WorldRenderContext context) {
        ActorPathController controller = ActorPathController.getInstance();
        if (!enabled || !controller.isActive()) {
            return;
        }

        List<Vec3d> anchors = controller.getAnchorPositions();
        if (anchors.isEmpty()) {
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
        ActorRouteClip.InterpolationMode mode = controller.getInterpolationMode();
        List<Vector3f> waypointVectors = controller.getWaypointVectors();
        List<Vec3d> curve = ActorRouteSampler.sampleCurvePositions(waypointVectors, mode);

        MatrixStack matrices = context.matrices();
        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(false);
        RenderSystem.lineWidth(LINE_WIDTH);

        VertexConsumerProvider consumers = context.consumers();
        if (curve.size() >= 2) {
            renderPathLine(consumers, matrix, cameraPos, curve);
        }

        int selected = controller.getSelectedAnchorIndex();
        for (int i = 0; i < anchors.size(); i++) {
            Vec3d anchor = anchors.get(i);
            boolean isSelected = i == selected;
            renderAnchorBox(consumers, matrix, cameraPos, anchor, isSelected);
        }

        GlStateManager._depthMask(true);
        GlStateManager._disableBlend();
        matrices.pop();
    }

    private void renderPathLine(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, List<Vec3d> points) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            consumer.vertex(matrix, (float) (a.x - cameraPos.x), (float) (a.y - cameraPos.y), (float) (a.z - cameraPos.z))
                    .color(PATH_R, PATH_G, PATH_B, PATH_A)
                    .normal(0.0f, 1.0f, 0.0f);
            consumer.vertex(matrix, (float) (b.x - cameraPos.x), (float) (b.y - cameraPos.y), (float) (b.z - cameraPos.z))
                    .color(PATH_R, PATH_G, PATH_B, PATH_A)
                    .normal(0.0f, 1.0f, 0.0f);
        }
    }

    private void renderAnchorBox(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos,
                                 Vec3d anchor, boolean selected) {
        float r = selected ? SELECTED_R : ANCHOR_R;
        float g = selected ? SELECTED_G : ANCHOR_G;
        float b = selected ? SELECTED_B : ANCHOR_B;
        float a = selected ? 1.0f : 0.85f;

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
