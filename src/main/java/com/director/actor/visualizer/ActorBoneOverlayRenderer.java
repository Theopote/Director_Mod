package com.director.actor.visualizer;

import com.director.actor.rig.skeleton.RigDefinition;
import com.director.actor.rig.skeleton.RigWorldPose;
import com.director.client.DirectorClientController;
import com.director.ui.actor.pose.ActorBoneSelectionController;
import com.director.ui.actor.pose.ActorBoneWorldQuery;
import com.director.ui.actor.selection.ActorSelectionHub;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

/** Draws the active humanoid rig and highlights its selected/hovered bone. */
@Environment(EnvType.CLIENT)
public final class ActorBoneOverlayRenderer {
    private static final ActorBoneOverlayRenderer INSTANCE = new ActorBoneOverlayRenderer();
    private static final double HANDLE_HALF_SIZE = 0.055;

    private ActorBoneOverlayRenderer() {}
    public static ActorBoneOverlayRenderer getInstance() { return INSTANCE; }

    public void render(WorldRenderContext context) {
        ActorBoneSelectionController selection = ActorBoneSelectionController.getInstance();
        if (!DirectorClientController.getInstance().isDirectorModeActive() || !selection.isActive()
                || context.consumers() == null) return;

        UUID selectedActor = ActorSelectionHub.getSelectedActorId();
        selection.synchronizeActor(selectedActor);
        if (selectedActor == null) return;
        RigWorldPose worldPose = ActorBoneWorldQuery.resolve(selectedActor);
        if (worldPose == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        MatrixStack matrices = context.matrices();
        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        drawHierarchy(lines, matrix, camera, worldPose, selection);
        matrices.pop();
    }

    private static void drawHierarchy(VertexConsumer lines, Matrix4f matrix, Vec3d camera,
                                      RigWorldPose pose, ActorBoneSelectionController selection) {
        RigDefinition rig = pose.rig();
        for (int i = 0; i < rig.boneCount(); i++) {
            Vector3f position = pose.position(i);
            int parent = rig.bone(i).parentIndex();
            if (parent >= 0) {
                Vector3f parentPosition = pose.position(parent);
                line(lines, matrix, camera, parentPosition, position, 0.25f, 0.8f, 1f, 0.9f);
            }

            String id = rig.bone(i).id();
            boolean selected = id.equals(selection.getSelectedBoneId());
            boolean hovered = id.equals(selection.getHoveredBoneId());
            float r = selected ? 1f : hovered ? 1f : 0.2f;
            float g = selected ? 0.85f : hovered ? 1f : 0.75f;
            float b = selected ? 0.15f : hovered ? 0.35f : 1f;
            drawBox(lines, matrix, camera, position, selected ? HANDLE_HALF_SIZE * 1.35 : HANDLE_HALF_SIZE,
                    r, g, b, 1f);
        }
    }

    private static void drawBox(VertexConsumer out, Matrix4f matrix, Vec3d camera, Vector3f p,
                                double half, float r, float g, float b, float a) {
        Box box = new Box(p.x - half, p.y - half, p.z - half,
                p.x + half, p.y + half, p.z + half).offset(-camera.x, -camera.y, -camera.z);
        double[][] v = {
                {box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ},
                {box.maxX, box.minY, box.maxZ}, {box.minX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ},
                {box.maxX, box.maxY, box.maxZ}, {box.minX, box.maxY, box.maxZ}
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) line(out, matrix, v[edge[0]], v[edge[1]], r, g, b, a);
    }

    private static void line(VertexConsumer out, Matrix4f matrix, Vec3d camera,
                             Vector3f from, Vector3f to, float r, float g, float b, float a) {
        line(out, matrix,
                new double[]{from.x - camera.x, from.y - camera.y, from.z - camera.z},
                new double[]{to.x - camera.x, to.y - camera.y, to.z - camera.z}, r, g, b, a);
    }

    private static void line(VertexConsumer out, Matrix4f matrix, double[] from, double[] to,
                             float r, float g, float b, float a) {
        out.vertex(matrix, (float) from[0], (float) from[1], (float) from[2]).color(r, g, b, a).normal(0f, 1f, 0f);
        out.vertex(matrix, (float) to[0], (float) to[1], (float) to[2]).color(r, g, b, a).normal(0f, 1f, 0f);
    }
}
