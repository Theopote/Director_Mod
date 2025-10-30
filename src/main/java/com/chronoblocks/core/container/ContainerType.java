package com.chronoblocks.core.container;

import com.chronoblocks.ui.theme.Icons;

/**
 * 容器类型枚举
 * 
 * @author ChronoBlocks Team
 * @version 4.1 (使用自定义图标字体)
 */
public enum ContainerType {
    /**
     * 分组容器 - 用于组织其他容器
     */
    GROUP("容器", Icons.FOLDER),
    
    /**
     * 建筑容器 - 代表一个建筑结构
     */
    BUILDING("建筑", Icons.BUILDING),
    
    /**
     * 区域容器 - 代表一个空间区域
     */
    REGION("区域", Icons.AREA),
    
    /**
     * 粒子容器 - 用于粒子效果
     */
    PARTICLE("粒子", Icons.PARTICLE),
    
    /**
     * 动画容器 - 预定义动画序列
     */
    ANIMATION("动画", Icons.ANIMATION),
    
    /**
     * 自定义容器
     */
    CUSTOM("自定义", Icons.CONTAINER);
    
    private final String displayName;
    private final String icon;
    
    ContainerType(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getIcon() {
        return icon;
    }
    
    @Override
    public String toString() {
        return icon + " " + displayName;
    }
}

