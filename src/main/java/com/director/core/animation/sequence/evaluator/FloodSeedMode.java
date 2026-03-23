package com.director.core.animation.sequence.evaluator;

/**
 * 泛洪蔓延起点模式：决定每层泛洪蔓延的起始点选择方式。
 */
public enum FloodSeedMode {
    LAYER_CENTER("每层中心"),
    LAYER_MIN_CORNER("每层最小角"),
    LAYER_RANDOM("每层随机点"),
    GLOBAL_RANDOM("全局随机点");

    private final String displayName;

    FloodSeedMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

