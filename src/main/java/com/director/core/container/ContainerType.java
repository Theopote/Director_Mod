package com.director.core.container;

import com.director.i18n.ContainerTypeI18n;
import com.director.ui.theme.Icons;

/**
 * 容器类型枚举
 * 
 * @author Director Team
 * @version 4.2 (简化类型：通用、流体、装饰物、逻辑控制、拆除)
 */
public enum ContainerType {
    /**
     * 分组容器 - 仅用于组织其他容器
     */
    GROUP(Icons.FOLDER),
    GENERAL(Icons.CONTAINER),
    FLUID(Icons.WAVEFORM),
    DECORATION(Icons.PALETTE),
    LOGIC(Icons.SETTINGS),
    DEMOLITION(Icons.ERASER);
    
    private final String icon;
    
    ContainerType(String icon) {
        this.icon = icon;
    }
    
    public String getDisplayName() {
        return ContainerTypeI18n.name(this);
    }
    
    public String getIcon() {
        return icon;
    }
    
    @Override
    public String toString() {
        return ContainerTypeI18n.iconAndName(this);
    }
}

