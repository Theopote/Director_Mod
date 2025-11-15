package com.chronoblocks.ui.timeline;

import com.chronoblocks.ui.timeline.commands.TimelineCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Timeline 上下文菜单系统
 * 使用命令模式管理菜单操作
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineContextMenu {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineContextMenu.class);
    
    /**
     * 菜单项
     */
    public static class MenuItem {
        private final String label;
        private final Runnable action;
        private final boolean enabled;
        private final String shortcut;
        
        public MenuItem(String label, Runnable action) {
            this(label, action, true, null);
        }
        
        public MenuItem(String label, Runnable action, boolean enabled) {
            this(label, action, enabled, null);
        }
        
        public MenuItem(String label, Runnable action, boolean enabled, String shortcut) {
            this.label = label;
            this.action = action;
            this.enabled = enabled;
            this.shortcut = shortcut;
        }
        
        public String getLabel() {
            return label;
        }
        
        public void execute() {
            if (enabled && action != null) {
                action.run();
            }
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public String getShortcut() {
            return shortcut;
        }
        
        public boolean isSeparator() {
            return label == null || label.equals("---");
        }
    }
    
    private final TimelineCommandHistory commandHistory;
    private final List<MenuItem> menuItems = new ArrayList<>();
    private String popupId = "TimelineContextMenu";
    
    public TimelineContextMenu(TimelineCommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }
    
    /**
     * 设置弹出窗口ID
     */
    public void setPopupId(String id) {
        this.popupId = id;
    }
    
    /**
     * 清空菜单项
     */
    public void clear() {
        menuItems.clear();
    }
    
    /**
     * 添加菜单项
     */
    public void addItem(String label, Runnable action) {
        menuItems.add(new MenuItem(label, action));
    }
    
    /**
     * 添加菜单项（带快捷键）
     */
    public void addItem(String label, Runnable action, String shortcut) {
        menuItems.add(new MenuItem(label, action, true, shortcut));
    }
    
    /**
     * 添加菜单项（带条件禁用和快捷键）
     */
    public void addItem(String label, Runnable action, boolean enabled, String shortcut) {
        menuItems.add(new MenuItem(label, action, enabled, shortcut));
    }
    
    /**
     * 添加命令菜单项（自动加入历史栈）
     */
    public void addCommandItem(String label, TimelineCommand command) {
        menuItems.add(new MenuItem(label, () -> commandHistory.executeCommand(command)));
    }
    
    /**
     * 添加命令菜单项（带快捷键）
     */
    public void addCommandItem(String label, TimelineCommand command, String shortcut) {
        menuItems.add(new MenuItem(label, () -> commandHistory.executeCommand(command), true, shortcut));
    }
    
    /**
     * 添加禁用的菜单项
     */
    public void addDisabledItem(String label) {
        menuItems.add(new MenuItem(label, null, false));
    }
    
    /**
     * 添加分隔符
     */
    public void addSeparator() {
        menuItems.add(new MenuItem("---", null));
    }
    
    /**
     * 打开菜单
     */
    public void open() {
        ImGui.openPopup(popupId);
        LOGGER.debug("[Timeline] 打开上下文菜单: {}", popupId);
    }
    
    /**
     * 渲染菜单
     */
    public void render() {
        if (ImGui.beginPopup(popupId)) {
            for (MenuItem item : menuItems) {
                if (item.isSeparator()) {
                    ImGui.separator();
                } else {
                    boolean selected = false;
                    if (item.getShortcut() != null) {
                        selected = ImGui.menuItem(item.getLabel(), item.getShortcut(), false, item.isEnabled());
                    } else {
                        selected = ImGui.menuItem(item.getLabel(), "", false, item.isEnabled());
                    }
                    
                    if (selected) {
                        item.execute();
                        LOGGER.debug("[Timeline] 执行菜单项: {}", item.getLabel());
                    }
                }
            }
            ImGui.endPopup();
        }
    }
    
    /**
     * 创建片段上下文菜单
     */
    public static TimelineContextMenu createClipMenu(TimelineCommandHistory commandHistory, 
                                                    Object clip, 
                                                    Consumer<Object> onDelete,
                                                    Consumer<Object> onDuplicate) {
        TimelineContextMenu menu = new TimelineContextMenu(commandHistory);
        menu.setPopupId("ClipContextMenu");
        
        menu.addItem("复制", () -> {
            // TODO: 实现复制
            LOGGER.debug("复制片段");
        }, "Ctrl+C");
        
        menu.addItem("粘贴", () -> {
            // TODO: 实现粘贴
            LOGGER.debug("粘贴片段");
        }, "Ctrl+V");
        
        menu.addItem("复制片段", () -> {
            if (onDuplicate != null) {
                onDuplicate.accept(clip);
            }
        }, "Ctrl+D");
        
        menu.addSeparator();
        
        menu.addItem("删除", () -> {
            if (onDelete != null) {
                onDelete.accept(clip);
            }
        }, "Del");
        
        return menu;
    }
    
    /**
     * 创建关键帧上下文菜单
     */
    public static TimelineContextMenu createKeyframeMenu(TimelineCommandHistory commandHistory,
                                                        float keyframeTime,
                                                        Consumer<Float> onDelete) {
        TimelineContextMenu menu = new TimelineContextMenu(commandHistory);
        menu.setPopupId("KeyframeContextMenu");
        
        menu.addItem("删除关键帧", () -> {
            if (onDelete != null) {
                onDelete.accept(keyframeTime);
            }
        }, "Del");
        
        menu.addSeparator();
        
        menu.addItem("线性插值", () -> {
            LOGGER.debug("设置线性插值");
        });
        
        menu.addItem("平滑插值", () -> {
            LOGGER.debug("设置平滑插值");
        });
        
        menu.addItem("阶梯插值", () -> {
            LOGGER.debug("设置阶梯插值");
        });
        
        return menu;
    }
}

