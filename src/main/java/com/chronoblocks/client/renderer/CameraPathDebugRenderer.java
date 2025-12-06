package com.chronoblocks.client.renderer;

import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraPathManager;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraPathManager.BezierDebugSegment;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * CameraPathDebugRenderer v2.0
 *
 * 在世界中渲染摄像机轨迹，可视化：
 *  - 整体路径（线）
 *  - 关键帧位置（点）
 *  - Bezier 控制柄线段
 *  - 当前播放时间对应的位置（红点）
 *  - 当前摄像机方向箭头
 *
 * 使用方式：
 *  - TimelineCameraController 在构造时注入 pathManager：
 *      CameraPathDebugRenderer.getInstance().setPathManager(pathManager);
 *  - UI 中通过 checkbox 开关：
 *      CameraPathDebugRenderer.getInstance().setEnabled(showCameraPath);
 *  - 每帧 world render 时调用 render(matrixStack, tickDelta, currentTimeSeconds)
 */
@Environment(EnvType.CLIENT)
public final class CameraPathDebugRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPathDebugRenderer.class);
    private static final CameraPathDebugRenderer INSTANCE = new CameraPathDebugRenderer();

    /** 是否渲染调试轨迹（由 UI 开关控制） */
    private boolean enabled = false;

    /** 轨迹数据来源 */
    private TimelineCameraPathManager pathManager;

    /** 是否渲染方向箭头（可扩展 UI 开关） */
    private boolean showDirectionArrow = true;

    /** 轨迹线宽 */
    private static final float LINE_WIDTH = 2.0f;

    /** 关键帧点大小 */
    private static final double KEYFRAME_POINT_SIZE = 0.05;

    /** 当前播放点大小 */
    private static final double CURRENT_POINT_SIZE = 0.08;

    private CameraPathDebugRenderer() {}

    public static CameraPathDebugRenderer getInstance() {
        return INSTANCE;
    }

    // ============ 外部接口 ============

    public void setPathManager(TimelineCameraPathManager pathManager) {
        this.pathManager = pathManager;
        LOGGER.debug("[CameraPathDebugRenderer] pathManager 绑定: {}", pathManager != null);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.debug("[CameraPathDebugRenderer] 轨迹渲染开关: {}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setShowDirectionArrow(boolean showDirectionArrow) {
        this.showDirectionArrow = showDirectionArrow;
    }

    /**
     * 使用 WorldRenderContext 渲染轨迹（用于 Fabric WorldRenderEvents）。
     *
     * @param context WorldRenderContext，包含所有渲染所需信息
     */
    public void render(WorldRenderContext context) {
        if (!enabled || pathManager == null || pathManager.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client != null && client.getRenderTickCounter() != null
                ? client.getRenderTickCounter().getTickDelta(false)
                : 0.0f;
        float currentTimeSeconds = TimelineCameraController.getInstance().getCurrentTimeSeconds();
        render(context.matrixStack(), context.consumers(), tickDelta, currentTimeSeconds);
    }

    /**
     * 在世界渲染阶段调用。
     *
     * @param matrices           当前 world render 的 MatrixStack
     * @param consumers          VertexConsumerProvider
     * @param tickDelta          插值 tick
     * @param currentTimeSeconds 当前时间线时间（秒），用于渲染当前摄像机位置
     */
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta, float currentTimeSeconds) {
        if (!enabled || pathManager == null || pathManager.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || consumers == null) {
            return;
        }

        Vec3d cameraPos;
        if (client.getCameraEntity() != null) {
            cameraPos = client.getCameraEntity().getLerpedPos(tickDelta);
        } else if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            cameraPos = client.gameRenderer.getCamera().getPos();
        } else {
            return;
        }

        // 采样路径点 / 关键帧点 / Bezier 调试段
        List<Vec3d> pathPoints = pathManager.getPathPointsForDebug();
        // 从 keyframes 生成关键帧点列表
        List<Vec3d> keyframePoints = pathManager.getKeyframes().stream()
                .map(kf -> new Vec3d(kf.getPosition().x, kf.getPosition().y, kf.getPosition().z))
                .toList();
        List<BezierDebugSegment> bezierSegments = pathManager.getBezierSegmentsForDebug();

        if (pathPoints.size() < 2 && keyframePoints.isEmpty()) {
            return;
        }

        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.lineWidth(LINE_WIDTH);

        // 1. 渲染轨迹线（整体路径）
        if (pathPoints.size() >= 2) {
            renderPathLine(consumers, matrix, cameraPos, pathPoints);
        }

        // 2. 渲染关键帧点（小十字）
        if (!keyframePoints.isEmpty()) {
            renderKeyframePoints(consumers, matrix, cameraPos, keyframePoints);
        }

        // 3. 渲染 Bezier 控制柄线段（P0-C1-C2-P3）
        if (!bezierSegments.isEmpty()) {
            renderBezierHandles(consumers, matrix, cameraPos, bezierSegments);
        }

        // 4. 渲染当前时间对应的位置 + 方向箭头
        renderCurrentSample(consumers, matrix, cameraPos, currentTimeSeconds);

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
        RenderSystem.disableBlend();

        matrices.pop();
    }

    // ============ 轨迹线渲染 ============

    private void renderPathLine(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, List<Vec3d> pathPoints) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        
        // 淡蓝色线
        float r = 64 / 255.0f;
        float g = 160 / 255.0f;
        float b = 255 / 255.0f;
        float a = 200 / 255.0f;

        // 使用 LINE_STRIP 模式：连续线段
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec3d p0 = pathPoints.get(i);
            Vec3d p1 = pathPoints.get(i + 1);
            
            double x0 = p0.x - cameraPos.x;
            double y0 = p0.y - cameraPos.y;
            double z0 = p0.z - cameraPos.z;
            double x1 = p1.x - cameraPos.x;
            double y1 = p1.y - cameraPos.y;
            double z1 = p1.z - cameraPos.z;
            
            // 计算法线（用于线条渲染）
            double dx = x1 - x0;
            double dy = y1 - y0;
            double dz = z1 - z0;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float nx = len > 1e-6 ? (float)(dx / len) : 0f;
            float ny = len > 1e-6 ? (float)(dy / len) : 1f;
            float nz = len > 1e-6 ? (float)(dz / len) : 0f;
            
            consumer.vertex(matrix, (float) x0, (float) y0, (float) z0)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
            consumer.vertex(matrix, (float) x1, (float) y1, (float) z1)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
        }
    }

    // ============ 关键帧点渲染 ============

    private void renderKeyframePoints(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, List<Vec3d> points) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        // 黄色
        float r = 255 / 255.0f;
        float g = 220 / 255.0f;
        float b = 64 / 255.0f;
        float a = 255 / 255.0f;

        double s = KEYFRAME_POINT_SIZE;

        for (Vec3d p : points) {
            double x = p.x - cameraPos.x;
            double y = p.y - cameraPos.y;
            double z = p.z - cameraPos.z;

            // 画一个小十字（X/Y/Z 三个轴方向的线段）
            // X 轴
            consumer.vertex(matrix, (float) (x - s), (float) y, (float) z).color(r, g, b, a).normal(1f, 0f, 0f);
            consumer.vertex(matrix, (float) (x + s), (float) y, (float) z).color(r, g, b, a).normal(1f, 0f, 0f);
            // Y 轴
            consumer.vertex(matrix, (float) x, (float) (y - s), (float) z).color(r, g, b, a).normal(0f, 1f, 0f);
            consumer.vertex(matrix, (float) x, (float) (y + s), (float) z).color(r, g, b, a).normal(0f, 1f, 0f);
            // Z 轴
            consumer.vertex(matrix, (float) x, (float) y, (float) (z - s)).color(r, g, b, a).normal(0f, 0f, 1f);
            consumer.vertex(matrix, (float) x, (float) y, (float) (z + s)).color(r, g, b, a).normal(0f, 0f, 1f);
        }
    }

    // ============ Bezier 控制柄渲染 ============

    private void renderBezierHandles(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, List<BezierDebugSegment> segments) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        // 控制柄线颜色：青色
        float r = 64 / 255.0f;
        float g = 255 / 255.0f;
        float b = 200 / 255.0f;
        float a = 180 / 255.0f;

        for (BezierDebugSegment seg : segments) {
            Vec3d P0 = seg.p0();
            Vec3d C1 = seg.c1();
            Vec3d C2 = seg.c2();
            Vec3d P3 = seg.p3();

            // P0 - C1
            lineVertexPair(consumer, matrix, cameraPos, P0, C1, r, g, b, a);
            // C1 - C2
            lineVertexPair(consumer, matrix, cameraPos, C1, C2, r, g, b, a);
            // C2 - P3
            lineVertexPair(consumer, matrix, cameraPos, C2, P3, r, g, b, a);
        }
    }

    private void lineVertexPair(VertexConsumer consumer,
                                Matrix4f matrix,
                                Vec3d cameraPos,
                                Vec3d p0,
                                Vec3d p1,
                                float r, float g, float b, float a) {

        double x0 = p0.x - cameraPos.x;
        double y0 = p0.y - cameraPos.y;
        double z0 = p0.z - cameraPos.z;

        double x1 = p1.x - cameraPos.x;
        double y1 = p1.y - cameraPos.y;
        double z1 = p1.z - cameraPos.z;

        // 计算法线
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len > 1e-6 ? (float)(dx / len) : 0f;
        float ny = len > 1e-6 ? (float)(dy / len) : 1f;
        float nz = len > 1e-6 ? (float)(dz / len) : 0f;

        consumer.vertex(matrix, (float) x0, (float) y0, (float) z0).color(r, g, b, a).normal(nx, ny, nz);
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(nx, ny, nz);
    }

    // ============ 当前采样点 & 方向箭头 ============

    private void renderCurrentSample(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, float currentTimeSeconds) {
        Optional<CameraSample> opt = pathManager.sample(currentTimeSeconds);
        if (opt.isEmpty()) {
            return;
        }

        CameraSample sample = opt.get();
        Vec3d worldPos = sample.position();
        double x = worldPos.x - cameraPos.x;
        double y = worldPos.y - cameraPos.y;
        double z = worldPos.z - cameraPos.z;

        // 1. 当前点（红色小十字）
        renderCrossPoint(consumers, matrix, x, y, z,
                64 / 255.0f, 64 / 255.0f);

        // 2. 方向箭头
        if (showDirectionArrow) {
            renderDirectionArrow(consumers, matrix, x, y, z, sample.yaw(), sample.pitch());
        }
    }

    private void renderCrossPoint(VertexConsumerProvider consumers,
                                  Matrix4f matrix,
                                  double x, double y, double z,
                                  float g, float b) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        // X 轴
        consumer.vertex(matrix, (float) (x - CameraPathDebugRenderer.CURRENT_POINT_SIZE), (float) y, (float) z).color((float) 1.0, g, b, (float) 1.0).normal(1f, 0f, 0f);
        consumer.vertex(matrix, (float) (x + CameraPathDebugRenderer.CURRENT_POINT_SIZE), (float) y, (float) z).color((float) 1.0, g, b, (float) 1.0).normal(1f, 0f, 0f);
        // Y 轴
        consumer.vertex(matrix, (float) x, (float) (y - CameraPathDebugRenderer.CURRENT_POINT_SIZE), (float) z).color((float) 1.0, g, b, (float) 1.0).normal(0f, 1f, 0f);
        consumer.vertex(matrix, (float) x, (float) (y + CameraPathDebugRenderer.CURRENT_POINT_SIZE), (float) z).color((float) 1.0, g, b, (float) 1.0).normal(0f, 1f, 0f);
        // Z 轴
        consumer.vertex(matrix, (float) x, (float) y, (float) (z - CameraPathDebugRenderer.CURRENT_POINT_SIZE)).color((float) 1.0, g, b, (float) 1.0).normal(0f, 0f, 1f);
        consumer.vertex(matrix, (float) x, (float) y, (float) (z + CameraPathDebugRenderer.CURRENT_POINT_SIZE)).color((float) 1.0, g, b, (float) 1.0).normal(0f, 0f, 1f);
    }

    /**
     * 渲染当前摄像机方向箭头（简单由 yaw/pitch 推出前向向量）。
     */
    private void renderDirectionArrow(VertexConsumerProvider consumers,
                                      Matrix4f matrix,
                                      double x, double y, double z,
                                      float yawDeg, float pitchDeg) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        // 箭头颜色：青色
        float r = 64 / 255.0f;
        float g = 255 / 255.0f;
        float b = 200 / 255.0f;
        float a = 255 / 255.0f;

        // 箭头长度
        double len = 0.8;

        // Minecraft 中 yaw/pitch 角度转换
        float yawRad   = (float) Math.toRadians(-yawDeg - 180.0f);
        float pitchRad = (float) Math.toRadians(-pitchDeg);

        double dx = MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        double dy = MathHelper.sin(pitchRad);
        double dz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);

        double x2 = x + dx * len;
        double y2 = y + dy * len;
        double z2 = z + dz * len;

        // 计算法线
        float nx = (float) dx;
        float ny = (float) dy;
        float nz = (float) dz;

        // 箭头主体线
        consumer.vertex(matrix, (float) x,  (float) y,  (float) z ).color(r, g, b, a).normal(nx, ny, nz);
        consumer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(nx, ny, nz);

        // 箭头分叉（尾部两个小翅膀）
        // 计算一个简单的侧向向量
        double sideLen = len * 0.25;
        // 使用 yaw + 90 度 生成一个侧向
        float sideYaw = yawRad + (float) Math.toRadians(90.0f);
        double sx = MathHelper.sin(sideYaw) * MathHelper.cos(pitchRad) * sideLen;
        double sz = MathHelper.cos(sideYaw) * MathHelper.cos(pitchRad) * sideLen;
        double sy = 0; // 简化：忽略竖直方向偏移

        Vec3d tip = new Vec3d(x2, y2, z2);
        Vec3d left  = tip.add(-sx, sy, -sz);
        Vec3d right = tip.add(sx, sy, sz);

        // 计算翅膀法线
        double leftDx = left.x - x2;
        double leftDy = left.y - y2;
        double leftDz = left.z - z2;
        double leftLen = Math.sqrt(leftDx * leftDx + leftDy * leftDy + leftDz * leftDz);
        float leftNx = leftLen > 1e-6 ? (float)(leftDx / leftLen) : nx;
        float leftNy = leftLen > 1e-6 ? (float)(leftDy / leftLen) : ny;
        float leftNz = leftLen > 1e-6 ? (float)(leftDz / leftLen) : nz;

        double rightDx = right.x - x2;
        double rightDy = right.y - y2;
        double rightDz = right.z - z2;
        double rightLen = Math.sqrt(rightDx * rightDx + rightDy * rightDy + rightDz * rightDz);
        float rightNx = rightLen > 1e-6 ? (float)(rightDx / rightLen) : nx;
        float rightNy = rightLen > 1e-6 ? (float)(rightDy / rightLen) : ny;
        float rightNz = rightLen > 1e-6 ? (float)(rightDz / rightLen) : nz;

        // 左翅
        consumer.vertex(matrix, (float) left.x,  (float) left.y,  (float) left.z ).color(r, g, b, a).normal(leftNx, leftNy, leftNz);
        consumer.vertex(matrix, (float) x2,      (float) y2,      (float) z2     ).color(r, g, b, a).normal(leftNx, leftNy, leftNz);
        // 右翅
        consumer.vertex(matrix, (float) right.x, (float) right.y, (float) right.z).color(r, g, b, a).normal(rightNx, rightNy, rightNz);
        consumer.vertex(matrix, (float) x2,      (float) y2,      (float) z2     ).color(r, g, b, a).normal(rightNx, rightNy, rightNz);
    }
}

