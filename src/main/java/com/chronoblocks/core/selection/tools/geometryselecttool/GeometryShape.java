package com.chronoblocks.core.selection.tools.geometryselecttool;

/**
 * 🧩 几何形状类型定义。
 */
public enum GeometryShape {
    SPHERE("球体"),
    CYLINDER("圆柱体"),
    PYRAMID("金字塔"),
    BOX("立方体");

    private final String displayName;

    GeometryShape(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

