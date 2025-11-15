package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * 纯渲染层：负责绘制树节点、图标与提示，不处理业务逻辑。
 */
class ContainerTreeRenderer {

    private static final float ICONS_REGION_WIDTH = 40.0f;
    private static final float ICON_RIGHT_MARGIN = 8.0f; // 图标右侧边距，避免被裁剪
    private static final float TREE_NODE_INNER_SPACING = 2.0f; // 树节点内部间距（箭头和文本之间），更紧凑
    private static final int LOCKED_COLOR = 0xFFC8B464;
    private static final int LOCKED_DISABLED_COLOR = 0xB4787878;
    private static final int VISIBLE_COLOR = 0xFFB4B4B4;
    private static final int VISIBLE_DISABLED_COLOR = 0xB4969696;

    private final ContainerPanelController controller;
    private final ContainerPanelState state;
    private final ContainerTreeInteractionHandler interactionHandler;
    private final InlineRenamer inlineRenamer;

    ContainerTreeRenderer(ContainerPanelController controller,
                          ContainerPanelState state,
                          ContainerTreeInteractionHandler interactionHandler,
                          InlineRenamer inlineRenamer) {
        this.controller = controller;
        this.state = state;
        this.interactionHandler = interactionHandler;
        this.inlineRenamer = inlineRenamer;
    }

    void renderTree(List<Container> roots) {
        if (roots == null || roots.isEmpty()) {
            ImGui.textDisabled("(暂无容器)");
            return;
        }
        for (Container container : roots) {
            renderContainerNode(container);
        }
    }

    private void renderContainerNode(Container container) {
        // 内联重命名优先，直接占位这一行
        if (inlineRenamer.isRenaming(container)) {
            inlineRenamer.render(container);
            return;
        }

        // 使用 pushID 隔离节点，避免拖拽、上下文菜单、内联重命名、图标按钮的 ID 冲突
        ImGui.pushID(container.getId().toString());
        try {
            // 减小树节点内部间距（箭头和文本之间），让布局更紧凑
            ImGui.pushStyleVar(ImGuiStyleVar.ItemInnerSpacing, TREE_NODE_INNER_SPACING, TREE_NODE_INNER_SPACING);
            
            int flags = buildNodeFlags(container);
            // 移除 SpanAvailWidth，让树节点只占据它需要的宽度，为图标留出空间
            flags &= ~ImGuiTreeNodeFlags.SpanAvailWidth;

            // 使用稳定的 ID 与可读 Label 分离的模式
            String label = container.getType().getIcon() + " " + container.getName();
            
            // 记录展开前的状态，用于检测状态变化
            boolean wasExpanded = state.isNodeExpanded(container.getId());
            boolean nodeOpen = ImGui.treeNodeEx(label, flags);
            
            // 恢复树节点内部间距
            ImGui.popStyleVar();

            // 同步展开状态到 state（用户点击箭头或双击时）
            if (nodeOpen != wasExpanded) {
                state.toggleNode(container.getId());
            }

            boolean isHovered = ImGui.isItemHovered();
            boolean isLeftClicked = ImGui.isItemClicked(ImGuiMouseButton.Left);
            boolean isDoubleClicked = isHovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left);

            // 拖拽与节点右键菜单（在图标渲染之前检测，避免被图标影响）
            interactionHandler.setupDragAndDrop(container, isHovered);
            interactionHandler.handleContextMenu(container);

            // 在同一行右侧绘制图标（右对齐）
            // 计算相对于当前行起始位置的偏移量，减去右侧边距避免被裁剪
            float contentMaxX = ImGui.getContentRegionMaxX();
            float cursorStartX = ImGui.getCursorStartPosX();
            float iconRegionStart = contentMaxX - cursorStartX - ICONS_REGION_WIDTH - ICON_RIGHT_MARGIN;
            iconRegionStart = Math.max(0, iconRegionStart); // 确保不为负
            ImGui.sameLine(iconRegionStart);
            IconClickState iconClickState = renderContainerControlIcons(container);

            // 检测图标是否被点击（在节点点击检测之前）
            boolean iconClicked = iconClickState.lockClicked || iconClickState.visibilityClicked;
            
            // 检查最后一个渲染的图标是否被悬停（可见性图标是最后渲染的）
            boolean iconHovered = ImGui.isItemHovered();
            
            // 优先响应图标点击，立即处理
            if (iconClicked) {
                interactionHandler.handleIconClicks(container,
                        iconClickState.lockClicked,
                        iconClickState.visibilityClicked);
            }

            // 如果图标被点击或悬停，不处理节点点击（避免冲突）
            // 图标悬停时，即使节点也被悬停，也不处理节点点击
            boolean suppressNodeClick = iconClicked || iconHovered;
            interactionHandler.handleNodeInteraction(
                    container,
                    isHovered && !iconHovered,  // 节点悬停但不包括图标悬停
                    !suppressNodeClick && isLeftClicked,
                    isDoubleClicked
            );

            if (isHovered) {
                renderTooltip(container);
            }

            if (nodeOpen) {
                for (Container child : container.getChildren()) {
                    renderContainerNode(child);
                }
                ImGui.treePop();
            }
        } finally {
            ImGui.popID();
        }
    }

    private int buildNodeFlags(Container container) {
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
        if (!container.hasChildren()) {
            flags |= ImGuiTreeNodeFlags.Leaf;
        }
        if (controller.isContainerSelected(container.getId())) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }
        if (state.isNodeExpanded(container.getId())) {
            flags |= ImGuiTreeNodeFlags.DefaultOpen;
        }
        return flags;
    }

    private IconClickState renderContainerControlIcons(Container container) {
        // 使用局部 pushID 确保图标按钮 ID 唯一
        ImGui.pushID("Icons");
        boolean lockClicked = false;
        boolean visibilityClicked = false;
        try {
            // 锁定/解锁
            String lockIcon = container.isLocked() ? Icons.LOCK : Icons.UNLOCK;
            int lockColor = container.isLocked() ? LOCKED_COLOR : LOCKED_DISABLED_COLOR;

            ImGui.pushStyleColor(ImGuiCol.Text, lockColor);
            if (ImGui.smallButton(lockIcon + "##lock")) {
                lockClicked = true;
            }
            ImGui.popStyleColor();

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(container.isLocked() ? "解锁容器" : "锁定容器");
            }

            // 可见性（基于容器状态，控制 Minecraft 中方块的显示/隐藏）
            ImGui.sameLine();
            boolean isHidden = container.getState() == com.chronoblocks.core.container.ContainerState.HIDDEN;
            String visIcon = isHidden ? Icons.INVISIBLE : Icons.VISIBLE;
            int visColor = isHidden ? VISIBLE_DISABLED_COLOR : VISIBLE_COLOR;

            ImGui.pushStyleColor(ImGuiCol.Text, visColor);
            if (ImGui.smallButton(visIcon + "##vis")) {
                visibilityClicked = true;
            }
            ImGui.popStyleColor();

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(isHidden ? "显示容器（方块将在 Minecraft 中可见）" : "隐藏容器（方块将在 Minecraft 中不可见，看起来像被清除）");
            }
        } finally {
            ImGui.popID();
        }

        return new IconClickState(lockClicked, visibilityClicked);
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

    private record IconClickState(boolean lockClicked, boolean visibilityClicked) {
    }
}