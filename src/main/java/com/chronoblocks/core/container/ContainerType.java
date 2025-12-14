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
     * 分组容器 - 仅用于组织其他容器
     */
    GROUP("组", Icons.FOLDER),

    /**
     * 结构容器 - 静态方块组合（建筑、地形、道路）
     */
    STRUCTURE("建筑结构", Icons.BUILDING),

    /**
     * 植被容器 - 自然元素（树、草、藤蔓）
     */
    VEGETATION("植被", Icons.SPRING),

    /**
     * 流体容器 - 水体、岩浆、雾、云
     */
    FLUID("流体", Icons.WAVEFORM),

    /**
     * 实体容器 - 动物、NPC、展示模型
     */
    ENTITY("实体", Icons.GROUP),

    /**
     * 粒子容器 - 视觉特效烟雾火花等
     */
    PARTICLE("粒子特效", Icons.PARTICLE),

    /**
     * 环境容器 - 光照、天气、天空等全局控制
     */
    ENVIRONMENT("环境", Icons.LIGHT),

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
    DEMOLITION("拆除", Icons.ERASER),

    /**
     * 自定义容器 - 供拓展或插件使用
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

