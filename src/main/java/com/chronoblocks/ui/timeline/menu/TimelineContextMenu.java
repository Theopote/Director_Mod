package com.chronoblocks.ui.timeline.menu;

import com.chronoblocks.ui.timeline.commands.TimelineCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineTrack;

import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;
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
     * 菜单项（扩展版，支持 checkbox/radio/icon/子菜单）
     */
    public static class MenuItem {
        private final String label;
        private final Runnable action;
        private final boolean enabled;
        private final String shortcut;
        
        // 扩展属性（为未来功能准备）
        private final boolean isCheckbox;
        private final boolean checkboxState;
        private final boolean isRadio;
        private final boolean radioSelected;
        private final String icon;  // 图标字符串（如 Icons.LOCK）
        private final TimelineContextMenu submenu;  // 子菜单
        
        // 基础构造函数
        public MenuItem(String label, Runnable action) {
            this(label, action, true, null, false, false, false, false, null, null);
        }
        
        public MenuItem(String label, Runnable action, boolean enabled) {
            this(label, action, enabled, null, false, false, false, false, null, null);
        }
        
        public MenuItem(String label, Runnable action, boolean enabled, String shortcut) {
            this(label, action, enabled, shortcut, false, false, false, false, null, null);
        }
        
        // 完整构造函数
        public MenuItem(String label, Runnable action, boolean enabled, String shortcut,
                      boolean isCheckbox, boolean checkboxState,
                      boolean isRadio, boolean radioSelected,
                      String icon, TimelineContextMenu submenu) {
            this.label = label;
            this.action = action;
            this.enabled = enabled;
            this.shortcut = shortcut;
            this.isCheckbox = isCheckbox;
            this.checkboxState = checkboxState;
            this.isRadio = isRadio;
            this.radioSelected = radioSelected;
            this.icon = icon;
            this.submenu = submenu;
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
        
        // 扩展属性 getter
        public boolean isCheckbox() {
            return isCheckbox;
        }
        
        public boolean getCheckboxState() {
            return checkboxState;
        }
        
        public boolean isRadio() {
            return isRadio;
        }
        
        public boolean isRadioSelected() {
            return radioSelected;
        }
        
        public String getIcon() {
            return icon;
        }
        
        public TimelineContextMenu getSubmenu() {
            return submenu;
        }
        
        public boolean hasSubmenu() {
            return submenu != null;
        }
    }
    
    private final TimelineCommandHistory commandHistory;
    private final List<MenuItem> menuItems = new ArrayList<>();
    private String popupId = "TimelineContextMenu";
    
    public TimelineContextMenu(TimelineCommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }
    
    /**
     * 获取命令历史（用于子菜单构建）
     */
    public TimelineCommandHistory getCommandHistory() {
        return commandHistory;
    }
    
    /**
     * 设置弹出窗口ID
     */
    public void setPopupId(String id) {
        this.popupId = id;
    }
    
    /**
     * 获取弹出窗口ID
     */
    public String getPopupId() {
        return popupId;
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
     * 注意：ImGui.openPopup() 需要在同一帧中调用，并且需要在正确的窗口上下文中
     */
    public void open() {
        // 使用 ImGui.openPopup() 打开菜单
        // 菜单会在下一帧通过 render() 方法显示
        System.out.println("[TimelineContextMenu] 调用 ImGui.openPopup: " + popupId);
        ImGui.openPopup(popupId, ImGuiPopupFlags.None);
        LOGGER.info("[Timeline] 调用 ImGui.openPopup: {}", popupId);
    }
    
    /**
     * 渲染菜单（支持子菜单、checkbox、radio）
     * 
     * <p>⭐ 修复：添加自动关闭机制
     * 当 popup 不再打开时，菜单会自动关闭，避免多次右键导致的问题。
     */
    public void render() {
        // ⭐ 修复：检查 popup 是否应该打开，如果没有打开则不需要渲染
        if (!ImGui.isPopupOpen(popupId, ImGuiPopupFlags.None)) {
            return;
        }
        
        boolean popupOpen = ImGui.beginPopup(popupId);
        if (popupOpen) {
            for (MenuItem item : menuItems) {
                if (item.isSeparator()) {
                    ImGui.separator();
                } else if (item.hasSubmenu()) {
                    // 子菜单
                    String displayLabel = item.getLabel();
                    if (item.getIcon() != null) {
                        displayLabel = item.getIcon() + " " + displayLabel;
                    }
                    if (ImGui.beginMenu(displayLabel, item.isEnabled())) {
                        item.getSubmenu().render();
                        ImGui.endMenu();
                    }
                } else {
                    // 普通菜单项或 checkbox/radio
                    String displayLabel = item.getLabel();
                    if (item.getIcon() != null) {
                        displayLabel = item.getIcon() + " " + displayLabel;
                    }
                    
                    boolean selected;
                    String shortcut = item.getShortcut() != null ? item.getShortcut() : "";
                    
                    if (item.isCheckbox()) {
                        // Checkbox 菜单项
                        boolean[] checked = {item.getCheckboxState()};
                        selected = ImGui.menuItem(displayLabel, shortcut, checked[0], item.isEnabled());
                        if (selected && item.isEnabled()) {
                            item.execute(); // 执行切换操作
                        }
                    } else if (item.isRadio()) {
                        // Radio 菜单项
                        selected = ImGui.menuItem(displayLabel, shortcut, item.isRadioSelected(), item.isEnabled());
                        if (selected && item.isEnabled()) {
                            item.execute();
                        }
                    } else {
                        // 普通菜单项
                        selected = ImGui.menuItem(displayLabel, shortcut, false, item.isEnabled());
                        if (selected && item.isEnabled()) {
                            item.execute();
                            LOGGER.debug("[Timeline] 执行菜单项: {}", item.getLabel());
                        }
                    }
                }
            }
            ImGui.endPopup();
        }
    }
    
    /**
     * 添加子菜单
     */
    public void addSubmenu(String label, TimelineContextMenu submenu) {
        menuItems.add(new MenuItem(label, null, true, null, false, false, false, false, null, submenu));
    }

    /**
     * 创建片段上下文菜单
     * @deprecated 请使用 {@link TimelineContextMenuFactory#createClipMenu(TimelineClip, TimelineTrack)}
     */
    @Deprecated(forRemoval = true)
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
     * @deprecated 请使用 {@link TimelineContextMenuFactory#createKeyframeMenu(TimelineClip, float)}
     */
    @Deprecated(forRemoval = true)
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
        
        menu.addItem("线性插值", () -> LOGGER.debug("设置线性插值"));
        
        menu.addItem("平滑插值", () -> LOGGER.debug("设置平滑插值"));
        
        menu.addItem("阶梯插值", () -> LOGGER.debug("设置阶梯插值"));
        
        return menu;
    }
}