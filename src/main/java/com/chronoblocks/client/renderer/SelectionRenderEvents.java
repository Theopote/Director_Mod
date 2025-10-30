package com.chronoblocks.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎯 选择渲染事件处理器
 * 
 * 负责将选择渲染器集成到Minecraft的世界渲染系统中。
 * 使用Fabric API的WorldRenderEvents来在适当的时候渲染选择效果。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionRenderEvents {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionRenderEvents.class);
    
    /** 选择渲染器 */
    private static final SelectionRenderer selectionRenderer = SelectionRenderer.getInstance();
    
    /** 选择覆盖层渲染器 */
    private static final SelectionOverlayRenderer selectionOverlayRenderer = SelectionOverlayRenderer.get();
    
    /** 工具预览管理器 */
    private static final ToolPreviewManager toolPreviewManager = ToolPreviewManager.getInstance();
    
    /** 是否已注册事件监听器 */
    private static boolean registered = false;
    
    /**
     * 注册选择渲染事件
     */
    public static void register() {
        if (registered) {
            LOGGER.warn("[SelectionRenderEvents] 事件监听器已经注册");
            return;
        }
        
        try {
            // 注册世界渲染后事件 - 在所有其他渲染完成后渲染选择效果
            WorldRenderEvents.AFTER_TRANSLUCENT.register(SelectionRenderEvents::onWorldRenderAfterTranslucent);
            
            // 注册世界渲染前事件 - 用于准备渲染状态
            WorldRenderEvents.BEFORE_ENTITIES.register(SelectionRenderEvents::onWorldRenderBeforeEntities);
            
            registered = true;
            LOGGER.info("[SelectionRenderEvents] 选择渲染事件已注册");
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderEvents] 注册选择渲染事件失败", e);
        }
    }
    
    /**
     * 取消注册选择渲染事件
     */
    public static void unregister() {
        if (!registered) {
            LOGGER.warn("[SelectionRenderEvents] 事件监听器未注册");
            return;
        }
        
        try {
            // 注意：Fabric API没有提供直接取消注册的方法
            // 在实际应用中，这通常通过条件检查来实现
            registered = false;
            LOGGER.info("[SelectionRenderEvents] 选择渲染事件已取消注册");
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderEvents] 取消注册选择渲染事件失败", e);
        }
    }
    
    /**
     * 世界渲染后事件处理 - 在透明渲染后渲染选择效果
     */
    private static void onWorldRenderAfterTranslucent(WorldRenderContext context) {
        try {
            // 检查是否应该渲染选择效果
            if (!shouldRenderSelection(context)) {
                return;
            }
            
            // 渲染选择效果
            selectionRenderer.render(
                context.matrixStack(),
                context.consumers(),
                0.0f // 简化实现，不使用tickDelta
            );
            
            // 渲染选择覆盖层（线框、包围盒、中心锚点）
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
                selectionOverlayRenderer.render(
                    context.matrixStack(),
                    0.0f, // tickDelta
                    client.gameRenderer.getCamera(),
                    context.consumers()
                );
            }
            
            // 渲染工具预览效果
            toolPreviewManager.render(
                context.matrixStack(),
                context.consumers(),
                0.0f // 简化实现，不使用tickDelta
            );
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderEvents] 渲染选择效果失败", e);
        }
    }
    
    /**
     * 世界渲染前事件处理 - 在实体渲染前准备渲染状态
     */
    private static void onWorldRenderBeforeEntities(WorldRenderContext context) {
        try {
            // 这里可以用于准备渲染状态，例如设置深度测试等
            // 目前暂时不需要特殊处理
            
        } catch (Exception e) {
            LOGGER.error("[SelectionRenderEvents] 准备渲染状态失败", e);
        }
    }
    
    /**
     * 检查是否应该渲染选择效果
     */
    private static boolean shouldRenderSelection(WorldRenderContext context) {
        try {
            // 检查渲染器是否启用
            if (!selectionRenderer.isEnabled()) {
                return false; // 不需要每帧都记录
            }
            
            // 检查是否有选择管理器
            com.chronoblocks.core.selection.SelectionManager selectionManager = 
                com.chronoblocks.core.selection.SelectionManager.get();
            if (selectionManager == null) {
                return false;
            }
            
            // 检查是否有当前工具
            if (!selectionManager.hasCurrentTool()) {
                return false;
            }
            
            // 检查是否有选择内容
            com.chronoblocks.core.selection.SelectionState selectionState = 
                selectionManager.getCurrentSelection();
            if (selectionState == null) {
                return false;
            }
            
            if (selectionState.isEmpty()) {
                return false;
            }
            
            int blockCount = selectionState.getSelectedBlocks().size();
            LOGGER.info("[SelectionRenderEvents] ✅ 满足渲染条件，选择 {} 个方块", blockCount);
            return true;

        } catch (Exception e) {
            LOGGER.error("[SelectionRenderEvents] 检查渲染条件失败", e);
            return false;
        }
    }
    
    /**
     * 获取选择渲染器实例
     */
    public static SelectionRenderer getSelectionRenderer() {
        return selectionRenderer;
    }
    
    /**
     * 获取选择覆盖层渲染器实例
     */
    public static SelectionOverlayRenderer getSelectionOverlayRenderer() {
        return selectionOverlayRenderer;
    }
    
    /**
     * 获取工具预览管理器实例
     */
    public static ToolPreviewManager getToolPreviewManager() {
        return toolPreviewManager;
    }
    
    /**
     * 检查事件是否已注册
     */
    public static boolean isRegistered() {
        return registered;
    }
    
    /**
     * 启用选择渲染
     */
    public static void enableSelectionRendering() {
        selectionRenderer.setEnabled(true);
        LOGGER.info("[SelectionRenderEvents] 选择渲染已启用");
    }
    
    /**
     * 禁用选择渲染
     */
    public static void disableSelectionRendering() {
        selectionRenderer.setEnabled(false);
        LOGGER.info("[SelectionRenderEvents] 选择渲染已禁用");
    }
    
    /**
     * 设置选择渲染颜色
     */
    public static void setSelectionColor(float red, float green, float blue, float alpha) {
        selectionRenderer.setSelectionColor(new float[]{red, green, blue, alpha});
        LOGGER.info("[SelectionRenderEvents] 选择颜色设置为: RGBA({}, {}, {}, {})", red, green, blue, alpha);
    }
    
    /**
     * 设置轮廓渲染颜色
     */
    public static void setOutlineColor(float red, float green, float blue, float alpha) {
        selectionRenderer.setOutlineColor(new float[]{red, green, blue, alpha});
        LOGGER.info("[SelectionRenderEvents] 轮廓颜色设置为: RGBA({}, {}, {}, {})", red, green, blue, alpha);
    }
    
    /**
     * 设置预览渲染颜色
     */
    public static void setPreviewColor(float red, float green, float blue, float alpha) {
        selectionRenderer.setPreviewColor(new float[]{red, green, blue, alpha});
        LOGGER.info("[SelectionRenderEvents] 预览颜色设置为: RGBA({}, {}, {}, {})", red, green, blue, alpha);
    }
}