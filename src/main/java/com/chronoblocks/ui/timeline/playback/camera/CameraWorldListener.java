package com.chronoblocks.ui.timeline.playback.camera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CameraWorldListener - 监听世界切换事件，确保摄像机状态正确清理
 * 
 * <p>防止以下问题：
 * <ul>
 *   <li>世界卸载时残留在 PLAYING/PREVIEW 模式</li>
 *   <li>切换服务器时摄像机状态不一致</li>
 *   <li>返回主菜单时锁定在电影视角</li>
 * </ul>
 */
public final class CameraWorldListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraWorldListener.class);
    private static boolean initialized = false;

    private CameraWorldListener() {
    }

    /**
     * 注册世界事件监听器（在 ChronoBlocks 模组初始化时调用一次）
     */
    public static void register() {
        if (initialized) {
            return;
        }

        // 监听客户端停止事件（退出游戏、断开连接等）
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("[CameraWorldListener] 客户端停止，重置摄像机状态");
            CameraRuntime.getInstance().reset();
        });

        initialized = true;
        LOGGER.info("[CameraWorldListener] 世界事件监听器已注册");
    }
}

