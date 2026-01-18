package com.chronoblocks.actor.visualizer;

import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.actor.preview.ActorPreviewManager;
import net.minecraft.entity.Entity;
import com.chronoblocks.ui.actor.selection.ActorSelectionHub;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntityClip;
import com.chronoblocks.ui.timeline.model.entity.EntityLookAtClip;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ActorLookAtVisualizer v1.0
 *
 * <p>编辑态辅助可视化：
 * <ul>
 *   <li>演员 → 目标点 的视线线段</li>
 *   <li>目标点 Marker（可见、可选、可拖）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>❌ 不修改任何数据（只读）</li>
 *   <li>✅ 只在编辑态生效</li>
 *   <li>✅ 与 Timeline / Gizmo 完全同步</li>
 *   <li>✅ 0 侵入 Runtime 逻辑</li>
 * </ul>
 *
 * <p>渲染时机：
 * <ul>
 *   <li>只在选中 Actor 且有活跃 LookAtClip 时渲染</li>
 *   <li>使用 WorldRenderEvents.AFTER_ENTITIES 阶段</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorLookAtVisualizer {

    private static final ActorLookAtVisualizer INSTANCE = new ActorLookAtVisualizer();

    public static ActorLookAtVisualizer getInstance() {
        return INSTANCE;
    }

    /** 上次渲染的 Marker 世界坐标（供 Drag Controller 使用） */
    private Vec3d lastMarkerWorldPos = null;

    private ActorLookAtVisualizer() {
    }

    /**
     * 获取上次渲染的 Marker 世界坐标（只读）
     * <p>供 MarkerDragController 用于拾取判定
     *
     * @return Marker 世界坐标，如果未渲染则返回 null
     */
    public Vec3d getLastMarkerWorldPos() {
        return lastMarkerWorldPos;
    }

    // =========================================================
    // Render Entry
    // =========================================================

    /**
     * 渲染 LookAt 可视化
     *
     * @param matrices MatrixStack
     * @param tickDelta 帧间插值
     * @param vcp VertexConsumerProvider
     */
    public void render(MatrixStack matrices, float tickDelta, VertexConsumerProvider vcp) {
        // 1️⃣ 检查是否有选中的 Actor
        UUID actorId = ActorSelectionHub.getSelectedActorId();
        if (actorId == null) {
            return;
        }

        // 2️⃣ 获取 TimelineModel
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            return;
        }

        // 3️⃣ 获取预览实体
        Entity actor = ActorPreviewManager.getInstance().getPreview(actorId);
        if (actor == null) {
            return;
        }

        // 4️⃣ 查找活跃的 LookAtClip
        EntityLookAtClip clip = findActiveLookAtClip(model, actorId);
        if (clip == null) {
            return;
        }

        // 5️⃣ 解析起点（演员眼睛位置）
        Vec3d from = getActorEyePos(actor);

        // 6️⃣ 解析终点（目标点）
        Vec3d to = resolveTargetPoint(clip);
        if (to == null) {
            return;
        }

        // 7️⃣ 渲染视线线段和目标 Marker
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        renderLookAtLine(vcp, matrix, from, to, cameraPos, clip.getStrength());
        renderTargetMarker(vcp, matrix, to, cameraPos);
    }

    // =========================================================
    // Clip Resolve
    // =========================================================

    /**
     * 查找活跃的 LookAtClip（last-wins 规则）
     *
     * @param model TimelineModel
     * @param actorId Actor ID
     * @return LookAtClip，如果未找到则返回 null
     */
    private EntityLookAtClip findActiveLookAtClip(TimelineModel model, UUID actorId) {
        float time = model.getCurrentTime();

        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ENTITY) {
                continue;
            }

            EntityTrack entityTrack = track.getEntityTrackPayload();
            if (entityTrack == null) {
                continue;
            }

            ActorRef ref = entityTrack.getActorRef();
            if (ref == null || !Objects.equals(ref.getActorId(), actorId)) {
                continue;
            }

            // 找到覆盖当前时间的 LookAtClip（last-wins）
            EntityLookAtClip best = null;
            List<EntityClip> clips = entityTrack.getClips();
            for (EntityClip c : clips) {
                if (c instanceof EntityLookAtClip lc && !lc.isMuted()) {
                    if (time >= lc.getStartTime() && time <= lc.getEndTime()) {
                        if (best == null || lc.getStartTime() >= best.getStartTime()) {
                            best = lc;
                        }
                    }
                }
            }

            return best;
        }

        return null;
    }

    /**
     * 解析目标点
     *
     * @param clip LookAtClip
     * @return 目标点世界坐标，如果无法解析则返回 null
     */
    private Vec3d resolveTargetPoint(EntityLookAtClip clip) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }

        return switch (clip.getMode()) {
            case WORLD_POINT -> new Vec3d(
                    clip.getWorldPoint().x,
                    clip.getWorldPoint().y,
                    clip.getWorldPoint().z
            );
            case CAMERA -> {
                var camera = client.gameRenderer.getCamera();
                if (camera != null) {
                    yield camera.getPos();
                }
                yield null;
            }
            case ENTITY_UUID -> // TODO: 未来实现实体目标
                    null;
        };
    }

    /**
     * 获取演员眼睛位置
     *
     * @param actor Entity
     * @return 眼睛位置（世界坐标）
     */
    private Vec3d getActorEyePos(Entity actor) {
        // Entity.getEyePos() 返回眼睛位置（考虑眼高）
        return actor.getEyePos();
    }

    // =========================================================
    // Rendering
    // =========================================================

    /**
     * 渲染视线线段
     *
     * @param vcp VertexConsumerProvider
     * @param matrix 变换矩阵
     * @param from 起点（世界坐标）
     * @param to 终点（世界坐标）
     * @param cameraPos 摄像机位置（用于坐标转换）
     * @param strength 跟随强度（影响透明度）
     */
    private void renderLookAtLine(
            VertexConsumerProvider vcp,
            Matrix4f matrix,
            Vec3d from,
            Vec3d to,
            Vec3d cameraPos,
            float strength
    ) {
        // 转换为相对摄像机坐标
        Vec3d a = from.subtract(cameraPos);
        Vec3d b = to.subtract(cameraPos);

        // 计算透明度（根据 strength）
        float alpha = 0.3f + 0.7f * strength;

        // 获取线条渲染层
        VertexConsumer consumer = vcp.getBuffer(RenderLayer.getLines());

        // 计算法线（用于线条渲染）
        Vec3d dir = b.subtract(a);
        double length = dir.length();
        float nx, ny, nz;
        if (length > 1e-6) {
            nx = (float) (dir.x / length);
            ny = (float) (dir.y / length);
            nz = (float) (dir.z / length);
        } else {
            nx = 0.0f;
            ny = 1.0f;
            nz = 0.0f;
        }

        // 淡蓝色线条
        float r = 0.8f;
        float g = 0.9f;
        float blue = 1.0f;

        // 绘制线段
        consumer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z)
                .color(r, g, blue, alpha)
                .normal(nx, ny, nz);
        consumer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z)
                .color(r, g, blue, alpha)
                .normal(nx, ny, nz);
    }

    /**
     * 渲染目标点 Marker（十字标记）
     *
     * @param vcp VertexConsumerProvider
     * @param matrix 变换矩阵
     * @param worldPos 目标点（世界坐标）
     * @param cameraPos 摄像机位置（用于坐标转换）
     */
    private void renderTargetMarker(
            VertexConsumerProvider vcp,
            Matrix4f matrix,
            Vec3d worldPos,
            Vec3d cameraPos
    ) {
        // 转换为相对摄像机坐标
        Vec3d p = worldPos.subtract(cameraPos);

        // Marker 大小
        float size = 0.15f;

        // 获取线条渲染层
        VertexConsumer consumer = vcp.getBuffer(RenderLayer.getLines());

        // 橙色 Marker
        float r = 1.0f;
        float g = 0.6f;
        float b = 0.2f;
        float a = 0.9f;

        // 绘制十字（X 轴和 Y 轴）
        // X 轴
        consumer.vertex(matrix, (float) (p.x - size), (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .normal(1.0f, 0.0f, 0.0f);
        consumer.vertex(matrix, (float) (p.x + size), (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .normal(1.0f, 0.0f, 0.0f);

        // Y 轴
        consumer.vertex(matrix, (float) p.x, (float) (p.y - size), (float) p.z)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f);
        consumer.vertex(matrix, (float) p.x, (float) (p.y + size), (float) p.z)
                .color(r, g, b, a)
                .normal(0.0f, 1.0f, 0.0f);

        // Z 轴（可选，增加深度感）
        consumer.vertex(matrix, (float) p.x, (float) p.y, (float) (p.z - size))
                .color(r, g, b, a)
                .normal(0.0f, 0.0f, 1.0f);
        consumer.vertex(matrix, (float) p.x, (float) p.y, (float) (p.z + size))
                .color(r, g, b, a)
                .normal(0.0f, 0.0f, 1.0f);

        // 保存 Marker 世界坐标（供 Drag Controller 使用）
        this.lastMarkerWorldPos = worldPos;
    }
}

