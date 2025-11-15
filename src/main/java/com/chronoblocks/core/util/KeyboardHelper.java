package com.chronoblocks.core.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * ⌨️ KeyboardHelper - 键盘辅助工具类
 * 
 * 提供便捷的按键检测方法，用于工具交互逻辑
 * 
 * @author ChronoBlocks
 * @version 1.0
 */
public final class KeyboardHelper {
    
    private KeyboardHelper() {}
    
    /**
     * 检测 Ctrl 键是否按下
     * 
     * @return true 如果 Ctrl 键按下
     */
    public static boolean isCtrlDown() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return false;
            }
            
            long window = client.getWindow().getHandle();
            boolean leftCtrl = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL);
            boolean rightCtrl = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            return leftCtrl || rightCtrl;
        } catch (Exception e) {
            // 如果不在客户端环境（服务器端），返回 false
            return false;
        }
    }
    
    /**
     * 检测 Shift 键是否按下
     * 
     * @return true 如果 Shift 键按下
     */
    public static boolean isShiftDown() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return false;
            }
            
            long window = client.getWindow().getHandle();
            boolean leftShift = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT);
            boolean rightShift = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
            return leftShift || rightShift;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检测 Alt 键是否按下
     * 
     * @return true 如果 Alt 键按下
     */
    public static boolean isAltDown() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return false;
            }
            
            long window = client.getWindow().getHandle();
            boolean leftAlt = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT);
            boolean rightAlt = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT);
            return leftAlt || rightAlt;
        } catch (Exception e) {
            return false;
        }
    }
}
