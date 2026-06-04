package com.director.core.container.visibility;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器服务单例访问层（v5.2）
 * 
 * <p>提供全局访问ContainerVisibilityService的入口
 * 
 * @author Director Team
 * @version 5.2
 */
public class ContainerServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerServices.class);
    
    private static ContainerVisibilityService visibilityService;
    
    /**
     * 初始化服务（在服务器启动时调用）
     */
    public static void init(MinecraftServer server) {
        visibilityService = new ContainerVisibilityService(server);
        syncLoadedHiddenContainers();
    }
    
    /**
     * 获取可见性服务
     */
    public static ContainerVisibilityService visibility() {
        return visibilityService;
    }

    private static void syncLoadedHiddenContainers() {
        if (visibilityService == null) {
            return;
        }

        try {
            ContainerManager manager = ContainerManager.getInstance();
            if (manager == null) {
                return;
            }

            int synced = 0;
            for (Container container : manager.getAllContainers()) {
                if (container != null && container.isHidden() && container.getBlockCount() > 0) {
                    visibilityService.hideContainer(container);
                    synced++;
                }
            }

            if (synced > 0) {
                LOGGER.debug("[ContainerServices] 已同步 {} 个已加载隐藏容器", synced);
            }
        } catch (Exception e) {
            LOGGER.warn("[ContainerServices] 同步已加载隐藏容器失败: {}", e.getMessage(), e);
        }
    }
}

