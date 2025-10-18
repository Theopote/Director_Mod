package com.chronoblocks.ui.timeline;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 快捷键管理器
 * 统一管理所有快捷键绑定，支持动态注册和移除
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 */
public class ShortcutManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutManager.class);
    
    /**
     * 快捷键定义
     */
    public static class Shortcut {
        private final int key;
        private final boolean ctrl;
        private final boolean shift;
        private final boolean alt;
        private final Runnable action;
        private final String description;
        
        public Shortcut(int key, boolean ctrl, boolean shift, boolean alt, Runnable action, String description) {
            this.key = key;
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
            this.action = action;
            this.description = description;
        }
        
        public boolean matches(ImGuiIO io, int pressedKey) {
            if (pressedKey != key) return false;
            if (ctrl && !io.getKeyCtrl()) return false;
            if (shift && !io.getKeyShift()) return false;
            if (alt && !io.getKeyAlt()) return false;
            if (!ctrl && io.getKeyCtrl()) return false;  // 精确匹配
            if (!shift && io.getKeyShift()) return false;
            if (!alt && io.getKeyAlt()) return false;
            return true;
        }
        
        public void execute() {
            if (action != null) {
                action.run();
            }
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getKeyString() {
            StringBuilder sb = new StringBuilder();
            if (ctrl) sb.append("Ctrl+");
            if (shift) sb.append("Shift+");
            if (alt) sb.append("Alt+");
            sb.append(getKeyName(key));
            return sb.toString();
        }
        
        private String getKeyName(int key) {
            // 简化版，实际可以扩展更多
            if (key == ImGuiKey.Z) return "Z";
            if (key == ImGuiKey.Y) return "Y";
            if (key == ImGuiKey.Delete) return "Del";
            if (key == ImGuiKey.Escape) return "Esc";
            if (key == ImGuiKey.Space) return "Space";
            return "Key" + key;
        }
    }
    
    private final Map<String, Shortcut> shortcuts = new HashMap<>();
    
    /**
     * 注册快捷键
     */
    public void register(String id, int key, boolean ctrl, boolean shift, boolean alt, 
                        Runnable action, String description) {
        shortcuts.put(id, new Shortcut(key, ctrl, shift, alt, action, description));
        LOGGER.debug("[ShortcutManager] 注册快捷键: {} -> {}", id, description);
    }
    
    /**
     * 便捷方法：注册 Ctrl+ 快捷键
     */
    public void registerCtrl(String id, int key, Runnable action, String description) {
        register(id, key, true, false, false, action, description);
    }
    
    /**
     * 便捷方法：注册单键快捷键
     */
    public void registerKey(String id, int key, Runnable action, String description) {
        register(id, key, false, false, false, action, description);
    }
    
    /**
     * 移除快捷键
     */
    public void unregister(String id) {
        if (shortcuts.remove(id) != null) {
            LOGGER.debug("[ShortcutManager] 移除快捷键: {}", id);
        }
    }
    
    /**
     * 处理输入事件
     */
    public void handleInput(ImGuiIO io) {
        for (Shortcut shortcut : shortcuts.values()) {
            // 检查所有可能的按键
            if (ImGui.isKeyPressed(shortcut.key) && shortcut.matches(io, shortcut.key)) {
                shortcut.execute();
                LOGGER.debug("[ShortcutManager] 触发快捷键: {} ({})", 
                    shortcut.getKeyString(), shortcut.getDescription());
                break;  // 只触发第一个匹配的快捷键
            }
        }
    }
    
    /**
     * 获取所有已注册的快捷键
     */
    public Map<String, Shortcut> getShortcuts() {
        return new HashMap<>(shortcuts);
    }
    
    /**
     * 清空所有快捷键
     */
    public void clear() {
        shortcuts.clear();
        LOGGER.debug("[ShortcutManager] 清空所有快捷键");
    }
    
    /**
     * 打印所有快捷键（调试用）
     */
    public void printShortcuts() {
        LOGGER.info("[ShortcutManager] 已注册快捷键:");
        for (Map.Entry<String, Shortcut> entry : shortcuts.entrySet()) {
            LOGGER.info("  {} -> {} : {}", 
                entry.getKey(), 
                entry.getValue().getKeyString(), 
                entry.getValue().getDescription());
        }
    }
}