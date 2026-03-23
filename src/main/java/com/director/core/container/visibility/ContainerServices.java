package com.director.core.container.visibility;

import net.minecraft.server.MinecraftServer;

/**
 * 容器服务单例访问层（v5.2）
 * 
 * <p>提供全局访问ContainerVisibilityService的入口
 * 
 * @author Director Team
 * @version 5.2
 */
public class ContainerServices {
    
    private static ContainerVisibilityService visibilityService;
    
    /**
     * 初始化服务（在服务器启动时调用）
     */
    public static void init(MinecraftServer server) {
        visibilityService = new ContainerVisibilityService(server);
    }
    
    /**
     * 获取可见性服务
     */
    public static ContainerVisibilityService visibility() {
        return visibilityService;
    }
}

