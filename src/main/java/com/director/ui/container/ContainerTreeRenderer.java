package com.director.ui.container;

import com.director.core.container.Container;
import com.director.ui.theme.Icons;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

/**
 * 纯渲染层：负责绘制树节点、图标与提示，不处理业务逻辑。
 *
 * <p>与 InteractionHandler 对齐（可见 = 可点 = 可选 = 可拖）：
 * <ul>
 *   <li>可见行：每行一个 {@code treeNodeEx}，父行不叠子行，不存在 parent 吃掉 children 的 hit。</li>
 *   <li>可点：整行 TreeNode 为 hit 区；组内子节点不画行内锁/可见性图标，避免叠在标签上抢点击导致「组内部分容器点不中」。</li>
 *   <li>根级节点仅在 {@code iconRegionStart >= MIN_LABEL_WIDTH_FOR_ICONS} 时绘制行内图标；锁/可见性仍可通过右键菜单操作。</li>
 *   <li>不引入独立 VisibleNode 列表：ImGui 每行一 item，hit 与 last item 一致，由 InteractionHandler 直接基于 isItemHovered/Clicked 处理。</li>
 * </ul>
 *
 * <p><b>行内按钮事件铁律</b>：所有行内按钮必须在 selection / drag 之前命中并 consume 事件。
 * 当 ImGui 把 click 判给 TreeNode（行）时，用 isMouseClicked+rect 显式命中按钮，确保 handleIconClicks 能执行并 suppress 行选择。
 */
class ContainerTreeRenderer {

    // 右侧“锁/隐藏”图标区域：两枚 18×18 按钮，右侧留白避开滚动条
    private static final float ICON_BUTTON_SIZE = 18.0f;
    private static final float ICONS_REGION_WIDTH = ICON_BUTTON_SIZE * 2.0f; // 36
    private static final float ICON_RIGHT_MARGIN = 6.0f; // 与滚动条拉开距离，避免抢点击
    /** 仅用于根级节点：图标区起始偏移至少保留此宽度才绘制行内锁/可见性图标；组内子节点一律不绘（避免抢点击）。 */
    private static final float MIN_LABEL_WIDTH_FOR_ICONS = 80.0f;
    private static final float TREE_NODE_INNER_SPACING = 2.0f; // 树节点内部间距（箭头和文本之间），更紧凑
    /** 行与行之间的垂直间距（ItemSpacing.y），在 renderTree 内覆写。 */
    private static final float ROW_SPACING = 2.0f;
    private static final float COLOR_TAG_BAR_WIDTH = 6.0f; // 颜色标签条宽度（4-6px，推荐6px）
    private static final float NAME_BUTTONS_SEPARATOR_PADDING = 6.0f; // 名称与按钮之间的留白
    private static final float NAME_BUTTONS_SEPARATOR_THICKNESS = 1.0f;
    // 参考 TimelineTrackRenderer 的分割线风格：半透明白色
    private static final int NAME_BUTTONS_SEPARATOR_COLOR = 0x40FFFFFF;
    private static final int LOCKED_COLOR = 0xFFC8B464;
    private static final int LOCKED_DISABLED_COLOR = 0xB4787878;
    private static final int VISIBLE_COLOR = 0xFFB4B4B4;
    private static final int VISIBLE_DISABLED_COLOR = 0xB4969696;

    /** 每行背景：未选中深灰，选中明显更亮的中灰，便于区分行与选中态。 */
    private static final int ROW_BG_DEFAULT = 0xFF252525;
    private static final int ROW_BG_SELECTED = 0xFF5E5E5E;

    /**
     * 行高与行距（与 TreeNode 一致，用于行背景等布局）：
     * <ul>
     *   <li><b>行高</b>：{@code getTextLineHeight() + getStyle().getFramePadding().y * 2}，
     *       约 22–24px（视字体）；FramePadding.y = Spacing.BUTTON_PADDING_Y = 3。</li>
     *   <li><b>行间距</b>：相邻 TreeNode 之间为 {@code ItemSpacing.y}；本树在 {@code renderTree} 内
     *       覆写为 {@link #ROW_SPACING} = 2px（水平间距保留主题值）。</li>
     * </ul>
     * 更精确的实时行高可在 {@code treeNodeEx} 之后用 {@code getItemRectMax().y - getItemRectMin().y} 得到。
     */
    // (行高/行距为 ImGui 主题与 TreeNode 行为，此处仅作文档；行背景的 rh 与本公式一致)

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
        float spacingX = ImGui.getStyle().getItemSpacing().x;
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, spacingX, ROW_SPACING);
        try {
            if (roots == null || roots.isEmpty()) {
                ImGui.textDisabled("(暂无容器)");
                return;
            }
            for (Container container : roots) {
                renderContainerNode(container);
            }
        } finally {
            ImGui.popStyleVar();
        }
    }

    private void renderContainerNode(Container container) {
        // 内联重命名优先，直接占位这一行
        if (inlineRenamer.isRenaming(container)) {
            inlineRenamer.render(container);
            return;
        }

        // 检查容器是否在时间线上
        boolean isOnTimeline = com.director.ui.timeline.container.ContainerTimelineService.getInstance()
            .isContainerOnTimeline(container.getId());
        
        // ★ 功能：已拖动到时间线的容器，在容器名称前面显示圆点"·"
        String timelineIndicator = isOnTimeline ? "· " : "";

        // 使用 pushID 隔离节点，避免拖拽、上下文菜单、内联重命名、图标按钮的 ID 冲突
        ImGui.pushID(container.getId().toString());
        try {
            // 减小树节点内部间距（箭头和文本之间），让布局更紧凑
            ImGui.pushStyleVar(ImGuiStyleVar.ItemInnerSpacing, TREE_NODE_INNER_SPACING, TREE_NODE_INNER_SPACING);
            
            int flags = buildNodeFlags(container);
            // 保留 SpanAvailWidth：整行可点击，Ctrl/Shift 多选在组内子节点才可靠；
            // 图标用 sameLine 置于右侧且后渲染，会叠在 TreeNode 上并优先接收点击，不会丢图标点击

            // 获取容器标签颜色（从metadata或默认值）
            Integer tagColor = (Integer) container.getMetadata("tagColor", null);
            
            // 使用稳定的 ID 与可读 Label 分离的模式
            String iconText = container.getType().getIcon();
            String nameText = timelineIndicator + container.getName();
            String label = iconText + " " + nameText;
            
            // 强制根据 state 控制展开/折叠（DefaultOpen 只影响首次出现，需 setNextItemOpen 每帧生效）
            boolean shouldBeOpen = state.isNodeExpanded(container.getId());
            ImGui.setNextItemOpen(shouldBeOpen, imgui.flag.ImGuiCond.Always);

            // 记录展开前的状态，用于检测状态变化
            boolean wasExpanded = state.isNodeExpanded(container.getId());
            
            // 使用树节点渲染（包含图标和文本的完整 label）
            // ✅ 让文字在“行背景/按钮”中看起来更垂直居中
            ImGui.alignTextToFramePadding();

            // 行背景：未选中深灰，选中中灰；在 TreeNode 之前绘制以保证在文字/图标下层
            boolean isSelected = controller.isContainerSelected(container.getId());
            float rx = ImGui.getCursorScreenPosX();
            float ry = ImGui.getCursorScreenPosY();
            float rw = ImGui.getContentRegionAvailX();
            float rh = ImGui.getTextLineHeight() + ImGui.getStyle().getFramePadding().y * 2.0f; // 行高，见类注释
            int rowBg = isSelected ? ROW_BG_SELECTED : ROW_BG_DEFAULT;
            ImGui.getWindowDrawList().addRectFilled(rx, ry, rx + rw, ry + rh, rowBg);

            boolean nodeOpen = ImGui.treeNodeEx(label, flags);

            // 记录这一行的矩形（用于让右侧按钮/分割线与背景垂直居中对齐）
            imgui.ImVec2 rowMin = ImGui.getItemRectMin();
            imgui.ImVec2 rowMax = ImGui.getItemRectMax();
            
            // 恢复树节点内部间距
            ImGui.popStyleVar();

            // 同步展开状态到 state（用户点击箭头或双击时）
            if (nodeOpen != wasExpanded) {
                // ✅ 新增：如果节点被折叠，且正在编辑的对象在这个子树里，安全取消会话
                if (!nodeOpen) {
                    inlineRenamer.cancelIfEditingInSubtree(container);
                }
                
                if (state.isExpandAll()) {
                    state.disableExpandAllByCollapsing(container.getId());
                } else {
                    state.toggleNode(container.getId());
                }
            }

            // 在 setupDragAndDrop 之前采集：isItemHovered/Clicked/Visible 都针对当前 TreeNode；
            // setupDragAndDrop 内 ImGui.text 会改掉 last item，故 handleContextMenu 也需在其前
            boolean isHovered = ImGui.isItemHovered();
            boolean isLeftClicked = ImGui.isItemClicked(ImGuiMouseButton.Left);
            boolean isDoubleClicked = isHovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left);
            boolean isRowVisible = ImGui.isItemVisible();

            interactionHandler.handleContextMenu(container);
            interactionHandler.setupDragAndDrop(container, isHovered);

            if (tagColor != null && isRowVisible) {
                drawColorTagBar(tagColor, rowMin, rowMax);
            }

            // 在同一行右侧绘制图标（右对齐）
            // 组内子节点一律不绘制行内图标：缩进会压缩右侧空间，图标极易叠在标签上抢走点击，导致「组中有些容器不能被选中」。
            // 根级节点在空间足够时仍绘制；锁/可见性始终可通过右键菜单操作。
            float contentMaxX = ImGui.getContentRegionMaxX();
            float cursorStartX = ImGui.getCursorStartPosX();
            float iconRegionStart = contentMaxX - cursorStartX - ICONS_REGION_WIDTH - ICON_RIGHT_MARGIN;
            iconRegionStart = Math.max(0, iconRegionStart);

            boolean isInGroup = container.getParent() != null;
            boolean allowIcons = !isInGroup && iconRegionStart >= MIN_LABEL_WIDTH_FOR_ICONS;

            IconClickState iconClickState;
            boolean iconHovered;
            if (allowIcons) {
                if (isRowVisible) {
                    drawNameButtonsSeparator(iconRegionStart, rowMin, rowMax);
                }
                ImGui.sameLine(iconRegionStart);
                iconClickState = renderContainerControlIcons(container, isOnTimeline);
                iconHovered = ImGui.isItemHovered();
            } else {
                iconClickState = new IconClickState(false, false);
                iconHovered = false;
            }

            // 检测图标是否被点击（在节点点击检测之前）
            boolean iconClicked = iconClickState.lockClicked || iconClickState.visibilityClicked;
            
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


    /**
     * 绘制颜色标签条（Color Tag Bar）
     * @param tagColor 标签颜色（ARGB格式）
     * @param rowMin  行矩形最小点（不依赖 getItemRect，避免被拖拽预览等破坏 last item）
     * @param rowMax  行矩形最大点
     */
    private void drawColorTagBar(Integer tagColor, imgui.ImVec2 rowMin, imgui.ImVec2 rowMax) {
        if (tagColor == null) {
            return;
        }
        float windowPosX = ImGui.getWindowPosX();
        float barX2 = windowPosX + COLOR_TAG_BAR_WIDTH;
        float barY1 = rowMin.y;
        float barY2 = rowMax.y;
        
        // 使用 DrawList 绘制颜色标签条
        ImDrawList drawList = ImGui.getWindowDrawList();
        
        // 转换为 ImGui 的 ABGR 格式 (A << 24 | B << 16 | G << 8 | R)
        // tagColor 是 ARGB 格式：(A << 24) | (R << 16) | (G << 8) | B
        int r = (tagColor >> 16) & 0xFF;
        int g = (tagColor >> 8) & 0xFF;
        int b = tagColor & 0xFF;
        int a = (tagColor >> 24) & 0xFF;
        // 如果 alpha 为 0 或未设置，使用完全不透明（255）
        if (a == 0) {
            a = 255;
        }
        // ImGui 使用 ABGR 格式：A << 24 | B << 16 | G << 8 | R
        int colorInt = (a << 24) | (b << 16) | (g << 8) | r;
        
        // 绘制填充的矩形颜色条（竖直，贯穿整行）
        drawList.addRectFilled(windowPosX, barY1, barX2, barY2, colorInt);
    }

    /**
     * 在“名称”和“右侧控制按钮（锁定/可见性）”之间绘制竖向分割线。
     * 视觉目标：类似时间线轨道左侧名称与按钮区的分割线。
     */
    private void drawNameButtonsSeparator(float iconRegionStartLocalX, imgui.ImVec2 itemMin, imgui.ImVec2 itemMax) {
        // iconRegionStartLocalX 是窗口局部坐标，转换为屏幕坐标
        float windowX = ImGui.getWindowPosX();
        float separatorX = windowX + iconRegionStartLocalX - NAME_BUTTONS_SEPARATOR_PADDING;

        // 防止极端窄窗口导致分割线跑到左边去
        float minX = itemMin.x + 8.0f;
        if (separatorX < minX) {
            return;
        }

        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addLine(separatorX, itemMin.y, separatorX, itemMax.y, NAME_BUTTONS_SEPARATOR_COLOR, NAME_BUTTONS_SEPARATOR_THICKNESS);
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

    /**
     * 锁定/可见性图标：InvisibleButton 作命中区 + DrawList 绘制图标。
     * 用 isItemClicked 检测点击；当 ImGui 把 click 判给行时，用 isMouseClicked+rect 显式命中，确保事件能路由到按钮并 consume，不触发行选择。
     */
    private IconClickState renderContainerControlIcons(Container container, boolean isOnTimeline) {
        ImGui.pushID("Icons");
        boolean lockClicked;
        boolean visibilityClicked;
        boolean lockHovered;
        boolean visibilityHovered;
        ImDrawList drawList = ImGui.getWindowDrawList();
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        try {
            // 锁定
            String lockIcon = container.isLocked() ? Icons.LOCK : Icons.UNLOCK;
            int lockColor = container.isLocked() ? LOCKED_COLOR : LOCKED_DISABLED_COLOR;
            ImGui.invisibleButton("lock_btn", ICON_BUTTON_SIZE, ICON_BUTTON_SIZE);
            imgui.ImVec2 lockMin = ImGui.getItemRectMin();
            imgui.ImVec2 lockMax = ImGui.getItemRectMax();
            lockClicked = ImGui.isItemClicked(ImGuiMouseButton.Left)
                || (ImGui.isMouseClicked(ImGuiMouseButton.Left) && rectContainsScreen(lockMin, lockMax, mx, my));
            lockHovered = ImGui.isItemHovered();
            if (lockHovered) {
                ImGui.setTooltip(container.isLocked() ? "解锁容器" : "锁定容器");
            }
            drawIconInRect(drawList, lockMin, lockMax, lockIcon, lockColor);

            // 可见性
            ImGui.sameLine();
            boolean isHidden = container.getState() == com.director.core.container.ContainerState.HIDDEN;
            String visIcon = isHidden ? Icons.INVISIBLE : Icons.VISIBLE;
            int visColor = isHidden ? VISIBLE_DISABLED_COLOR : VISIBLE_COLOR;

            if (isOnTimeline) {
                ImGui.beginDisabled();
            }
            ImGui.invisibleButton("vis_btn", ICON_BUTTON_SIZE, ICON_BUTTON_SIZE);
            imgui.ImVec2 visMin = ImGui.getItemRectMin();
            imgui.ImVec2 visMax = ImGui.getItemRectMax();
            visibilityClicked = ImGui.isItemClicked(ImGuiMouseButton.Left)
                || (!isOnTimeline && ImGui.isMouseClicked(ImGuiMouseButton.Left) && rectContainsScreen(visMin, visMax, mx, my));
            visibilityHovered = ImGui.isItemHovered();
            if (isOnTimeline) {
                ImGui.endDisabled();
            }
            if (visibilityHovered) {
                if (isOnTimeline) {
                    ImGui.setTooltip("容器在时间线上，无法切换可见性（请先从时间线删除片段）");
                } else {
                    ImGui.setTooltip(isHidden ? "显示容器（方块将在 Minecraft 中可见）" : "隐藏容器（方块将在 Minecraft 中不可见，看起来像被清除）");
                }
            }
            drawIconInRect(drawList, visMin, visMax, visIcon, visColor);
        } finally {
            ImGui.popID();
        }

        return new IconClickState(lockClicked, visibilityClicked);
    }

    /** 屏幕坐标 rect 包含点 (mx,my)：与 getItemRectMin/Max 同系，用于显式命中以绕过 ImGui 把 click 判给行的情况。 */
    private static boolean rectContainsScreen(imgui.ImVec2 min, imgui.ImVec2 max, float mx, float my) {
        return mx >= min.x && mx < max.x && my >= min.y && my < max.y;
    }

    /** 在矩形内居中绘制图标；argb 为 ARGB，内部转 DrawList 所需格式。 */
    private void drawIconInRect(ImDrawList drawList, imgui.ImVec2 min, imgui.ImVec2 max, String icon, int argb) {
        imgui.ImVec2 size = ImGui.calcTextSize(icon);
        float x = min.x + (max.x - min.x - size.x) * 0.5f;
        float y = min.y + (max.y - min.y - size.y) * 0.5f;
        int a = (argb >> 24) & 0xFF;
        if (a == 0) a = 255;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
        drawList.addText(x, y, abgr, icon);
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
