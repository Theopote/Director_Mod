package com.chronoblocks.ui.timeline.shortcuts;

import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.interaction.TimelineInteractionManager;
import com.chronoblocks.ui.timeline.model.TimelineModel;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 时间线快捷键管理器
 * 统一管理时间线面板的所有快捷键
 */
public final class TimelineShortcutManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineShortcutManager.class);
    
    /**
     * 快捷键定义
     */
    private static class Shortcut {
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
            return alt || !io.getKeyAlt();
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
            if (key == ImGuiKey.X) return "X";
            if (key == ImGuiKey.C) return "C";
            if (key == ImGuiKey.V) return "V";
            if (key == ImGuiKey.Home) return "Home";
            if (key == ImGuiKey.End) return "End";
            return "Key" + key;
        }
    }
    
    private final Map<String, Shortcut> shortcuts = new HashMap<>();
    private final TimelineModel model;
    private final TimelineCommandHistory commandHistory;
    private final TimelineInteractionManager interactionManager;
    
    // 回调接口，用于需要访问 TimelinePanel 的方法
    public interface Callbacks {
        void cutSelectedClip();
        void copySelectedClip();
        void pasteClip();
        void deleteSelectedClip();
    }
    
    private final Callbacks callbacks;

    public TimelineShortcutManager(
            TimelineModel model,
            TimelineCommandHistory commandHistory,
            TimelineInteractionManager interactionManager,
            Callbacks callbacks) {
        this.model = model;
        this.commandHistory = commandHistory;
        this.interactionManager = interactionManager;
        this.callbacks = callbacks;
        
        registerShortcuts();
    }

    /**
     * 注册快捷键
     */
    private void register(String id, int key, boolean ctrl,
                          Runnable action, String description) {
        shortcuts.put(id, new Shortcut(key, ctrl, false, false, action, description));
        LOGGER.debug("[TimelineShortcutManager] 注册快捷键: {} -> {}", id, description);
    }
    
    /**
     * 便捷方法：注册 Ctrl+ 快捷键
     */
    private void registerCtrl(String id, int key, Runnable action, String description) {
        register(id, key, true, action, description);
    }
    
    /**
     * 便捷方法：注册单键快捷键
     */
    private void registerKey(String id, int key, Runnable action, String description) {
        register(id, key, false, action, description);
    }

    /**
     * 注册所有快捷键
     */
    private void registerShortcuts() {
        // 撤销/重做
        registerCtrl("undo", ImGuiKey.Z,
                commandHistory::undo, "撤销");
        registerCtrl("redo", ImGuiKey.Y,
                commandHistory::redo, "重做");
        
        // 删除
        registerKey("delete", ImGuiKey.Delete, 
            callbacks::deleteSelectedClip, "删除选中片段");
        
        // 剪切/复制/粘贴
        registerCtrl("cut", ImGuiKey.X,
            callbacks::cutSelectedClip, "剪切");
        registerCtrl("copy", ImGuiKey.C,
            callbacks::copySelectedClip, "复制");
        registerCtrl("paste", ImGuiKey.V,
            callbacks::pasteClip, "粘贴");
        
        // 取消
        registerKey("cancel", ImGuiKey.Escape,
                interactionManager::cancelInteraction, "取消交互");
        
        // 播放控制
        registerKey("togglePlay", ImGuiKey.Space,
                model::togglePlay, "播放/暂停");
        registerKey("jumpStart", ImGuiKey.Home,
                model::jumpToStart, "跳到开始");
        registerKey("jumpEnd", ImGuiKey.End,
                model::jumpToEnd, "跳到结束");
        
        LOGGER.debug("[TimelineShortcutManager] 注册了 {} 个快捷键", shortcuts.size());
    }

    /**
     * 每帧调用，处理快捷键输入
     */
    public void handle() {
        ImGuiIO io = ImGui.getIO();
        for (Shortcut shortcut : shortcuts.values()) {
            // 检查所有可能的按键
            if (ImGui.isKeyPressed(shortcut.key) && shortcut.matches(io, shortcut.key)) {
                shortcut.execute();
                LOGGER.debug("[TimelineShortcutManager] 触发快捷键: {} ({})", 
                    shortcut.getKeyString(), shortcut.getDescription());
                break;  // 只触发第一个匹配的快捷键
            }
        }
    }
    
    /**
     * 获取所有已注册的快捷键（调试用）
     */
    public Map<String, Shortcut> getShortcuts() {
        return new HashMap<>(shortcuts);
    }
    
    /**
     * 清空所有快捷键
     */
    public void clear() {
        shortcuts.clear();
        LOGGER.debug("[TimelineShortcutManager] 清空所有快捷键");
    }
}

