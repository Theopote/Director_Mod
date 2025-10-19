package com.chronoblocks.ui.panels;

import com.chronoblocks.core.container.*;
import com.chronoblocks.core.container.commands.*;
import com.chronoblocks.core.timeline.TimelineCommandHistory;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ChronoBlocks 容器面板 v4.0
 * 完全重构的企业级容器管理系统
 * 
 * 架构改进：
 * - MVC 分离：使用 ContainerManager 作为 Model
 * - 命令模式：支持撤销/重做
 * - 事件驱动：监听容器变化
 * - 模块化设计：清晰的职责分离
 * - 拖放支持：可拖动到 Timeline
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class ContainerPanel extends AbstractPanel implements ContainerManager.ContainerListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPanel.class);
    
    // ========== 核心组件 ==========
    private final ContainerManager containerManager;
    private final TimelineCommandHistory commandHistory;
    private final ContainerPropertiesPanel propertiesPanel;
    private final WorldBlockCollector blockCollector;
    
    // ========== UI 状态 ==========
    private boolean expandAll = true;
    @SuppressWarnings("unused") // 保留用于未来悬停预览功能
    private Container hoverContainer = null;
    private Container contextMenuContainer = null;
    
    // ========== 重命名状态 ==========
    private Container renamingContainer = null;
    private final ImString renameBuffer = new ImString(256);
    
    // ========== 拖放状态 ==========
    private static final String DND_PAYLOAD_TYPE = "CHRONOBLOCKS_CONTAINER";
    
    // ========== 颜色常量 ==========
    private static final int COLOR_HEADER = rgba(0, 150, 255, 255);
    @SuppressWarnings("unused") // 保留用于未来UI增强
    private static final int COLOR_SELECTED = rgba(64, 128, 255, 100);
    @SuppressWarnings("unused") // 保留用于未来UI增强
    private static final int COLOR_HOVER = rgba(64, 128, 255, 50);
    
    public ContainerPanel() {
        this(new ContainerManager(), new TimelineCommandHistory());
    }
    
    public ContainerPanel(ContainerManager containerManager, TimelineCommandHistory commandHistory) {
        this.containerManager = containerManager;
        this.commandHistory = commandHistory;
        this.propertiesPanel = new ContainerPropertiesPanel(commandHistory);
        this.blockCollector = new WorldBlockCollector();
        
        // 设置全局容器管理器实例（v4.5）
        ContainerManager.setInstance(containerManager);
        
        // 注册事件监听器
        containerManager.addListener(this);
        
        LOGGER.info("[ContainerPanel] 初始化 v4.5 with Properties, Collector & Animation");
    }
    
    @Override
    public String getPanelName() {
        return "Container";
    }
    
    @Override
    public String getWindowName() {
        return "Container###ContainerPanel";
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.NoCollapse;
    }

    @Override
    public boolean shouldReserveDockSpaceWhenHidden() {
        return false;
    }

    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }
    
    @Override
    protected void doRender() {
        renderHeader();
        ImGui.separator();
        renderToolbar();
        ImGui.separator();
        
        // 分割布局：左侧容器树，右侧属性面板
        float windowWidth = ImGui.getContentRegionAvailX();
        float treeWidth = windowWidth * 0.5f;  // 左侧占50%
        
        ImGui.columns(2, "ContainerLayout", true);
        ImGui.setColumnWidth(0, treeWidth);
        
        // 左侧：容器树
        renderContainerTree();
        
        ImGui.nextColumn();
        
        // 右侧：属性面板
        Container selected = containerManager.getSelectedContainer();
        if (selected != null) {
            propertiesPanel.render(selected);
        } else {
            ImGui.textDisabled("(未选择容器)");
            ImGui.spacing();
            ImGui.textWrapped("在左侧容器树中选择一个容器以查看和编辑其属性。");
        }
        
        ImGui.columns(1);
        
        renderContextMenu();
        renderRenamePopup();
    }
    
    // ========== 渲染方法 ==========
    
    private void renderHeader() {
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, Icons.CONTAINER + " 容器管理系统 v4.0");
        ImGui.sameLine();
        ImGui.textDisabled(String.format("(%d 个容器)", containerManager.getContainerCount()));
    }
    
    private void renderToolbar() {
        // 第一行：容器操作（使用自动宽度：按钮宽度 = 文字宽度 + 2×边距）
        if (ImGui.button(Icons.ADD + " 新建容器", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            createNewContainer();
        }
        
        ImGui.sameLine();
        if (ImGui.button(Icons.BLOCK + " 从世界采集", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            collectBlocksFromWorld();
        }
        
        ImGui.sameLine();
        Container selected = containerManager.getSelectedContainer();
        boolean hasSelection = selected != null;
        
        if (!hasSelection) ImGui.beginDisabled();
        if (ImGui.button(Icons.DELETE + " 删除选中", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            deleteSelectedContainer();
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.COPY + " 复制", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            duplicateSelectedContainer();
        }
        if (!hasSelection) ImGui.endDisabled();
        
        ImGui.sameLine();
        if (ImGui.button(expandAll ? "折叠全部" : "展开全部", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            expandAll = !expandAll;
        }
        
        // 采集模式选择
        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();
        ImGui.textDisabled("采集模式:");
        ImGui.sameLine();
        
        WorldBlockCollector.CollectionMode[] modes = WorldBlockCollector.CollectionMode.values();
        int currentMode = blockCollector.getMode().ordinal();
        String[] modeNames = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            modeNames[i] = modes[i].getDisplayName();
        }
        
        ImGui.pushItemWidth(120);
        ImInt modeBuffer = new ImInt(currentMode);
        if (ImGui.combo("##CollectionMode", modeBuffer, modeNames)) {
            blockCollector.setMode(modes[modeBuffer.get()]);
        }
        ImGui.popItemWidth();
        
        // 第二行：撤销/重做（使用自动宽度）
        boolean canUndo = commandHistory.canUndo();
        boolean canRedo = commandHistory.canRedo();
        
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(Icons.UNDO + " 撤销", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.undo();
        }
        if (!canUndo) ImGui.endDisabled();
        
        ImGui.sameLine();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(Icons.REDO + " 重做", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.redo();
        }
        if (!canRedo) ImGui.endDisabled();
        
        ImGui.sameLine();
        // 显示撤销/重做状态
        String historyStatus = canUndo || canRedo ? 
            (canUndo ? "可撤销" : "") + (canRedo ? " 可重做" : "") : "无历史";
        ImGui.textDisabled("历史: " + historyStatus);
        
        // 第三行：选中信息
        if (hasSelection) {
            ImGui.textColored(COLOR_HEADER, "选中: " + selected.getName());
            ImGui.sameLine();
            ImGui.textDisabled(String.format("(%s, %d 方块)", 
                selected.getType().getDisplayName(), 
                selected.getTotalBlockCount()));
        } else {
            ImGui.textDisabled("(未选择容器)");
        }
    }
    
    private void renderContainerTree() {
        ImGui.beginChild("ContainerTree", 0, 0, true);
        
        List<Container> roots = containerManager.getRootContainers();
        if (roots.isEmpty()) {
            ImGui.textDisabled("(暂无容器)");
        } else {
            for (Container container : roots) {
                renderContainerNode(container);
            }
        }
        
        // 空白区域右键菜单
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right) && ImGui.isWindowHovered()) {
            contextMenuContainer = null;
            ImGui.openPopup("ContainerContextMenu");
        }
        
        ImGui.endChild();
    }
    
    private void renderContainerNode(Container container) {
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth
                | ImGuiTreeNodeFlags.OpenOnArrow
                | (expandAll ? ImGuiTreeNodeFlags.DefaultOpen : 0);
        
        // 选中状态
        if (containerManager.isSelected(container)) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        
        // 叶子节点
        if (!container.hasChildren()) {
            flags |= ImGuiTreeNodeFlags.Leaf;
        }
        
        // 渲染节点
        String label = String.format("%s %s###%s", 
            container.getType().getIcon(),
            container.getName(),
            container.getId());
        
        boolean nodeOpen = ImGui.treeNodeEx(label, flags);
        
        // 悬停检测
        if (ImGui.isItemHovered()) {
            hoverContainer = container;
            renderTooltip(container);
        }
        
        // 左键选中
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            boolean multiSelect = ImGui.getIO().getKeyCtrl();
            containerManager.selectContainer(container, multiSelect);
        }
        
        // 右键菜单
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            containerManager.selectContainer(container, false);
            contextMenuContainer = container;
            ImGui.openPopup("ContainerContextMenu");
        }
        
        // 拖动源
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(DND_PAYLOAD_TYPE, container.getId().toString());
            ImGui.text("拖动容器: " + container.getName());
            ImGui.endDragDropSource();
        }
        
        // 拖放目标（用于层级调整）
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(DND_PAYLOAD_TYPE);
            if (payload != null) {
                String draggedId = (String) payload;
                Container draggedContainer = containerManager.getContainer(UUID.fromString(draggedId));
                if (draggedContainer != null && draggedContainer != container) {
                    // 移动到此容器下
                    containerManager.moveContainerToParent(draggedContainer, container);
                    LOGGER.info("[Container] 移动 {} 到 {}", 
                        draggedContainer.getName(), container.getName());
                }
            }
            ImGui.endDragDropTarget();
        }
        
        // 递归渲染子节点
        if (nodeOpen) {
            for (Container child : container.getChildren()) {
                renderContainerNode(child);
            }
            ImGui.treePop();
        }
    }
    
    private void renderTooltip(Container container) {
        ImGui.beginTooltip();
        
        ImGui.text("容器: " + container.getName());
        ImGui.separator();
        
        ImGui.text("类型: " + container.getType().getDisplayName());
        ImGui.text("方块数: " + container.getBlockCount());
        ImGui.text("子容器: " + container.getChildren().size());
        ImGui.text("总方块数: " + container.getTotalBlockCount());
        
        if (container.getCenter() != null) {
            ImGui.text("中心: " + container.getCenter());
        }
        
        Container.BoundingBox bbox = container.getBoundingBox();
        ImGui.text(String.format("大小: %dx%dx%d", 
            bbox.getWidth(), bbox.getHeight(), bbox.getDepth()));
        
        ImGui.separator();
        ImGui.textDisabled("右键打开菜单");
        ImGui.textDisabled("拖动到时间线创建轨道");
        
        ImGui.endTooltip();
    }
    
    private void renderContextMenu() {
        if (ImGui.beginPopup("ContainerContextMenu")) {
            if (contextMenuContainer != null) {
                // 容器右键菜单
                ImGui.textColored(COLOR_HEADER, contextMenuContainer.getName());
                ImGui.separator();
                
                if (ImGui.menuItem("重命名", "F2")) {
                    startRenaming(contextMenuContainer);
                }
                
                if (ImGui.menuItem("复制", "Ctrl+D")) {
                    duplicateContainer(contextMenuContainer);
                }
                
                if (ImGui.menuItem("删除", "Del")) {
                    deleteContainer(contextMenuContainer);
                }
                
                ImGui.separator();
                
                if (ImGui.beginMenu("新建子容器")) {
                    for (ContainerType type : ContainerType.values()) {
                        if (ImGui.menuItem(type.toString())) {
                            createNewContainer(type, contextMenuContainer);
                        }
                    }
                    ImGui.endMenu();
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("移到顶层")) {
                    containerManager.moveContainerToParent(contextMenuContainer, null);
                }
                
                if (ImGui.menuItem("上移", "Ctrl+Up")) {
                    containerManager.moveContainerUp(contextMenuContainer);
                }
                
                if (ImGui.menuItem("下移", "Ctrl+Down")) {
                    containerManager.moveContainerDown(contextMenuContainer);
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem("导出容器...")) {
                    exportContainer(contextMenuContainer);
                }
                
                if (ImGui.menuItem("预览内容", "Space")) {
                    previewContainer(contextMenuContainer);
                }
                
                ImGui.separator();
                
                boolean locked = contextMenuContainer.isLocked();
                if (ImGui.menuItem(locked ? "解锁" : "锁定", "L")) {
                    contextMenuContainer.setLocked(!locked);
                }
                
                boolean visible = contextMenuContainer.isVisible();
                if (ImGui.menuItem(visible ? "隐藏" : "显示", "H")) {
                    contextMenuContainer.setVisible(!visible);
                }
                
            } else {
                // 空白区域右键菜单
                ImGui.textDisabled("容器面板");
                ImGui.separator();
                
                if (ImGui.beginMenu("新建容器")) {
                    for (ContainerType type : ContainerType.values()) {
                        if (ImGui.menuItem(type.toString())) {
                            createNewContainer(type, null);
                        }
                    }
                    ImGui.endMenu();
                }
                
                if (ImGui.menuItem("粘贴")) {
                    // TODO: 实现剪贴板功能
                }
                
                ImGui.separator();
                
                if (ImGui.menuItem(expandAll ? "折叠全部" : "展开全部")) {
                    expandAll = !expandAll;
                }
                
                if (ImGui.menuItem("清空所有容器...")) {
                    // TODO: 添加确认对话框
                    containerManager.clear();
                }
            }
            
            ImGui.endPopup();
        }
    }
    
    private void renderRenamePopup() {
        if (renamingContainer != null) {
            ImGui.openPopup("RenameContainer");
        }
        
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        
        if (ImGui.beginPopupModal("RenameContainer", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("重命名容器");
            ImGui.separator();
            
            ImGui.setKeyboardFocusHere();
            boolean enterPressed = ImGui.inputText("##Name", renameBuffer, 
                ImGuiInputTextFlags.EnterReturnsTrue);
            
            ImGui.separator();
            
            if (ImGui.button("确定", 0, UITheme.Spacing.BUTTON_HEIGHT) || enterPressed) {
                String newName = renameBuffer.get().trim();
                if (!newName.isEmpty() && renamingContainer != null) {
                    renamingContainer.setName(newName);
                    LOGGER.info("[Container] 重命名容器: {}", newName);
                }
                renamingContainer = null;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("取消", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                renamingContainer = null;
                ImGui.closeCurrentPopup();
            }
            
            ImGui.endPopup();
        }
    }
    
    // ========== 容器操作 ==========
    
    private void createNewContainer() {
        createNewContainer(ContainerType.GROUP, null);
    }
    
    private void createNewContainer(ContainerType type, Container parent) {
        String baseName = type.getDisplayName() + "_";
        int index = 1;
        String name;
        
        // 生成唯一名称
        do {
            name = baseName + index++;
        } while (containerManager.getContainer(name) != null);
        
        CreateContainerCommand cmd = new CreateContainerCommand(containerManager, name, type, parent);
        // 直接执行命令（不使用TimelineCommandHistory，因为类型不匹配）
        // TODO: 创建统一的命令历史系统
        cmd.execute();
        
        // 选中新建的容器
        containerManager.selectContainer(cmd.getCreatedContainer());
    }
    
    private void startRenaming(Container container) {
        renamingContainer = container;
        renameBuffer.set(container.getName());
    }
    
    private void deleteSelectedContainer() {
        Container selected = containerManager.getSelectedContainer();
        if (selected != null) {
            deleteContainer(selected);
        }
    }
    
    private void deleteContainer(Container container) {
        DeleteContainerCommand cmd = new DeleteContainerCommand(containerManager, container);
        // 直接执行命令（不使用TimelineCommandHistory，因为类型不匹配）
        // TODO: 创建统一的命令历史系统
        cmd.execute();
    }
    
    private void duplicateSelectedContainer() {
        Container selected = containerManager.getSelectedContainer();
        if (selected != null) {
            duplicateContainer(selected);
        }
    }
    
    private void duplicateContainer(Container container) {
        List<Container> duplicates = containerManager.duplicateSelectedContainers();
        if (!duplicates.isEmpty()) {
            containerManager.selectContainer(duplicates.getFirst());
            LOGGER.info("[Container] 复制容器: {}", container.getName());
        }
    }
    
    private void exportContainer(Container container) {
        LOGGER.info("[Container] 导出容器: {}", container.getName());
        // TODO: 实现导出功能
    }
    
    private void previewContainer(Container container) {
        LOGGER.info("[Container] 预览容器: {}", container.getName());
        // TODO: 实现预览功能（在世界中高亮显示）
    }
    
    private void collectBlocksFromWorld() {
        Container selected = containerManager.getSelectedContainer();
        if (selected == null) {
            LOGGER.warn("[Container] 请先选择一个容器以接收方块");
            return;
        }
        
        int count = blockCollector.collectBlocksToContainer(selected);
        if (count > 0) {
            LOGGER.info("[Container] 成功采集 {} 个方块到容器: {}", count, selected.getName());
        } else {
            LOGGER.warn("[Container] 未采集到任何方块");
        }
    }
    
    // ========== 事件监听器 ==========
    
    @Override
    public void onContainerAdded(Container container) {
        LOGGER.debug("[ContainerPanel] 容器已添加: {}", container.getName());
    }
    
    @Override
    public void onContainerRemoved(Container container) {
        LOGGER.debug("[ContainerPanel] 容器已删除: {}", container.getName());
    }
    
    @Override
    public void onContainerMoved(Container container, Container newParent) {
        LOGGER.debug("[ContainerPanel] 容器已移动: {} -> {}", 
            container.getName(), 
            newParent != null ? newParent.getName() : "根目录");
    }
    
    @Override
    public void onSelectionChanged(Set<Container> selected) {
        LOGGER.debug("[ContainerPanel] 选中变化: {} 个容器", selected.size());
    }
    
    // ========== 辅助方法 ==========
    
    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
    
    // ========== Getter ==========
    
    public ContainerManager getContainerManager() {
        return containerManager;
    }
}