package com.chronoblocks.ui.timeline;

import com.chronoblocks.core.timeline.TimelineCommand;
import com.chronoblocks.core.timeline.TimelineCommandHistory;

/**
 * Timeline 上下文菜单构建器
 * 使用流式 API 声明式构建菜单
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 */
public class TimelineContextMenuBuilder {
    
    private final TimelineContextMenu menu;
    
    private TimelineContextMenuBuilder(TimelineCommandHistory commandHistory, String popupId) {
        this.menu = new TimelineContextMenu(commandHistory);
        this.menu.setPopupId(popupId);
    }
    
    /**
     * 创建片段菜单构建器
     */
    public static TimelineContextMenuBuilder forClip(TimelineCommandHistory commandHistory) {
        return new TimelineContextMenuBuilder(commandHistory, "ClipContextMenu");
    }
    
    /**
     * 创建关键帧菜单构建器
     */
    public static TimelineContextMenuBuilder forKeyframe(TimelineCommandHistory commandHistory) {
        return new TimelineContextMenuBuilder(commandHistory, "KeyframeContextMenu");
    }
    
    /**
     * 创建轨道菜单构建器
     */
    public static TimelineContextMenuBuilder forTrack(TimelineCommandHistory commandHistory) {
        return new TimelineContextMenuBuilder(commandHistory, "TrackContextMenu");
    }
    
    /**
     * 创建自定义菜单构建器
     */
    public static TimelineContextMenuBuilder custom(TimelineCommandHistory commandHistory, String popupId) {
        return new TimelineContextMenuBuilder(commandHistory, popupId);
    }
    
    // ========== 流式 API ==========
    
    /**
     * 添加普通菜单项
     */
    public TimelineContextMenuBuilder item(String label, Runnable action) {
        menu.addItem(label, action);
        return this;
    }
    
    /**
     * 添加带快捷键的菜单项
     */
    public TimelineContextMenuBuilder item(String label, Runnable action, String shortcut) {
        menu.addItem(label, action, shortcut);
        return this;
    }
    
    /**
     * 添加带条件禁用的菜单项
     */
    public TimelineContextMenuBuilder item(String label, Runnable action, String shortcut, boolean enabled) {
        menu.addItem(label, action, enabled, shortcut);
        return this;
    }
    
    /**
     * 添加命令菜单项（自动加入历史）
     */
    public TimelineContextMenuBuilder command(String label, TimelineCommand command) {
        menu.addCommandItem(label, command);
        return this;
    }
    
    /**
     * 添加带快捷键的命令菜单项
     */
    public TimelineContextMenuBuilder command(String label, TimelineCommand command, String shortcut) {
        menu.addCommandItem(label, command, shortcut);
        return this;
    }
    
    /**
     * 添加禁用的菜单项
     */
    public TimelineContextMenuBuilder disabledItem(String label) {
        menu.addDisabledItem(label);
        return this;
    }
    
    /**
     * 添加分隔符
     */
    public TimelineContextMenuBuilder separator() {
        menu.addSeparator();
        return this;
    }
    
    /**
     * 添加子菜单（未来扩展）
     */
    public SubMenuBuilder submenu(String label) {
        // TODO: 实现子菜单支持
        return new SubMenuBuilder(this);
    }
    
    /**
     * 构建并打开菜单
     */
    public TimelineContextMenu open() {
        menu.open();
        return menu;
    }
    
    /**
     * 仅构建菜单，不打开
     */
    public TimelineContextMenu build() {
        return menu;
    }
    
    // ========== 子菜单构建器（预留）==========
    
    public static class SubMenuBuilder {
        private final TimelineContextMenuBuilder parent;
        
        public SubMenuBuilder(TimelineContextMenuBuilder parent) {
            this.parent = parent;
        }
        
        public SubMenuBuilder item(String label, Runnable action) {
            // TODO: 添加到子菜单
            return this;
        }
        
        public TimelineContextMenuBuilder end() {
            return parent;
        }
    }
    
    // ========== 预定义菜单模板 ==========
    
    /**
     * 创建标准片段菜单
     */
    public static TimelineContextMenu createStandardClipMenu(
            TimelineCommandHistory commandHistory,
            TimelineClip clip,
            Runnable onDelete,
            Runnable onDuplicate) {
        
        return forClip(commandHistory)
            .item("复制", () -> {
                // TODO: 实现剪贴板
            }, "Ctrl+C")
            .item("粘贴", () -> {
                // TODO: 实现剪贴板
            }, "Ctrl+V")
            .item("复制片段", onDuplicate, "Ctrl+D")
            .separator()
            .item("删除", onDelete, "Del")
            .separator()
            .item("设置时长为 1s", () -> {
                clip.setLength(1.0f);
            })
            .item("设置时长为 2s", () -> {
                clip.setLength(2.0f);
            })
            .separator()
            .item("清除所有关键帧", () -> {
                clip.clearKeyframes();
            })
            .open();
    }
    
    /**
     * 创建标准关键帧菜单
     */
    public static TimelineContextMenu createStandardKeyframeMenu(
            TimelineCommandHistory commandHistory,
            float keyframeTime,
            Runnable onDelete) {
        
        return forKeyframe(commandHistory)
            .disabledItem(String.format("关键帧 @ %.2fs", keyframeTime))
            .separator()
            .item("删除关键帧", onDelete, "Del")
            .separator()
            .item("线性插值", () -> {
                // TODO: 设置插值类型
            })
            .item("平滑插值", () -> {
                // TODO: 设置插值类型
            })
            .item("阶梯插值", () -> {
                // TODO: 设置插值类型
            })
            .open();
    }
}

