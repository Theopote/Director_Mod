package com.chronoblocks.client.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * 🔌 鼠标悬停渲染事件处理器
 * 
 * 将 Fabric 的 WorldRenderEvents 桥接到 MouseHoverRenderer
 * 提供 enable()/disable()，便于在 ChronoBlocks 打开/关闭时切换
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class MouseHoverRenderEvents {
    
    private static boolean enabled = false;
    
    private MouseHoverRenderEvents() {}
    
    /**
     * 启用鼠标悬停高亮渲染
     */
    public static void enable() {
        if (enabled) return;
        WorldRenderEvents.AFTER_ENTITIES.register(MouseHoverRenderEvents::onAfterEntities);
        enabled = true;
    }
    
    /**
     * 禁用鼠标悬停高亮渲染
     */
    public static void disable() {
        enabled = false;
    }
    
    /**
     * 渲染事件回调
     */
    private static void onAfterEntities(WorldRenderContext ctx) {
        if (!enabled) return;
        MouseHoverRenderer.getInstance().render(
            ctx.matrixStack(),
            ctx.consumers(),
            1.0f // tickDelta
        );
    }
    
    /**
     * 检查是否已启用
     */
    public static boolean isEnabled() {
        return enabled;
    }
}
