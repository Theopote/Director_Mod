package com.director.ui.timeline.menu;

import com.director.ui.timeline.commands.TimelineCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineTrack;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
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
 * @author Director Team
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
     * 获取菜单项列表（用于子菜单渲染）
     * 
     * @return 菜单项列表
     */
    public List<MenuItem> getMenuItems() {
        return menuItems;
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
        // ⭐⭐ 注意：此方法只应由 TimelineInteractionHandler.openContextMenuForHit() 调用
        // 菜单工厂方法应使用 .build() 而不是 .open()，避免重复打开
        ImGui.openPopup(popupId, ImGuiPopupFlags.None);
        LOGGER.debug("[Timeline] 调用 ImGui.openPopup: {}", popupId);
    }
    
    /**
     * 渲染菜单（支持子菜单、checkbox、radio）
     * 
     * <p>⭐ 修复：添加自动关闭机制
     * 当 popup 不再打开时，菜单会自动关闭，避免多次右键导致的问题。
     * 
     * <p>⭐ 修复：确保菜单不会超出屏幕边界
     * 在打开菜单时，计算并调整位置，确保菜单完全可见。
     */
    public void render() {
        // ⭐ 修复：检查 popup 是否应该打开，如果没有打开则不需要渲染
        if (!ImGui.isPopupOpen(popupId, ImGuiPopupFlags.None)) {
            return;
        }
        
        // ⭐ 修复：设置菜单位置，确保不会超出屏幕边界
        // 使用鼠标位置作为基准，但会由 ImGui 自动调整到屏幕内
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        
        // 获取视口信息，用于边界检查
        imgui.ImGuiViewport viewport = ImGui.getMainViewport();
        float viewportX = viewport.getWorkPosX();
        float viewportY = viewport.getWorkPosY();
        float viewportW = viewport.getWorkSizeX();
        float viewportH = viewport.getWorkSizeY();
        
        // 估算菜单大小（可以根据实际菜单项数量调整）
        // 假设每个菜单项高度约 20px，加上一些边距
        float estimatedMenuHeight = Math.min(menuItems.size() * 20f + 20f, viewportH * 0.8f);
        float estimatedMenuWidth = 200f; // 估算菜单宽度
        
        // 计算菜单位置，确保不超出屏幕
        float menuX = mouseX;
        float menuY = mouseY;
        
        // 如果菜单会超出右边界，则向左调整
        if (menuX + estimatedMenuWidth > viewportX + viewportW) {
            menuX = viewportX + viewportW - estimatedMenuWidth;
            // 确保不会超出左边界
            if (menuX < viewportX) {
                menuX = viewportX + 10f; // 留一些边距
            }
        }
        
        // 如果菜单会超出下边界，则向上调整
        if (menuY + estimatedMenuHeight > viewportY + viewportH) {
            menuY = viewportY + viewportH - estimatedMenuHeight;
            // 确保不会超出上边界
            if (menuY < viewportY) {
                menuY = viewportY + 10f; // 留一些边距
            }
        }
        
        // 设置菜单位置（使用 ImGuiCond.Appearing 只在首次出现时设置）
        // 注意：对于 popup，我们不需要设置 pivot，因为 popup 默认从左上角定位
        ImGui.setNextWindowPos(menuX, menuY, ImGuiCond.Appearing);
        
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
                        // ⭐⭐ 修复：在 beginMenu() 块内直接渲染子菜单的菜单项，而不是调用 render()
                        // 因为 render() 会创建新的 popup，而 beginMenu() 已经创建了子菜单上下文
                        TimelineContextMenu submenu = item.getSubmenu();
                        if (submenu != null) {
                            renderMenuItems(submenu);
                        }
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
     * 渲染菜单项列表（不创建 popup，用于子菜单内部渲染）
     * 
     * <p>这个方法用于在 ImGui.beginMenu() 块内渲染子菜单的菜单项。
     * 与 render() 方法不同，它不会创建新的 popup，而是直接在当前的菜单上下文中渲染。
     * 
     * @param menu 菜单对象
     */
    private void renderMenuItems(TimelineContextMenu menu) {
        // 使用反射或直接访问来获取 menuItems
        // 为了简化，我们创建一个公共方法来获取菜单项
        List<MenuItem> items = menu.getMenuItems();
        for (MenuItem item : items) {
            if (item.isSeparator()) {
                ImGui.separator();
            } else if (item.hasSubmenu()) {
                // 递归处理嵌套子菜单
                String displayLabel = item.getLabel();
                if (item.getIcon() != null) {
                    displayLabel = item.getIcon() + " " + displayLabel;
                }
                if (ImGui.beginMenu(displayLabel, item.isEnabled())) {
                    renderMenuItems(item.getSubmenu());
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
                        item.execute();
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