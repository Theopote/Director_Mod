package com.chronoblocks.client.renderer;

import com.chronoblocks.ChronoBlocksMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器渲染事件处理器 v4.5
 * 
 * <p>负责在 Minecraft 渲染循环中集成容器渲染，使用客户端 Tick 事件
 * 代替 Fabric 的 WorldRenderEvents（因为我们在 ChronoBlocks 屏幕中渲染）。
 * 
 * <h3>工作原理</h3>
 * <ol>
 *   <li>在 ChronoBlocksScreen 打开时启用渲染</li>
 *   <li>每帧从 ChronoBlocksScreen.render() 调用容器渲染</li>
 *   <li>渲染器在 ImGui UI 之后、3D 世界空间中绘制</li>
 * </ol>
 * 
 * <h3>使用方法</h3>
 * <pre>{@code
 * // 在 ChronoBlocksClient.onInitializeClient() 中注册
 * ContainerRenderEvents.register();
 * 
 * // 在 ChronoBlocksScreen.render() 中调用
 * ContainerRenderEvents.renderInWorld(context, delta);
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class ContainerRenderEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerRenderEvents.class);
    
    /** 是否已注册事件 */
    private static boolean registered = false;
    
    /** 是否启用渲染 */
    private static boolean renderingEnabled = false;
    
    /**
     * 注册容器渲染事件
     * 
     * <p>在客户端初始化时调用一次
     */
    public static void register() {
        if (registered) {
            LOGGER.warn("[ContainerRenderEvents] Already registered, skipping");
            return;
        }
        
        try {
            // 由于我们在自定义 Screen 中渲染，不需要注册 Fabric 事件
            // 渲染将在 ChronoBlocksScreen.render() 中手动触发
            
            registered = true;
            LOGGER.info("[ContainerRenderEvents] Container rendering events registered v4.5");
            LOGGER.info("[ContainerRenderEvents] Rendering will be triggered from ChronoBlocksScreen");
            
        } catch (Exception e) {
            LOGGER.error("[ContainerRenderEvents] Failed to register events", e);
        }
    }
    
    /**
     * 在世界空间中渲染容器（从 Screen 调用）
     * 
     * @param delta 帧间插值
     */
    public static void renderInWorld(float delta) {
        if (!renderingEnabled) {
            return;
        }
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) {
                return;
            }
            
            Camera camera = client.gameRenderer.getCamera();
            if (camera == null) {
                return;
            }
            
            // 创建矩阵栈
            MatrixStack matrices = new MatrixStack();
            
            // 调用渲染器
            ContainerRenderer.get().renderAllContainers(matrices, camera, delta);
            
        } catch (Exception e) {
            LOGGER.error("[ContainerRenderEvents] Error during world rendering", e);
        }
    }
    
    /**
     * 启用容器渲染（当 ChronoBlocksScreen 打开时调用）
     */
    public static void enable() {
        renderingEnabled = true;
        ContainerRenderer.get().setEnabled(true);
        LOGGER.debug("[ContainerRenderEvents] Container rendering enabled");
    }
    
    /**
     * 禁用容器渲染（当 ChronoBlocksScreen 关闭时调用）
     */
    public static void disable() {
        renderingEnabled = false;
        ContainerRenderer.get().setEnabled(false);
        LOGGER.debug("[ContainerRenderEvents] Container rendering disabled");
    }
    
    /**
     * 检查渲染是否启用
     */
    public static boolean isEnabled() {
        return renderingEnabled;
    }
    
    /**
     * 检查事件是否已注册
     */
    public static boolean isRegistered() {
        return registered;
    }
    
    /**
     * 获取渲染器实例（用于配置）
     */
    public static ContainerRenderer getRenderer() {
        return ContainerRenderer.get();
    }
}

