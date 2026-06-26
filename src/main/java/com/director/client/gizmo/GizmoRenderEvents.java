package com.director.client.gizmo;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;

import com.director.client.gizmo.handles.AxisHandle;
import com.director.client.input.DirectorInputSystem;
import com.director.ui.cursor.ImGuiCursorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🧭 GizmoRenderEvents - Gizmo 渲染事件
 * 
 * 负责将 Gizmo 集成到 Minecraft 的世界渲染系统中
 * 
 * @author Director Team
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
            // 1.21.10+：使用 END_MAIN 作为“主世界渲染结束”的时机（替代旧的 AFTER_TRANSLUCENT）
            WorldRenderEvents.END_MAIN.register(GizmoRenderEvents::onWorldRenderAfterTranslucent);
            
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
            
            // 更新 Gizmo（交互在 DirectorScreen.tick / tickInteraction 中处理，此处仅渲染）
            double time = client.world != null ? client.world.getTime() : 0.0;
            
            // 渲染 Gizmo
            LOGGER.debug("[GizmoRenderEvents] 调用 gm.render()");
            gm.render(time, cameraPos, context.matrices(), vcp);
            LOGGER.debug("[GizmoRenderEvents] gm.render() 调用完成");
            
        } catch (Exception e) {
            LOGGER.error("[GizmoRenderEvents] ❌ 渲染 Gizmo 失败: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    /**
     * 每帧更新 Gizmo 交互（在 DirectorScreen.tick 中调用，与世界渲染解耦）。
     */
    public static void tickInteraction() {
        try {
            GizmoManager gm = GizmoManager.get();
            if (!gm.isActive()) {
                lastMouseDown = false;
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null || client.getWindow() == null) {
                return;
            }
            if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
                return;
            }

            boolean mouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == 1;
            boolean mouseReleased = lastMouseDown && !mouseDown;
            lastMouseDown = mouseDown;

            DirectorInputSystem.ImGuiRay ray = DirectorInputSystem.computeImGuiRay();
            if (ray == null) {
                return;
            }

            gm.tick(ray.origin(), ray.direction(), mouseDown, mouseReleased);
            updateGizmoCursor(gm);
        } catch (Exception e) {
            LOGGER.warn("[GizmoRenderEvents] Gizmo 交互 tick 失败", e);
        }
    }

    private static void updateGizmoCursor(GizmoManager gm) {
        ImGuiCursorController cursor = ImGuiCursorController.get();
        if (gm == null || !gm.isActive() || gm.getController() == null) {
            cursor.setCursorType(ImGuiCursorController.CursorType.DEFAULT);
            return;
        }
        var controller = gm.getController();
        if (controller.getActiveRotationHandle() != null) {
            cursor.setCursorType(ImGuiCursorController.CursorType.RESIZE_Z);
            return;
        }
        if (controller.getHoveredRotationHandle() != null) {
            cursor.setCursorType(ImGuiCursorController.CursorType.RESIZE_Z);
            return;
        }
        if (controller.getHoveredHandle() instanceof AxisHandle axisHandle) {
            cursor.setCursorByAxis(axisHandle.getAxis());
            return;
        }
        if (controller.getActiveHandle() instanceof AxisHandle activeAxis) {
            cursor.setCursorByAxis(activeAxis.getAxis());
            return;
        }
        if (gm.isHot()) {
            cursor.setCursorType(ImGuiCursorController.CursorType.RESIZE_Z);
            return;
        }
        cursor.setCursorType(ImGuiCursorController.CursorType.DEFAULT);
    }

    /**
     * 计算射线方向（从鼠标位置），在 {@link DirectorInputSystem#computeImGuiRay()} 不可用时回退。
     */
    @SuppressWarnings("unused")
    private static Vec3d calculateRayDirectionFallback(MinecraftClient client) {
        try {
            var hitResult = DirectorInputSystem.getCurrentRay();
            if (hitResult != null && hitResult.getType() == net.minecraft.util.hit.BlockHitResult.Type.BLOCK) {
                Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
                Vec3d hitPos = hitResult.getPos();
                return hitPos.subtract(cameraPos).normalize();
            }
            
            // 回退：使用相机方向
            if (client.getCameraEntity() != null) {
                return client.getCameraEntity().getRotationVecClient();
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

