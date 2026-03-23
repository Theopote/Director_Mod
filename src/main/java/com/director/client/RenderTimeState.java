package com.director.client;

/**
 * 渲染时的时间覆盖状态（轻量共享变量）
 */
public final class RenderTimeState {
    private static volatile float skyAngleRadians = Float.NaN;

    private RenderTimeState() {}

    public static void setSkyAngleRadians(float v) {
        skyAngleRadians = v;
    }

    public static float getSkyAngleRadians() {
        return skyAngleRadians;
    }

    public static void clear() {
        skyAngleRadians = Float.NaN;
    }
}
