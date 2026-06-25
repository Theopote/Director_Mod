package com.director.client.renderer;

import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CameraPathClip;
import com.director.timeline.model.camera.path.OrbitPathClip;
import com.director.timeline.model.camera.path.DollyPathClip;
import com.director.timeline.model.camera.path.CranePathClip;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.camera.path.CameraPathEditController;
import com.director.timeline.playback.camera.CameraSample;
import com.director.timeline.playback.camera.TimelineCameraController;
import com.director.timeline.playback.camera.TimelineCameraPathManager;
import com.director.timeline.playback.camera.TimelineCameraPathManager.BezierDebugSegment;
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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Optional;

/**
 * 在世界中渲染摄像机路径轨迹，可视化：
 *  - 整体路径（线）
 *  - 关键帧位置（点）
 *  - Bezier 控制柄线段
 *  - 当前播放时间对应的位置（红点）
 *  - 当前摄像机方向箭头
 * <p>
 * 使用方式：
 *  - TimelineCameraController 在构造时注入 pathManager：
 *      CameraPathRenderer.getInstance().setPathManager(pathManager);
 *  - UI 中通过 checkbox 开关：
 *      CameraPathRenderer.getInstance().setEnabled(showCameraPath);
 *  - 每帧 world render 时调用 render(matrixStack, tickDelta, currentTimeSeconds)
 */
@Environment(EnvType.CLIENT)
public final class CameraPathRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPathRenderer.class);
    private static final CameraPathRenderer INSTANCE = new CameraPathRenderer();

    /** 是否渲染摄像机轨迹（由 UI 开关控制） */
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

    private CameraPathRenderer() {}

    public static CameraPathRenderer getInstance() {
        return INSTANCE;
    }

    // ============ 外部接口 ============

    public void setPathManager(TimelineCameraPathManager pathManager) {
        this.pathManager = pathManager;
        LOGGER.debug("[CameraPathRenderer] pathManager 绑定: {}", pathManager != null);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[CameraPathRenderer] 轨迹渲染开关: {} (pathManager: {}, isEmpty: {})", 
                enabled, 
                pathManager != null, 
                pathManager != null ? pathManager.isEmpty() : "N/A");
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
        if (!enabled) {
            return;
        }
        if (pathManager == null) {
            LOGGER.warn("[CameraPathRenderer] pathManager 为 null，跳过渲染。请确保 TimelineCameraController 已初始化。");
            return;
        }
        
        // ⭐ 检查是否有可渲染的内容（关键帧或围绕轨迹）
        boolean hasKeyframes = !pathManager.isEmpty();
        boolean hasOrbitPaths = hasOrbitPathsToRender();
        
        if (!hasKeyframes && !hasOrbitPaths) {
            LOGGER.debug("[CameraPathRenderer] 没有可渲染的数据（关键帧: {}, 围绕轨迹: {}）", false, false);
            return;
        }
        
        LOGGER.debug("[CameraPathRenderer] 开始渲染轨迹（关键帧数: {}, 围绕轨迹: {}）", 
                pathManager.getKeyframes().size(), hasOrbitPaths);
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client != null && client.getRenderTickCounter() != null
                ? client.getRenderTickCounter().getTickProgress(false)
                : 0.0f;
        float currentTimeSeconds = TimelineCameraController.getInstance().getCurrentTimeSeconds();
        render(context.matrices(), context.consumers(), tickDelta, currentTimeSeconds);
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
        if (!enabled) {
            return;
        }
        
        // ⭐ 检查是否有可渲染的内容（关键帧或围绕轨迹）
        boolean hasKeyframes = pathManager != null && !pathManager.isEmpty();
        boolean hasOrbitPaths = hasOrbitPathsToRender();
        
        if (!hasKeyframes && !hasOrbitPaths) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || consumers == null) {
            return;
        }

        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();

        // 采样路径点 / 关键帧点 / Bezier 调试段
        List<Vec3d> pathPoints = pathManager != null ? pathManager.getPathPointsForDebug() : new java.util.ArrayList<>();
        List<CameraKeyframe> keyframes = pathManager != null ? pathManager.getKeyframes() : new java.util.ArrayList<>();
        List<BezierDebugSegment> bezierSegments = pathManager != null ? pathManager.getBezierSegmentsForDebug() : new java.util.ArrayList<>();

        // ⭐ 如果既没有路径点也没有关键帧，且没有围绕轨迹，则不渲染
        // 注意：即使没有关键帧，如果有围绕轨迹，也应该继续渲染
        if (pathPoints.size() < 2 && keyframes.isEmpty() && !hasOrbitPaths) {
            LOGGER.debug("[CameraPathRenderer] 没有可渲染的数据（路径点: {}, 关键帧: {}, 围绕轨迹: {}）",
                    pathPoints.size(), 0, false);
            return;
        }

        matrices.push();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._enableDepthTest();
        RenderSystem.lineWidth(LINE_WIDTH);

        // 1. 渲染轨迹线（整体路径）- 至少需要2个点才能画线
        if (pathPoints.size() >= 2) {
            renderPathLine(consumers, matrix, cameraPos, pathPoints);
        }

        // 2. 渲染关键帧点（小十字 + 朝向短线）- 即使只有一个关键帧也渲染
        if (!keyframes.isEmpty()) {
            renderKeyframePoints(consumers, matrix, cameraPos, keyframes);
        }

        // 3. 渲染 Bezier 控制柄线段（P0-C1-C2-P3）
        if (!bezierSegments.isEmpty()) {
            renderBezierHandles(consumers, matrix, cameraPos, bezierSegments);
        }

        // 4. 渲染当前时间对应的位置 + 方向箭头
        renderCurrentSample(consumers, matrix, cameraPos, currentTimeSeconds);

        // 5. ⭐ 渲染路径片段（OrbitPathClip, DollyPathClip, CranePathClip）
        renderPathClips(consumers, matrix, cameraPos);

        // WorldRenderContext.consumers() is shared across the render phase.
        // Let Fabric/FrameGraph flush it at the phase boundary instead of
        // forcing an early draw that can invalidate later renderers' buffers.
        GlStateManager._disableBlend();

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

    private void renderKeyframePoints(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, List<CameraKeyframe> keyframes) {
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());

        // 关键帧点颜色：黄色
        float pointR = 255 / 255.0f;
        float pointG = 220 / 255.0f;
        float pointB = 64 / 255.0f;
        float pointA = 255 / 255.0f;

        // 朝向线颜色：橙色（更明显）
        float dirR = 255 / 255.0f;
        float dirG = 165 / 255.0f;
        float dirB = 0 / 255.0f;
        float dirA = 255 / 255.0f;

        // 选中点颜色：青蓝，便于与未选中黄点区分
        float selectedR = 64 / 255.0f;
        float selectedG = 225 / 255.0f;
        float selectedB = 255 / 255.0f;
        float selectedA = 255 / 255.0f;

        double s = KEYFRAME_POINT_SIZE;
        double dirLen = 0.5; // 朝向线长度

        for (CameraKeyframe kf : keyframes) {
            boolean selected = CameraPathEditController.getInstance().isKeyframeSelected(kf);
            org.joml.Vector3f pos = kf.getPosition();
            org.joml.Vector3f rot = kf.getRotation();
            
            double x = pos.x - cameraPos.x;
            double y = pos.y - cameraPos.y;
            double z = pos.z - cameraPos.z;

            // 1. 画关键帧位置点（小十字）
            // X 轴
                consumer.vertex(matrix, (float) (x - s), (float) y, (float) z)
                    .color(selected ? selectedR : pointR, selected ? selectedG : pointG, selected ? selectedB : pointB, selected ? selectedA : pointA)
                    .normal(1f, 0f, 0f);
                consumer.vertex(matrix, (float) (x + s), (float) y, (float) z)
                    .color(selected ? selectedR : pointR, selected ? selectedG : pointG, selected ? selectedB : pointB, selected ? selectedA : pointA)
                    .normal(1f, 0f, 0f);
            // Y 轴
                consumer.vertex(matrix, (float) x, (float) (y - s), (float) z)
                    .color(selected ? selectedR : pointR, selected ? selectedG : pointG, selected ? selectedB : pointB, selected ? selectedA : pointA)
                    .normal(0f, 1f, 0f);
                consumer.vertex(matrix, (float) x, (float) (y + s), (float) z)
                    .color(selected ? selectedR : pointR, selected ? selectedG : pointG, selected ? selectedB : pointB, selected ? selectedA : pointA)
                    .normal(0f, 1f, 0f);
            // Z 轴
                consumer.vertex(matrix, (float) x, (float) y, (float) (z - s))
                    .color(selected ? selectedR : pointR, selected ? selectedG : pointG, selected ? selectedB : pointB, selected ? selectedA : pointA)
                    .normal(0f, 0f, 1f);
                consumer.vertex(matrix, (float) x, (float) y, (float) (z + s))
                    .color(selected ? selectedR : pointR, selected ? selectedG : pointG, selected ? selectedB : pointB, selected ? selectedA : pointA)
                    .normal(0f, 0f, 1f);

            // 2. 画朝向短线（根据 pitch/yaw 计算前向向量）
            // Minecraft 的旋转：pitch (x), yaw (y), roll (z)
            // 正确的 Minecraft yaw/pitch 到前向向量转换
            float yawDeg = rot.y;   // yaw: 0度指向正Z方向（南方），逆时针为正
            float pitchDeg = rot.x; // pitch: 0度为水平，向上为正（-90到90度）
            
            // 转换为弧度
            double yawRad = Math.toRadians(yawDeg);
            double pitchRad = Math.toRadians(pitchDeg);
            
            // 计算前向向量（Minecraft 标准坐标系）
            // x = -sin(yaw) * cos(pitch)
            // y = -sin(pitch)
            // z = cos(yaw) * cos(pitch)
            double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
            double dy = -Math.sin(pitchRad);
            double dz = Math.cos(yawRad) * Math.cos(pitchRad);
            
            // 朝向线的终点
            double xEnd = x + dx * dirLen;
            double yEnd = y + dy * dirLen;
            double zEnd = z + dz * dirLen;
            
            // 计算朝向线的法线
            float nx = (float) dx;
            float ny = (float) dy;
            float nz = (float) dz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                nx = 0f;
                ny = 1f;
                nz = 0f;
            }
            
            // 绘制朝向线
            consumer.vertex(matrix, (float) x, (float) y, (float) z).color(dirR, dirG, dirB, dirA).normal(nx, ny, nz);
            consumer.vertex(matrix, (float) xEnd, (float) yEnd, (float) zEnd).color(dirR, dirG, dirB, dirA).normal(nx, ny, nz);
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
        consumer.vertex(matrix, (float) (x - CameraPathRenderer.CURRENT_POINT_SIZE), (float) y, (float) z).color((float) 1.0, g, b, (float) 1.0).normal(1f, 0f, 0f);
        consumer.vertex(matrix, (float) (x + CameraPathRenderer.CURRENT_POINT_SIZE), (float) y, (float) z).color((float) 1.0, g, b, (float) 1.0).normal(1f, 0f, 0f);
        // Y 轴
        consumer.vertex(matrix, (float) x, (float) (y - CameraPathRenderer.CURRENT_POINT_SIZE), (float) z).color((float) 1.0, g, b, (float) 1.0).normal(0f, 1f, 0f);
        consumer.vertex(matrix, (float) x, (float) (y + CameraPathRenderer.CURRENT_POINT_SIZE), (float) z).color((float) 1.0, g, b, (float) 1.0).normal(0f, 1f, 0f);
        // Z 轴
        consumer.vertex(matrix, (float) x, (float) y, (float) (z - CameraPathRenderer.CURRENT_POINT_SIZE)).color((float) 1.0, g, b, (float) 1.0).normal(0f, 0f, 1f);
        consumer.vertex(matrix, (float) x, (float) y, (float) (z + CameraPathRenderer.CURRENT_POINT_SIZE)).color((float) 1.0, g, b, (float) 1.0).normal(0f, 0f, 1f);
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

        // Minecraft 中 yaw/pitch 到前向向量的正确转换
        // yaw: 0度指向正Z方向（南方），逆时针为正
        // pitch: 0度为水平，向上为正（-90到90度）
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);

        // 计算前向向量（Minecraft 标准坐标系）
        // x = -sin(yaw) * cos(pitch)
        // y = -sin(pitch)
        // z = cos(yaw) * cos(pitch)
        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);

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
        double sideYaw = yawRad + Math.toRadians(90.0);
        double sx = -Math.sin(sideYaw) * Math.cos(pitchRad) * sideLen;
        double sz = Math.cos(sideYaw) * Math.cos(pitchRad) * sideLen;
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

    // ============ 围绕轨迹渲染 ============

    /**
     * ⭐ 检查是否有围绕轨迹需要渲染
     */
    private boolean hasOrbitPathsToRender() {
        TimelineCameraController controller = TimelineCameraController.getInstance();

        TimelineTrack activeTrack = controller.getActiveCameraTrack();
        if (activeTrack == null || !activeTrack.hasCameraPathClips()) {
            return false;
        }

        List<CameraPathClip> pathClips = activeTrack.getCameraPathClips();
        for (CameraPathClip clip : pathClips) {
            if (clip instanceof OrbitPathClip) {
                return true;
            }
        }
        return false;
    }

    /**
     * ⭐ 渲染所有 PathClip（Orbit, Dolly, Crane）
     */
    private void renderPathClips(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos) {
        TimelineCameraController controller = TimelineCameraController.getInstance();

        TimelineTrack activeTrack = controller.getActiveCameraTrack();
        if (activeTrack == null) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[CameraPathRenderer] 没有激活的摄像机轨道，跳过路径片段渲染");
            }
            return;
        }
        
        if (!activeTrack.hasCameraPathClips()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[CameraPathRenderer] 轨道 {} 没有 PathClips，跳过路径片段渲染", activeTrack.getName());
            }
            return;
        }

        List<CameraPathClip> pathClips = activeTrack.getCameraPathClips();
        int orbitCount = 0;
        int dollyCount = 0;
        int craneCount = 0;
        
        for (CameraPathClip clip : pathClips) {
            if (clip instanceof OrbitPathClip orbitClip) {
                orbitCount++;
                renderOrbitPath(consumers, matrix, cameraPos, orbitClip);
            } else if (clip instanceof DollyPathClip dollyClip) {
                dollyCount++;
                renderDollyPath(consumers, matrix, cameraPos, dollyClip);
            } else if (clip instanceof CranePathClip craneClip) {
                craneCount++;
                renderCranePath(consumers, matrix, cameraPos, craneClip);
            }
        }
        
        if (LOGGER.isDebugEnabled() && (orbitCount > 0 || dollyCount > 0 || craneCount > 0)) {
            LOGGER.debug("[CameraPathRenderer] 已渲染 {} 个围绕轨迹, {} 个推拉轨迹, {} 个升降轨迹", 
                    orbitCount, dollyCount, craneCount);
        }
    }

    /**
     * 渲染单个 OrbitPathClip 的围绕轨迹
     */
    private void renderOrbitPath(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, OrbitPathClip clip) {
        List<Vec3d> orbitPoints = clip.samplePathPositions();
        if (orbitPoints.size() < 2) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[CameraPathRenderer] OrbitPathClip 采样点不足 ({} 个点)，跳过渲染", orbitPoints.size());
            }
            return;
        }
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[CameraPathRenderer] 渲染围绕轨迹: {} 个点, center=({:.2f}, {:.2f}, {:.2f}), radius={:.2f}",
                    orbitPoints.size(), clip.getCenter().x, clip.getCenter().y, clip.getCenter().z, clip.getRadius());
        }

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        
        // 围绕轨迹颜色：绿色（与关键帧路径的蓝色区分）
        float r = 64 / 255.0f;
        float g = 255 / 255.0f;
        float b = 64 / 255.0f;
        float a = 200 / 255.0f;

        // 渲染围绕轨迹线
        for (int i = 0; i < orbitPoints.size() - 1; i++) {
            Vec3d p0 = orbitPoints.get(i);
            Vec3d p1 = orbitPoints.get(i + 1);
            
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
            
            consumer.vertex(matrix, (float) x0, (float) y0, (float) z0)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
            consumer.vertex(matrix, (float) x1, (float) y1, (float) z1)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
        }

        // 渲染中心点与辅助连线（更易辨认）
        boolean centerSelected = CameraPathEditController.getInstance().isOrbitCenterSelected(clip);
        renderOrbitCenterMarker(consumer, matrix, cameraPos, clip, orbitPoints, centerSelected);

        Vec3d center = clip.getCenter();
        Vec3d lookAt = clip.getLookAt();
        if (!lookAt.equals(center)) {
            float lookAtR = 255 / 255.0f;
            float lookAtG = 200 / 255.0f;
            float lookAtB = 0 / 255.0f;
            float lookAtA = 255 / 255.0f;
            double lookAtSize = 0.08;

            double lx = lookAt.x - cameraPos.x;
            double ly = lookAt.y - cameraPos.y;
            double lz = lookAt.z - cameraPos.z;

            consumer.vertex(matrix, (float)(lx - lookAtSize), (float) ly, (float) lz).color(lookAtR, lookAtG, lookAtB, lookAtA).normal(1f, 0f, 0f);
            consumer.vertex(matrix, (float)(lx + lookAtSize), (float) ly, (float) lz).color(lookAtR, lookAtG, lookAtB, lookAtA).normal(1f, 0f, 0f);
            consumer.vertex(matrix, (float) lx, (float)(ly - lookAtSize), (float) lz).color(lookAtR, lookAtG, lookAtB, lookAtA).normal(0f, 1f, 0f);
            consumer.vertex(matrix, (float) lx, (float)(ly + lookAtSize), (float) lz).color(lookAtR, lookAtG, lookAtB, lookAtA).normal(0f, 1f, 0f);
            consumer.vertex(matrix, (float) lx, (float) ly, (float)(lz - lookAtSize)).color(lookAtR, lookAtG, lookAtB, lookAtA).normal(0f, 0f, 1f);
            consumer.vertex(matrix, (float) lx, (float) ly, (float)(lz + lookAtSize)).color(lookAtR, lookAtG, lookAtB, lookAtA).normal(0f, 0f, 1f);
        }
    }

    private void renderOrbitCenterMarker(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3d cameraPos,
            OrbitPathClip clip,
            List<Vec3d> orbitPoints,
            boolean centerSelected) {
        Vec3d center = clip.getCenter();
        double cx = center.x - cameraPos.x;
        double cy = center.y - cameraPos.y;
        double cz = center.z - cameraPos.z;
        double distToCam = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double centerSize = Math.max(0.35, Math.min(1.2, distToCam * 0.025));
        if (centerSelected) {
            centerSize *= 1.25;
        }

        float centerR = centerSelected ? 0 / 255.0f : 255 / 255.0f;
        float centerG = centerSelected ? 255 / 255.0f : 255 / 255.0f;
        float centerB = centerSelected ? 255 / 255.0f : 0 / 255.0f;
        float centerA = 255 / 255.0f;

        drawCross(consumer, matrix, cx, cy, cz, centerSize, centerR, centerG, centerB, centerA);

        // 垂直指示线
        double pole = Math.max(0.75, clip.getRadius() * 0.2);
        drawLine(consumer, matrix, cx, cy, cz, cx, cy + pole, cz, centerR, centerG, centerB, 180 / 255.0f);

        // 中心到轨迹的辐条（起点 + 中点）
        float spokeR = 255 / 255.0f;
        float spokeG = 220 / 255.0f;
        float spokeB = 64 / 255.0f;
        float spokeA = 180 / 255.0f;
        Vec3d ringStart = orbitPoints.getFirst();
        drawWorldLine(consumer, matrix, cameraPos, center, ringStart, spokeR, spokeG, spokeB, spokeA);
        if (orbitPoints.size() > 2) {
            Vec3d ringMid = orbitPoints.get(orbitPoints.size() / 2);
            drawWorldLine(consumer, matrix, cameraPos, center, ringMid, spokeR, spokeG, spokeB, spokeA);
        }
    }

    private static void drawCross(
            VertexConsumer consumer, Matrix4f matrix,
            double x, double y, double z, double size,
            float r, float g, float b, float a) {
        drawLine(consumer, matrix, x - size, y, z, x + size, y, z, r, g, b, a);
        drawLine(consumer, matrix, x, y - size, z, x, y + size, z, r, g, b, a);
        drawLine(consumer, matrix, x, y, z - size, x, y, z + size, r, g, b, a);
    }

    private static void drawWorldLine(
            VertexConsumer consumer, Matrix4f matrix, Vec3d cameraPos,
            Vec3d from, Vec3d to,
            float r, float g, float b, float a) {
        drawLine(
                consumer, matrix,
                from.x - cameraPos.x, from.y - cameraPos.y, from.z - cameraPos.z,
                to.x - cameraPos.x, to.y - cameraPos.y, to.z - cameraPos.z,
                r, g, b, a);
    }

    private static void drawLine(
            VertexConsumer consumer, Matrix4f matrix,
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float r, float g, float b, float a) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = len > 1e-6 ? (float) (dx / len) : 0f;
        float ny = len > 1e-6 ? (float) (dy / len) : 1f;
        float nz = len > 1e-6 ? (float) (dz / len) : 0f;
        consumer.vertex(matrix, (float) x0, (float) y0, (float) z0).color(r, g, b, a).normal(nx, ny, nz);
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(nx, ny, nz);
    }

    /**
     * 渲染单个 DollyPathClip 的推拉轨迹
     */
    private void renderDollyPath(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, DollyPathClip clip) {
        // 采样推拉路径的点
        List<Vec3d> dollyPoints = sampleDollyPath(clip);
        if (dollyPoints.size() < 2) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[CameraPathRenderer] DollyPathClip 采样点不足 ({} 个点)，跳过渲染", dollyPoints.size());
            }
            return;
        }
        
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        
        // 推拉轨迹颜色：绿色（与 Orbit 的绿色区分，使用更亮的绿色）
        float r = 100 / 255.0f;
        float g = 255 / 255.0f;
        float b = 150 / 255.0f;
        float a = 200 / 255.0f;
        
        // 渲染推拉轨迹线
        for (int i = 0; i < dollyPoints.size() - 1; i++) {
            Vec3d p0 = dollyPoints.get(i);
            Vec3d p1 = dollyPoints.get(i + 1);
            
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
            
            consumer.vertex(matrix, (float) x0, (float) y0, (float) z0)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
            consumer.vertex(matrix, (float) x1, (float) y1, (float) z1)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
        }
        
        // 渲染起始点、终点与方向箭头
        Vec3d startPos = clip.getStartPosition();
        Vec3d endPos = clip.getEndPosition();
        Vec3d dollyDir = clip.getDollyDirection();

        if (startPos != null) {
            boolean startSelected = CameraPathEditController.getInstance()
                    .isLinearEndpointSelected(clip, CameraPathEditController.LinearEndpoint.START);
            renderLinearEndpointMarker(consumer, matrix, cameraPos, startPos, startSelected,
                    0 / 255.0f, 255 / 255.0f, 255 / 255.0f);
        }
        if (endPos != null) {
            boolean endSelected = CameraPathEditController.getInstance()
                    .isLinearEndpointSelected(clip, CameraPathEditController.LinearEndpoint.END);
            renderLinearEndpointMarker(consumer, matrix, cameraPos, endPos, endSelected,
                    100 / 255.0f, 255 / 255.0f, 200 / 255.0f);
        }

        if (startPos != null && dollyDir != null) {
            float arrowR = 0 / 255.0f;
            float arrowG = 255 / 255.0f;
            float arrowB = 255 / 255.0f;
            float arrowA = 255 / 255.0f;

            double sx = startPos.x - cameraPos.x;
            double sy = startPos.y - cameraPos.y;
            double sz = startPos.z - cameraPos.z;

            double arrowLength = 2.0;
            Vec3d arrowEnd = startPos.add(dollyDir.multiply(arrowLength));
            double ex = arrowEnd.x - cameraPos.x;
            double ey = arrowEnd.y - cameraPos.y;
            double ez = arrowEnd.z - cameraPos.z;

            drawLine(consumer, matrix, sx, sy, sz, ex, ey, ez, arrowR, arrowG, arrowB, arrowA);
        }
    }
    
    /**
     * 采样 DollyPathClip 的路径点
     */
    private List<Vec3d> sampleDollyPath(DollyPathClip clip) {
        List<Vec3d> points = new java.util.ArrayList<>();
        
        float startTime = clip.getStartTime();
        float endTime = clip.getEndTime();
        float duration = endTime - startTime;
        
        if (duration <= 0) {
            return points;
        }
        
        // 采样点数：根据时长决定
        int sampleCount = Math.max(16, (int)(duration * 10));
        
        for (int i = 0; i <= sampleCount; i++) {
            float t = (float) i / sampleCount;
            float timeSeconds = startTime + t * duration;
            
            var sample = clip.sample(timeSeconds);
            if (sample != null) {
                points.add(sample.getPosition());
            }
        }
        
        return points;
    }
    
    /**
     * 渲染单个 CranePathClip 的升降轨迹
     */
    private void renderCranePath(VertexConsumerProvider consumers, Matrix4f matrix, Vec3d cameraPos, CranePathClip clip) {
        // 采样升降路径的点
        List<Vec3d> cranePoints = sampleCranePath(clip);
        if (cranePoints.size() < 2) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[CameraPathRenderer] CranePathClip 采样点不足 ({} 个点)，跳过渲染", cranePoints.size());
            }
            return;
        }
        
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        
        // 升降轨迹颜色：橙色
        float r = 255 / 255.0f;
        float g = 180 / 255.0f;
        float b = 100 / 255.0f;
        float a = 200 / 255.0f;
        
        // 渲染升降轨迹线
        for (int i = 0; i < cranePoints.size() - 1; i++) {
            Vec3d p0 = cranePoints.get(i);
            Vec3d p1 = cranePoints.get(i + 1);
            
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
            
            consumer.vertex(matrix, (float) x0, (float) y0, (float) z0)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
            consumer.vertex(matrix, (float) x1, (float) y1, (float) z1)
                    .color(r, g, b, a)
                    .normal(nx, ny, nz);
        }
        
        // 渲染起始点与终点
        Vec3d startPos = clip.getStartPosition();
        Vec3d endPos = clip.getEndPosition();
        if (startPos != null) {
            boolean startSelected = CameraPathEditController.getInstance()
                    .isLinearEndpointSelected(clip, CameraPathEditController.LinearEndpoint.START);
            renderLinearEndpointMarker(consumer, matrix, cameraPos, startPos, startSelected,
                    255 / 255.0f, 200 / 255.0f, 0 / 255.0f);
        }
        if (endPos != null) {
            boolean endSelected = CameraPathEditController.getInstance()
                    .isLinearEndpointSelected(clip, CameraPathEditController.LinearEndpoint.END);
            renderLinearEndpointMarker(consumer, matrix, cameraPos, endPos, endSelected,
                    255 / 255.0f, 140 / 255.0f, 80 / 255.0f);
        }
    }

    private void renderLinearEndpointMarker(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vec3d cameraPos,
            Vec3d worldPos,
            boolean selected,
            float baseR, float baseG, float baseB) {
        double cx = worldPos.x - cameraPos.x;
        double cy = worldPos.y - cameraPos.y;
        double cz = worldPos.z - cameraPos.z;
        double distToCam = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double size = Math.max(0.35, Math.min(1.2, distToCam * 0.025));
        if (selected) {
            size *= 1.25;
        }

        float r = selected ? 0 / 255.0f : baseR;
        float g = selected ? 255 / 255.0f : baseG;
        float b = selected ? 255 / 255.0f : baseB;
        float a = 255 / 255.0f;
        drawCross(consumer, matrix, cx, cy, cz, size, r, g, b, a);
    }
    
    /**
     * 采样 CranePathClip 的路径点
     */
    private List<Vec3d> sampleCranePath(CranePathClip clip) {
        List<Vec3d> points = new java.util.ArrayList<>();
        
        float startTime = clip.getStartTime();
        float endTime = clip.getEndTime();
        float duration = endTime - startTime;
        
        if (duration <= 0) {
            return points;
        }
        
        // 采样点数：根据时长决定
        int sampleCount = Math.max(16, (int)(duration * 10));
        
        for (int i = 0; i <= sampleCount; i++) {
            float t = (float) i / sampleCount;
            float timeSeconds = startTime + t * duration;
            
            var sample = clip.sample(timeSeconds);
            if (sample != null) {
                points.add(sample.getPosition());
            }
        }
        
        return points;
    }
}

