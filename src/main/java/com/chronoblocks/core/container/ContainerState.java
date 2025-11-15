package com.chronoblocks.core.container;

/**
 * 容器状态枚举
 * 
 * <p>定义容器的可见性状态，用于控制方块在世界中的显示和隐藏
 * 
 * @author ChronoBlocks Team
 * @version 5.0
 */
public enum ContainerState {
    
    /**
     * 可见状态 - 容器中的方块在Minecraft世界中正常显示
     */
    VISIBLE("可见"),
    
    /**
     * 隐藏状态 - 容器中的方块在Minecraft世界中被隐藏
     * <p>注意：这是客户端渲染层面的隐藏，不改动服务器世界数据
     */
    HIDDEN("隐藏");
    
    private final String displayName;
    
    ContainerState(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 切换状态
     */
    public ContainerState toggle() {
        return this == VISIBLE ? HIDDEN : VISIBLE;
    }
    
    /**
     * 是否隐藏
     */
    public boolean isHidden() {
        return this == HIDDEN;
    }
    
    /**
     * 是否可见
     */
    public boolean isVisible() {
        return this == VISIBLE;
    }
}

