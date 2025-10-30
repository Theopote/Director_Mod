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
 * ChronoBlocks 容器面板 v4.7
 * 完全重构的企业级容器管理系统
 * <p>
 * 架构改进：
 * - MVC 分离：使用 ContainerManager 作为 Model
 * - 命令模式：支持撤销/重做
 * - 事件驱动：监听容器变化
 * - 模块化设计：清晰的职责分离
 * - 拖放支持：可拖动到 Timeline
 * - 依赖注入：提高可测试性和灵活性
 * - 内联重命名：更好的用户体验
 * - 确认对话框：防止误操作
 * - 键盘快捷键：提高操作效率
 * - 性能优化：减少不必要的对象分配
 * - 可调节分割条：灵活的布局调整
 * - 智能空状态：增强的用户引导
 * 
 * @author ChronoBlocks Team
 * @version 4.7
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
    
    // ========== 布局状态 ==========
    private static float leftPanelWidthRatio = 0.5f; // 左侧面板宽度比例 (0.0-1.0)
    private static final float MIN_PANEL_RATIO = 0.15f; // 最小面板比例
    private static final float MAX_PANEL_RATIO = 0.85f; // 最大面板比例
    private static final float SPLITTER_WIDTH = 8.0f; // 分割条宽度
    
    // ========== 分割条视觉常量 ==========
    private static final float SPLITTER_LINE_WIDTH = 1.0f; // 正常状态分割线宽度
    private static final float SPLITTER_HIGHLIGHT_WIDTH = 2.0f; // 悬停状态高亮宽度
    private static final int SPLITTER_LINE_COLOR = 0x40FFFFFF; // 正常状态颜色
    private static final int SPLITTER_HIGHLIGHT_COLOR = 0x80FFFFFF; // 悬停状态颜色
    private static final int SPLITTER_SHADOW_COLOR = 0x10FFFFFF; // 阴影颜色
    
    // ========== 重命名状态 ==========
    private Container renamingContainer = null;
    private final ImString renameBuffer = new ImString(256);
    
    // ========== 拖放状态 ==========
    private static final String DND_PAYLOAD_TYPE = "CHRONOBLOCKS_CONTAINER";
    
    // ========== 颜色常量 ==========
    private static final int COLOR_HEADER = rgba(0, 150, 255);
    @SuppressWarnings("unused") // 保留用于未来UI增强
    private static final int COLOR_SELECTED = rgba(64, 128, 100);
    @SuppressWarnings("unused") // 保留用于未来UI增强
    private static final int COLOR_HOVER = rgba(64, 128, 50);
    
    // ========== 性能优化常量 ==========
    // 预计算采集模式名称，避免每帧分配
    private static final String[] COLLECTION_MODE_NAMES = Arrays.stream(WorldBlockCollector.CollectionMode.values())
        .map(WorldBlockCollector.CollectionMode::getDisplayName)
        .toArray(String[]::new);
    
    /**
     * 依赖注入构造函数 - 推荐使用
     * 通过依赖注入实现控制反转，提高可测试性和灵活性
     */
    public ContainerPanel(ContainerManager containerManager, 
                          TimelineCommandHistory commandHistory,
                          ContainerPropertiesPanel propertiesPanel,
                          WorldBlockCollector blockCollector) {
        this.containerManager = containerManager;
        this.commandHistory = commandHistory;
        this.propertiesPanel = propertiesPanel;
        this.blockCollector = blockCollector;
        
        // 设置全局容器管理器实例（向后兼容）
        ContainerManager.setInstance(containerManager);
        
        // 注册事件监听器
        containerManager.addListener(this);
        
        LOGGER.info("[ContainerPanel] 初始化 v4.6 with Dependency Injection");
    }
    
    /**
     * 便利构造函数 - 向后兼容
     * @deprecated 推荐使用依赖注入构造函数
     */
    @Deprecated
    public ContainerPanel() {
        this(new ContainerManager(), new TimelineCommandHistory());
    }
    
    /**
     * 部分依赖注入构造函数 - 向后兼容
     * @deprecated 推荐使用完整依赖注入构造函数
     */
    @Deprecated
    public ContainerPanel(ContainerManager containerManager, TimelineCommandHistory commandHistory) {
        this(containerManager, commandHistory, 
             new ContainerPropertiesPanel(commandHistory), 
             new WorldBlockCollector());
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
        return super.shouldReserveDockSpaceWhenHidden();
    }

    @Override
    protected boolean shouldShowHiddenPlaceholderIndicator() {
        return false;
    }
    
    @Override
    protected void doRender() {
        handleKeyboardShortcuts();
        
        renderHeader();
        ImGui.separator();
        renderToolbar();
        ImGui.separator();
        
        // 可调节的垂直分割布局
        renderResizableLayout();
        
        renderContextMenu();
        renderConfirmationDialogs();
    }
    
    /**
     * 渲染可调节的垂直分割布局
     */
    private void renderResizableLayout() {
        float windowWidth = ImGui.getContentRegionAvailX();
        float availableHeight = ImGui.getContentRegionAvailY();
        
        // 计算当前帧的左侧面板宽度
        float leftWidth = windowWidth * leftPanelWidthRatio - SPLITTER_WIDTH / 2;
        
        // 左侧：容器树
        ImGui.beginChild("ContainerTreeChild", leftWidth, availableHeight, true);
        renderContainerTree();
        ImGui.endChild();
        
        // 渲染可拖动的分割条
        ImGui.sameLine();
        ImGui.invisibleButton("vSplitter", SPLITTER_WIDTH, availableHeight);
        
        // 处理分割条拖拽
        handleSplitterDrag(windowWidth);
        
        // 设置鼠标指针样式
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        
        // 绘制分割条视觉指示器
        ImVec2 splitterPos = ImGui.getItemRectMin();
        ImVec2 splitterSize = ImGui.getItemRectSize();
        float centerX = splitterPos.x + splitterSize.x / 2;
        
        if (ImGui.isItemHovered() || ImGui.isItemActive()) {
            // 悬停/拖拽时：绘制较窄的高亮区域
            float highlightX = centerX - SPLITTER_HIGHLIGHT_WIDTH / 2;
            
            // 绘制高亮分割线
            ImGui.getWindowDrawList().addRectFilled(
                highlightX, splitterPos.y,
                highlightX + SPLITTER_HIGHLIGHT_WIDTH, splitterPos.y + splitterSize.y,
                SPLITTER_HIGHLIGHT_COLOR, 0.0f
            );
            
            // 添加轻微的阴影效果
            ImGui.getWindowDrawList().addRectFilled(
                highlightX + 1, splitterPos.y,
                highlightX + SPLITTER_HIGHLIGHT_WIDTH + 1, splitterPos.y + splitterSize.y,
                SPLITTER_SHADOW_COLOR, 0.0f
            );
        } else {
            // 正常状态：绘制细线分割条
            float lineX = centerX - SPLITTER_LINE_WIDTH / 2;
            
            // 绘制基础分割线
            ImGui.getWindowDrawList().addRectFilled(
                lineX, splitterPos.y,
                lineX + SPLITTER_LINE_WIDTH, splitterPos.y + splitterSize.y,
                SPLITTER_LINE_COLOR, 0.0f
            );
            
            // 添加轻微的阴影效果，增强立体感
            ImGui.getWindowDrawList().addRectFilled(
                lineX + 1, splitterPos.y,
                lineX + SPLITTER_LINE_WIDTH + 1, splitterPos.y + splitterSize.y,
                SPLITTER_SHADOW_COLOR, 0.0f
            );
        }
        
        ImGui.sameLine();
        
        // 右侧：属性面板
        ImGui.beginChild("PropertiesChild", 0, availableHeight, true);
        renderPropertiesPanel();
        ImGui.endChild();
    }
    
    /**
     * 处理分割条拖拽逻辑
     */
    private void handleSplitterDrag(float windowWidth) {
        if (ImGui.isItemActive() && ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            float mouseDeltaX = ImGui.getIO().getMouseDeltaX();
            leftPanelWidthRatio += mouseDeltaX / windowWidth;
            
            // 限制比例范围，防止面板消失
            leftPanelWidthRatio = Math.max(MIN_PANEL_RATIO, Math.min(MAX_PANEL_RATIO, leftPanelWidthRatio));
        }
    }
    
    /**
     * 渲染属性面板内容
     */
    private void renderPropertiesPanel() {
        Container selected = containerManager.getSelectedContainer();
        if (selected != null) {
            propertiesPanel.render(selected);
        } else {
            renderEmptyStateProperties();
        }
    }
    
    /**
     * 渲染空状态属性面板
     */
    private void renderEmptyStateProperties() {
        ImGui.textDisabled("(未选择容器)");
        ImGui.spacing();
        ImGui.textWrapped("在左侧容器树中选择一个容器以查看和编辑其属性。");
        
        // 添加一些有用的提示
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, Icons.LIGHTBULB + " 提示：");
        ImGui.textWrapped("• 使用鼠标拖拽分割条可以调整左右面板的宽度");
        ImGui.textWrapped("• 右键点击容器可以打开上下文菜单");
        ImGui.textWrapped("• 双击容器名称可以快速重命名");
    }
    
    // ========== 渲染方法 ==========
    
    private void renderHeader() {
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, Icons.CONTAINER + " 容器管理 ");
        ImGui.sameLine();
        ImGui.textDisabled(String.format("(%d 个容器)", containerManager.getContainerCount()));
        
        // 显示分割比例
        ImGui.sameLine();
        ImGui.textDisabled(String.format("分割: %.0f%%", leftPanelWidthRatio * 100));
        
        // 添加重置分割按钮
        ImGui.sameLine();
        if (ImGui.smallButton("重置分割")) {
            leftPanelWidthRatio = 0.5f;
        }
    }
    
    private void renderToolbar() {
        renderMainActionButtons();
        renderViewAndCollectionControls();
        renderHistoryControls();
        renderSelectionInfo();
    }
    
    /**
     * 渲染主要操作按钮
     */
    private void renderMainActionButtons() {
        // 新建容器
        if (ImGui.button(Icons.ADD + " 新建容器", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            createNewContainer();
        }
        
        ImGui.sameLine();
        // 从世界采集
        if (ImGui.button(Icons.BLOCK + " 从世界采集", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            collectBlocksFromWorld();
        }
        
        ImGui.sameLine();
        Container selected = containerManager.getSelectedContainer();
        boolean hasSelection = selected != null;
        
        // 删除和复制按钮（需要选中状态）
        if (!hasSelection) ImGui.beginDisabled();
        if (ImGui.button(Icons.DELETE + " 删除选中", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            // 打开确认对话框而不是直接删除
            ImGui.openPopup("ConfirmDeletion");
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.COPY + " 复制", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            duplicateSelectedContainer();
        }
        if (!hasSelection) ImGui.endDisabled();
        
        ImGui.sameLine();
        // 展开/折叠控制
        if (ImGui.button(expandAll ? "折叠全部" : "展开全部", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            expandAll = !expandAll;
        }
    }
    
    /**
     * 渲染视图和采集控制
     */
    private void renderViewAndCollectionControls() {
        // 采集模式选择
        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();
        ImGui.textDisabled("采集模式:");
        ImGui.sameLine();
        
        // 使用预计算的模式名称，避免每帧分配
        int currentMode = blockCollector.getMode().ordinal();
        
        ImGui.pushItemWidth(120);
        ImInt modeBuffer = new ImInt(currentMode);
        if (ImGui.combo("##CollectionMode", modeBuffer, COLLECTION_MODE_NAMES)) {
            WorldBlockCollector.CollectionMode[] modes = WorldBlockCollector.CollectionMode.values();
            blockCollector.setMode(modes[modeBuffer.get()]);
        }
        ImGui.popItemWidth();
    }
    
    /**
     * 渲染历史控制按钮
     */
    private void renderHistoryControls() {
        boolean canUndo = commandHistory.canUndo();
        boolean canRedo = commandHistory.canRedo();
        
        // 撤销按钮
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(Icons.UNDO + " 撤销", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            commandHistory.undo();
        }
        if (!canUndo) ImGui.endDisabled();
        
        ImGui.sameLine();
        // 重做按钮
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
    }
    
    /**
     * 渲染选中信息
     */
    private void renderSelectionInfo() {
        Container selected = containerManager.getSelectedContainer();
        if (selected != null) {
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
        // 检查是否正在重命名此容器
        if (renamingContainer == container) {
            renderInlineRename(container);
            return;
        }
        
        int flags = buildNodeFlags(container);
        String label = String.format("%s %s###%s", 
            container.getType().getIcon(),
            container.getName(),
            container.getId());
        
        boolean nodeOpen = ImGui.treeNodeEx(label, flags);
        
        // 处理所有交互
        handleNodeInteraction(container);
        setupDragAndDrop(container);
        
        // 递归渲染子节点
        if (nodeOpen) {
            for (Container child : container.getChildren()) {
                renderContainerNode(child);
            }
            ImGui.treePop();
        }
    }
    
    /**
     * 构建树节点标志
     */
    private int buildNodeFlags(Container container) {
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
        
        return flags;
    }
    
    /**
     * 处理节点交互
     */
    private void handleNodeInteraction(Container container) {
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
    }
    
    /**
     * 设置拖放功能
     */
    private void setupDragAndDrop(Container container) {
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
    }
    
    /**
     * 渲染内联重命名输入框
     */
    private void renderInlineRename(Container container) {
        // 设置输入框焦点
        ImGui.setKeyboardFocusHere();
        
        // 渲染输入框
        boolean enterPressed = ImGui.inputText("##InlineRename", renameBuffer, 
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
        
        // 处理输入完成
        if (enterPressed || (!ImGui.isItemFocused() && ImGui.isItemActive())) {
            String newName = renameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(container.getName())) {
                container.setName(newName);
                LOGGER.info("[Container] 内联重命名容器: {}", newName);
            }
            renamingContainer = null;
        }
        
        // ESC键取消重命名
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renamingContainer = null;
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
                renderItemContextMenu();
            } else {
                renderCanvasContextMenu();
            }
            ImGui.endPopup();
        }
    }
    
    /**
     * 渲染容器项右键菜单
     */
    private void renderItemContextMenu() {
        // 容器右键菜单
        ImGui.textColored(COLOR_HEADER, contextMenuContainer.getName());
        ImGui.separator();
        
        if (ImGui.menuItem("重命名", "F2")) {
            startInlineRenaming(contextMenuContainer);
        }
        
        if (ImGui.menuItem("复制", "Ctrl+D")) {
            duplicateContainer(contextMenuContainer);
        }
        
        if (ImGui.menuItem("删除", "Del")) {
            // 打开确认对话框而不是直接删除
            ImGui.openPopup("ConfirmItemDeletion");
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
    }
    
    /**
     * 渲染画布右键菜单
     */
    private void renderCanvasContextMenu() {
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
            // 打开确认对话框而不是直接清空
            ImGui.openPopup("ConfirmClearAll");
        }
    }
    
    /**
     * 渲染所有确认对话框
     */
    private void renderConfirmationDialogs() {
        renderDeletionConfirmDialog();
        renderItemDeletionConfirmDialog();
        renderClearAllConfirmDialog();
    }
    
    /**
     * 渲染删除选中容器确认对话框
     */
    private void renderDeletionConfirmDialog() {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal("ConfirmDeletion", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("您确定要删除选中的容器吗？");
            ImGui.text("此操作无法撤销。");
            ImGui.separator();

            if (ImGui.button("确定删除", 120, 0)) {
                deleteSelectedContainer();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
    
    /**
     * 渲染删除特定容器确认对话框
     */
    private void renderItemDeletionConfirmDialog() {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal("ConfirmItemDeletion", ImGuiWindowFlags.AlwaysAutoResize)) {
            if (contextMenuContainer != null) {
                ImGui.text("您确定要删除容器 \"" + contextMenuContainer.getName() + "\" 吗？");
                ImGui.text("此操作无法撤销。");
                ImGui.separator();

                if (ImGui.button("确定删除", 120, 0)) {
                    deleteContainer(contextMenuContainer);
                    contextMenuContainer = null;
                    ImGui.closeCurrentPopup();
                }
                ImGui.sameLine();
                if (ImGui.button("取消", 120, 0)) {
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }
    
    /**
     * 渲染清空所有容器确认对话框
     */
    private void renderClearAllConfirmDialog() {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal("ConfirmClearAll", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("您确定要清空所有容器吗？");
            ImGui.text("此操作将删除所有容器，无法撤销！");
            ImGui.separator();

            if (ImGui.button("确定清空", 120, 0)) {
                containerManager.clear();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
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
    
    /**
     * 开始内联重命名
     */
    private void startInlineRenaming(Container container) {
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
    
    // ========== 键盘快捷键处理 ==========
    
    /**
     * 处理键盘快捷键
     */
    private void handleKeyboardShortcuts() {
        // 只有在窗口获得焦点时才处理快捷键
        if (!ImGui.isWindowFocused()) {
            return;
        }
        
        // Delete - 删除选中容器
        if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
            Container selected = containerManager.getSelectedContainer();
            if (selected != null) {
                ImGui.openPopup("ConfirmDeletion");
            }
        }
        
        // ESC - 取消重命名
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            if (renamingContainer != null) {
                renamingContainer = null;
            }
        }
        
        // 分割条快捷键（简化版本）
        // 注意：由于ImGuiKey常量问题，暂时移除键盘快捷键
        // 用户可以通过鼠标拖拽和"重置分割"按钮来调整布局
    }
    
    // ========== 辅助方法 ==========
    
    private static int rgba(int r, int g, int a) {
        return (a << 24) | (255 << 16) | (g << 8) | r;
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