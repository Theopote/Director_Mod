package com.chronoblocks.client.gizmo;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;

import com.chronoblocks.client.input.ChronoBlocksInputSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🧭 GizmoRenderEvents - Gizmo 渲染事件
 * 
 * 负责将 Gizmo 集成到 Minecraft 的世界渲染系统中
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class GizmoRenderEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(GizmoRenderEvents.class);
    
    /** 是否已注册事件监听器 */
    private static boolean registered = false;
    
    /** 鼠标状态跟踪 */
    private static boolean lastMouseDown = false;
    
    /**
     * 注册 Gizmo 渲染事件
     */
    public static void register() {
        if (registered) {
            LOGGER.warn("[GizmoRenderEvents] 事件监听器已经注册");
            return;
        }
        
        try {
            // ✅ 注册 Gizmo 渲染事件 - 在透明渲染后（AFTER_TRANSLUCENT）
            // Gizmo 需要在线框和填充之后渲染，确保可见
            WorldRenderEvents.AFTER_TRANSLUCENT.register(GizmoRenderEvents::onWorldRenderAfterTranslucent);
            
            registered = true;
            LOGGER.info("[GizmoRenderEvents] Gizmo 渲染事件已注册 (AFTER_TRANSLUCENT)");
            
        } catch (Exception e) {
            LOGGER.error("[GizmoRenderEvents] 注册 Gizmo 渲染事件失败", e);
        }
    }
    
    /**
     * 透明渲染后事件处理 - 渲染 Gizmo（AFTER_TRANSLUCENT）
     */
    private static void onWorldRenderAfterTranslucent(WorldRenderContext context) {
        render(context);
    }

    /**
     * 提供给渲染管线的直接调用入口。
     */
    public static void render(WorldRenderContext context) {
        try {
            GizmoManager gm = GizmoManager.get();
            
            // 检查 Gizmo 是否激活
            if (!gm.isActive()) {
                // LOGGER.debug("[GizmoRenderEvents] Gizmo 未激活，跳过渲染");
                return;
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                LOGGER.warn("[GizmoRenderEvents] Client 为 null");
                return;
            }
            
            if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
                LOGGER.warn("[GizmoRenderEvents] Camera 为 null");
                return;
            }
            
            // ✅ 获取 VertexConsumerProvider
            VertexConsumerProvider vcp = context.consumers();
            if (vcp == null) {
                LOGGER.warn("[GizmoRenderEvents] VertexConsumerProvider 为 null");
                return;
            }
            
            // 获取相机位置
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            
            // 获取 Gizmo 上下文信息（用于调试）
            var gizmoContext = gm.getContext();
            if (gizmoContext != null) {
                LOGGER.debug("[GizmoRenderEvents] ✅ Gizmo 渲染: 类型={}, Box={}, Pivot={}, Active={}", 
                           gizmoContext.getType(), 
                           gizmoContext.getTargetBox(), 
                           gizmoContext.getPivot(),
                           gizmoContext.isActive());
            }
            
            // 计算射线（用于 Gizmo 交互）
            Vec3d rayDir = calculateRayDirection(client);
            
            // 获取鼠标状态
            boolean mouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == 1;
            
            // 检测鼠标释放（从按下到释放的瞬间）
            boolean mouseReleased = lastMouseDown && !mouseDown;
            lastMouseDown = mouseDown;
            
            // 更新 Gizmo（每帧调用）
            double time = client.world != null ? client.world.getTime() : 0.0;
            gm.tick(cameraPos, rayDir, mouseDown, mouseReleased);
            
            // 渲染 Gizmo
            LOGGER.debug("[GizmoRenderEvents] 调用 gm.render()");
            gm.render(time, cameraPos, context.matrixStack(), vcp);
            LOGGER.debug("[GizmoRenderEvents] gm.render() 调用完成");
            
        } catch (Exception e) {
            LOGGER.error("[GizmoRenderEvents] ❌ 渲染 Gizmo 失败: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    /**
     * 计算射线方向（从鼠标位置）
     * 
     * 使用 ChronoBlocksInputSystem 的方法来计算射线
     */
    private static Vec3d calculateRayDirection(MinecraftClient client) {
        try {
            // 尝试使用 ChronoBlocksInputSystem 的射线结果
            var hitResult = ChronoBlocksInputSystem.getCurrentRay();
            if (hitResult != null && hitResult.getType() == net.minecraft.util.hit.BlockHitResult.Type.BLOCK) {
                Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
                Vec3d hitPos = hitResult.getPos();
                return hitPos.subtract(cameraPos).normalize();
            }
            
            // 回退：使用相机方向
            if (client.cameraEntity != null) {
                return client.cameraEntity.getRotationVecClient();
            }
            
            // 最后回退：默认方向
            return new Vec3d(0, 0, 1);
            
        } catch (Exception e) {
            LOGGER.warn("[GizmoRenderEvents] 计算射线方向失败，使用默认方向", e);
            return new Vec3d(0, 0, 1);
        }
    }
    
    /**
     * 检查事件是否已注册
     */
    public static boolean isRegistered() {
        return registered;
    }
}

