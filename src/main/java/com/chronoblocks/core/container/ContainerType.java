package com.chronoblocks.core.container;

import com.chronoblocks.ui.theme.Icons;

/**
 * 容器类型枚举
 * 
 * @author ChronoBlocks Team
 * @version 4.2 (简化类型：通用、流体、装饰物、逻辑控制、拆除)
 */
public enum ContainerType {
    /**
     * 分组容器 - 仅用于组织其他容器
     */
    GROUP("组", Icons.FOLDER),

    /**
     * 通用容器 - 默认类型，适用于大多数方块组合
     */
    GENERAL("通用", Icons.CONTAINER),

    /**
     * 流体容器 - 水体、岩浆、雾、云
     */
    FLUID("流体", Icons.WAVEFORM),

    /**
     * 装饰容器 - 家具、雕像、标识等艺术物件
     */
    DECORATION("装饰物", Icons.PALETTE),

    /**
     * 逻辑容器 - 脚本、动画序列、触发控制
     */
    LOGIC("逻辑控制", Icons.SETTINGS),

    /**
     * 拆除/清除容器 - 用于“清除轨道”逐步破坏方块（不用于建造/显隐）
     */
    DEMOLITION("拆除", Icons.ERASER);
    
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

