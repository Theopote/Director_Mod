package com.director.ui.timeline.menu;

import com.director.ui.timeline.commands.TimelineCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineTrack;

/**
 * Timeline 上下文菜单构建器
 * 使用流式 API 声明式构建菜单
 * 
 * @author Director Team
 * @version 6.0
 */
public class TimelineContextMenuBuilder {
    
    private final TimelineContextMenu menu;
    private final String popupId;
    
    private TimelineContextMenuBuilder(TimelineCommandHistory commandHistory, String popupId) {
        this.menu = new TimelineContextMenu(commandHistory);
        this.menu.setPopupId(popupId);
        this.popupId = popupId;
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
     * 添加带快捷键和启用状态的命令菜单项
     */
    public TimelineContextMenuBuilder command(String label, TimelineCommand command, String shortcut, boolean enabled) {
        if (enabled) {
            menu.addCommandItem(label, command, shortcut);
        } else {
            menu.addItem(label, () -> {}, false, shortcut);
        }
        return this;
    }
    
    /**
     * 添加仅带启用状态的命令菜单项
     */
    public TimelineContextMenuBuilder command(String label, TimelineCommand command, boolean enabled) {
        if (enabled) {
            menu.addCommandItem(label, command);
        } else {
            menu.addItem(label, () -> {}, false, null);
        }
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
     * 添加子菜单
     */
    public SubMenuBuilder submenu(String label) {
        TimelineContextMenu submenu = new TimelineContextMenu(menu.getCommandHistory());
        submenu.setPopupId(popupId + "_" + label.replaceAll("[^a-zA-Z0-9]", ""));
        menu.addSubmenu(label, submenu);
        return new SubMenuBuilder(this, submenu);
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
    
    // ========== 子菜单构建器 ==========
    
    public static class SubMenuBuilder {
        private final TimelineContextMenuBuilder parent;
        private final TimelineContextMenu submenu;
        
        public SubMenuBuilder(TimelineContextMenuBuilder parent, TimelineContextMenu submenu) {
            this.parent = parent;
            this.submenu = submenu;
        }
        
        public SubMenuBuilder item(String label, Runnable action) {
            submenu.addItem(label, action);
            return this;
        }

        public SubMenuBuilder command(String label, TimelineCommand command) {
            submenu.addCommandItem(label, command);
            return this;
        }
        
        public SubMenuBuilder command(String label, TimelineCommand command, String shortcut) {
            submenu.addCommandItem(label, command, shortcut);
            return this;
        }
        
        public SubMenuBuilder separator() {
            submenu.addSeparator();
            return this;
        }
        
        public SubMenuBuilder disabledItem(String label) {
            submenu.addDisabledItem(label);
            return this;
        }

        public TimelineContextMenuBuilder end() {
            return parent;
        }
    }
    
    
    // ========== 预定义菜单模板 ==========
    
    /**
     * 创建标准片段菜单
     * @deprecated 请使用 {@link TimelineContextMenuFactory#createClipMenu(TimelineClip, TimelineTrack)}
     */
    @Deprecated(forRemoval = true)
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
            .item("设置时长为 1s", () -> clip.setLength(1.0f))
            .item("设置时长为 2s", () -> clip.setLength(2.0f))
            .separator()
            .item("清除所有关键帧", clip::clearKeyframes)
            .open();
    }
    
    /**
     * 创建标准关键帧菜单
     * @deprecated 请使用 {@link TimelineContextMenuFactory#createKeyframeMenu(TimelineClip, float)}
     */
    @Deprecated(forRemoval = true)
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