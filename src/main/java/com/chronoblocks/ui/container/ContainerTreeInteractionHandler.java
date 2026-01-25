package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerType;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 负责容器树所有与用户交互相关的逻辑，如点击、拖放、上下文菜单等。
 *
 * <p>设计原则（与 Renderer / Command 对齐）：
 * <ul>
 *   <li>可见节点 = 可点 = 可选：每行一个 treeNodeEx，hit 与 ImGui item 一致；组内不画行内图标，避免抢点击。</li>
 *   <li>拖拽与 Selection 对齐：多选用 selection.getSelected() 作为 payload，否则当前节点；drop 统一走
 *       {@code controller.moveContainers(ids, targetParentId, targetIndex)} → MoveContainersCommand。</li>
 *   <li>Group 仅展开/折叠、不可拖拽；仍可作为 drop target。多选时禁用 InlineRename；Group 双击不触发 rename。</li>
 *   <li>Selection 生命周期：仅在「空白区域左键」时 clearSelection；树展开/折叠、刷新不清空。</li>
 * </ul>
 *
 * <p>事件流时序图与三条铁律（Interaction 只解释输入 / Selection 只表达意图 / Command 唯一改模型）：
 * 详见 {@code docs/CONTAINER_PANEL_EVENT_FLOW.md}，作后续 bug 的判官。
 */
class ContainerTreeInteractionHandler {

    static final String DND_PAYLOAD_TYPE = "CHRONOBLOCKS_CONTAINER";
    private static final String POPUP_CONTAINER_CONTEXT = "ContainerContextMenu";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerTreeInteractionHandler.class);

    /** 拖拽时在 setDragDropPayload 中写入，accept 时若 payload 不可用则用此回退，确保批量拖放可靠 */
    private String lastDragPayloadData;

    private final ContainerPanelController controller;
    private final ContainerPanelState state;
    private final InlineRenamer inlineRenamer;

    ContainerTreeInteractionHandler(ContainerPanelController controller,
                                    ContainerPanelState state,
                                    InlineRenamer inlineRenamer) {
        this.controller = controller;
        this.state = state;
        this.inlineRenamer = inlineRenamer;
    }

    void handleNodeInteraction(Container container,
                               boolean isHovered,
                               boolean isLeftClicked,
                               boolean isDoubleClicked) {
        if (isHovered) {
            state.setHoveredContainer(container);
        }

        // ✅ 双击语义：仅当「单选 + 非 Group」时开始重命名
        // - 多选：不触发 rename，避免误操作（需 enforce：selection.size()==1 才 rename）
        // - Group：双击仅用于展开/折叠（由 treeNodeEx 处理），不进入 rename
        if (isDoubleClicked) {
            if (inlineRenamer.isEditing() && !inlineRenamer.isRenaming(container)) {
                inlineRenamer.cancelEditing("startRenamingOther");
            }
            boolean singleSelected = controller.getSelectedContainers().size() == 1;
            boolean isGroup = container.getType() == ContainerType.GROUP;
            if (singleSelected && !isGroup) {
                inlineRenamer.startRenaming(container);
            }
        }

        if (isLeftClicked) {
            // ✅ 点击其他节点：如果正在编辑另一个节点，按"点击别处提交"的习惯处理
            if (inlineRenamer.isEditing() && !inlineRenamer.isRenaming(container)) {
                inlineRenamer.requestCommit();
            }

            boolean multiSelect = ImGui.getIO().getKeyCtrl();
            boolean rangeSelect = ImGui.getIO().getKeyShift();
            controller.selectContainer(container.getId(), multiSelect, rangeSelect);
        }
    }

    void setupDragAndDrop(Container container, boolean hovered) {
        // 仅「非 Group」可作为拖拽源；Group 仅展开/折叠，不参与拖拽。Group 仍可作为 drop target 接收子节点。
        // 不依赖 hovered：beginDragDropSource() 仅在从本节点发起拖拽时为 true
        if (container.getType() != ContainerType.GROUP && ImGui.beginDragDropSource()) {
            java.util.Collection<Container> selectedContainers = controller.getSelectedContainers();
            boolean isSelected = controller.isContainerSelected(container.getId());
            boolean hasMultipleSelection = selectedContainers.size() > 1 && isSelected;

            if (hasMultipleSelection) {
                // 批量拖拽：将选中的容器ID用逗号连接
                StringBuilder payloadBuilder = new StringBuilder();
                for (Container c : selectedContainers) {
                    if (!payloadBuilder.isEmpty()) {
                        payloadBuilder.append(",");
                    }
                    payloadBuilder.append(c.getId().toString());
                }
                lastDragPayloadData = payloadBuilder.toString();
                ImGui.setDragDropPayload(DND_PAYLOAD_TYPE, lastDragPayloadData);
                ImGui.text(String.format("拖动 %d 个容器", selectedContainers.size()));
            } else {
                lastDragPayloadData = container.getId().toString();
                ImGui.setDragDropPayload(DND_PAYLOAD_TYPE, lastDragPayloadData);
                ImGui.text("拖动容器: " + container.getName());
            }
            ImGui.endDragDropSource();
        }

        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(DND_PAYLOAD_TYPE);
            if (payload != null) {
                String raw = (payload instanceof String) ? (String) payload : null;
                if (raw == null || parsePayloadIds(raw).isEmpty()) raw = lastDragPayloadData;
                if (raw != null && !parsePayloadIds(raw).isEmpty()) {
                    handleDropPayload(container, raw);
                }
            }
            ImGui.endDragDropTarget();
        }
    }

    void handleRootDropArea() {
        if (!ImGui.isWindowHovered() || ImGui.isAnyItemHovered()) {
            return;
        }
        
        // 处理空白区域的左键点击：取消选中容器
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.isAnyItemHovered()) {
            // ✅ 空白点击：取消重命名（避免误提交半成品）
            inlineRenamer.cancelEditing("clearSelection");
            controller.clearSelection();
            LOGGER.debug("[ContainerTreeInteractionHandler] 空白区域点击：已取消选中容器");
        }
        
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(DND_PAYLOAD_TYPE);
            if (payload != null) {
                String raw = (payload instanceof String) ? (String) payload : null;
                if (raw == null || parsePayloadIds(raw).isEmpty()) raw = lastDragPayloadData;
                if (raw != null) {
                    List<UUID> ids = parsePayloadIds(raw);
                    if (!ids.isEmpty()) {
                        if (inlineRenamer.isEditing()) {
                            inlineRenamer.cancelEditing("dragToRoot");
                        }
                        controller.moveContainers(ids, null, -1);
                        LOGGER.info("[ContainerTreeInteractionHandler] 将 {} 个容器移动到根", ids.size());
                    }
                }
            }
            ImGui.endDragDropTarget();
        }
    }

    private static List<UUID> parsePayloadIds(String payload) {
        List<UUID> out = new ArrayList<>();
        if (payload == null || payload.isBlank()) return out;
        for (String s : payload.split(",")) {
            try {
                out.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    void handleCanvasContextMenu() {
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)
            && ImGui.isWindowHovered()
            && !ImGui.isAnyItemHovered()) {
            state.setContextMenuContainerId(null);
            ImGui.openPopup(POPUP_CONTAINER_CONTEXT);
        }

        if (ImGui.beginPopup(POPUP_CONTAINER_CONTEXT)) {
            ImGui.textDisabled("容器面板");
            ImGui.separator();

            if (ImGui.beginMenu("新建容器")) {
                for (ContainerType type : ContainerType.values()) {
                    if (ImGui.menuItem(type.getDisplayName())) {
                        controller.createNewContainer(type.getDisplayName(), type);
                    }
                }
                ImGui.endMenu();
            }

            boolean hasSelection = controller.hasSelection();
            renderMenuItem("将选中容器分组", "Ctrl+G", !hasSelection, controller::groupSelectedContainers);
            renderMenuItem("合并选中容器", "Ctrl+M", !controller.canMergeSelection(), () -> controller.mergeSelection(null));

            boolean clipboardEmpty = controller.isClipboardEmpty();
            renderMenuItem("粘贴为根容器", "Ctrl+V", clipboardEmpty, () -> controller.pasteClipboard(null, -1));

            ImGui.separator();

            if (ImGui.menuItem(state.isExpandAll() ? "折叠全部" : "展开全部")) {
                controller.toggleExpandAll();
            }

            if (ImGui.menuItem("清空所有容器...")) {
                ImGui.openPopup("ConfirmClearAll");
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    void handleContextMenu(Container container) {
        if (ImGui.beginPopupContextItem(("ContainerContextMenu##" + container.getId()))) {
            if (!controller.isContainerSelected(container.getId())) {
                controller.selectContainer(container.getId(), false);
            }
            state.setContextMenuContainerId(container.getId());
            renderItemContextMenu(container);
            ImGui.endPopup();
        }
    }

    private void renderItemContextMenu(Container container) {
        ImGui.textColored(0.0f, 0.588f, 1.0f, 1.0f, container.getName());
        ImGui.separator();

        if (container.getType() == ContainerType.GROUP) {
            renderGroupContextMenu(container);
        } else {
            renderContainerContextMenu(container);
        }
        
        ImGui.separator();
        renderContextMenu_ColorActions(container);
    }

    private void renderGroupContextMenu(Container container) {
        renderContextMenu_CommonEditActions(container);
        // 合并“复制容器/粘贴到此处”为单一操作仅针对普通容器，组不在此处提供“粘贴到此处”入口

        // 组特有：粘贴为子容器
        boolean clipboardEmpty = controller.isClipboardEmpty();
        if (ImGui.menuItem("粘贴为子容器", "Ctrl+Shift+V", false, !clipboardEmpty)) {
            controller.pasteClipboard(container.getId(), -1);
        }

        ImGui.separator();
        renderContextMenu_CommonOrganizationActions();

        ImGui.separator();
        // 组特有：新建子容器
        if (ImGui.beginMenu("新建子容器")) {
            for (ContainerType type : ContainerType.values()) {
                if (ImGui.menuItem(type.getDisplayName())) {
                    controller.createChildContainer(type, container.getId());
                }
            }
            ImGui.endMenu();
        }

        ImGui.separator();
        renderContextMenu_CommonMovementActions(container);

        ImGui.separator();
        renderContextMenu_CommonStateActions(container, "隐藏");
    }

    private void renderContainerContextMenu(Container container) {
        renderContextMenu_CommonEditActions(container);
        if (ImGui.menuItem("复制容器", "Ctrl+C")) {
            // 合并“复制容器 + 粘贴到此处”为一个动作：复制并立即粘贴到当前容器位置之后
            controller.copyContainerToClipboard(container.getId());
            Container parent = container.getParent();
            java.util.UUID parentId = parent != null ? parent.getId() : null;
            int insertIndex = (parent != null
                ? parent.indexOfChild(container)
                : controller.getRootContainers().indexOf(container)) + 1;
            controller.pasteClipboard(parentId, insertIndex);
        }

        ImGui.separator();
        renderContextMenu_CommonOrganizationActions();

        ImGui.separator();
        renderContextMenu_CommonMovementActions(container);

        ImGui.separator();
        // 容器特有：方块操作
        if (ImGui.menuItem(container.isIntakeEnabled() ? "停止实时接收" : "开启实时接收", "I")) {
            controller.toggleIntake(container.getId());
        }
        if (ImGui.menuItem("释放容器", "R")) {
            controller.getState().setPendingReleaseContainerId(container.getId());
            ImGui.openPopup("ConfirmRelease");
            ImGui.closeCurrentPopup();
        }

        ImGui.separator();
        renderContextMenu_CommonStateActions(container, "隐藏方块");
    }

    /**
     * 处理拖拽到某个目标 Container 上的行为：
     * - 目标是组：作为其子容器追加
     * - 否则：移动到目标节点之前（同级排序）
     * <p>
     * 支持批量拖拽：payloadId 为单个 UUID 或多个逗号分隔的 UUID；
     * 一次 drop = 一条 {@code moveContainers}，一次 undo。
     */
    private void handleDropPayload(Container container, String payloadId) {
        if (inlineRenamer.isEditing()) {
            inlineRenamer.cancelEditing("dragMove");
        }

        List<UUID> ids = parsePayloadIds(payloadId);
        if (ids.isEmpty()) return;

        UUID targetParentId;
        int targetIndex;
        if (container.getType() == ContainerType.GROUP) {
            targetParentId = container.getId();
            targetIndex = -1;
        } else {
            Container parent = container.getParent();
            List<Container> rootView = controller.getRootContainers();
            targetParentId = parent != null ? parent.getId() : null;
            targetIndex = parent != null
                ? parent.indexOfChild(container)
                : rootView.indexOf(container);
        }
        controller.moveContainers(ids, targetParentId, targetIndex);
        LOGGER.info("[ContainerTreeInteractionHandler] 批量移动 {} 个容器到目标", ids.size());
    }

    /**
     * 图标点击的业务处理：锁定/可见性切换。
     */
    void handleIconClicks(Container container, boolean lockClicked, boolean visibilityClicked) {
        if (lockClicked) {
            controller.setContainerLocked(container.getId(), !container.isLocked());
        }
        if (visibilityClicked) {
            // ★ 功能：已拖动到时间线的容器，不能切换可见性
            boolean isOnTimeline = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance()
                .isContainerOnTimeline(container.getId());
            
            if (isOnTimeline) {
                LOGGER.info("[ContainerTreeInteractionHandler] 容器 {} 在时间线上，无法切换可见性", container.getName());
                return;
            }
            
            // 切换容器状态（HIDDEN/VISIBLE），这会控制 Minecraft 中方块的显示/隐藏
            controller.toggleContainerState(container.getId());
        }
    }

    // ---------------------- 公共菜单渲染辅助 ----------------------

    private void renderMenuItem(String label, String shortcut, boolean disabled, Runnable action) {
        if (disabled) ImGui.beginDisabled();
        if (ImGui.menuItem(label, shortcut)) {
            action.run();
        }
        if (disabled) ImGui.endDisabled();
    }

    // 保留重载以便未来使用（当前未使用）
    // private void renderMenuItem(String label, boolean disabled, Runnable action) {
    //     renderMenuItem(label, "", disabled, action);
    // }

    private void renderContextMenu_CommonEditActions(Container container) {
        if (ImGui.menuItem("重命名", "F2")) {
            inlineRenamer.startRenaming(container);
        }
    }

    private void renderContextMenu_CommonOrganizationActions() {
        boolean hasSelection = controller.hasSelection();
        renderMenuItem("将选中容器分组", "Ctrl+G", !hasSelection, controller::groupSelectedContainers);
        renderMenuItem("合并选中容器", "Ctrl+M", !controller.canMergeSelection(), () -> controller.mergeSelection(null));
        renderMenuItem("删除", "Del", false, () -> {
            LOGGER.info("[ContainerTreeInteractionHandler] 点击删除，标记待打开删除确认弹窗");
            controller.getState().setPendingDeletionRequested(true);
            ImGui.closeCurrentPopup();
        });
    }

    private void renderContextMenu_CommonMovementActions(Container container) {
        renderMenuItem("移到顶层", "", false,
            () -> controller.moveContainer(container.getId(), null, -1));
        renderMenuItem("上移", "Ctrl+Up", false,
            () -> controller.moveContainerByOffset(container.getId(), -1));
        renderMenuItem("下移", "Ctrl+Down", false,
            () -> controller.moveContainerByOffset(container.getId(), 1));
    }

    private void renderContextMenu_CommonStateActions(Container container, String visibilityLabel) {
        boolean hidden = container.isHidden();
        renderMenuItem(hidden ? "显示" : visibilityLabel, "V", false,
            () -> controller.toggleContainerState(container.getId()));

        boolean locked = container.isLocked();
        renderMenuItem(locked ? "解锁" : "锁定", "L", false,
            () -> controller.setContainerLocked(container.getId(), !locked));
    }
    
    private void renderContextMenu_ColorActions(Container container) {
        // 获取当前标签颜色（从metadata或默认值）
        Integer tagColor = (Integer) container.getMetadata("tagColor", null);
        
        // 转换为 float 数组用于颜色选择器（如果没有标签色，使用白色作为默认值）
        int defaultColor = tagColor != null ? tagColor : 0xFFFFFFFF;
        float[] tagColorBuffer = ContainerColorPicker.colorIntToFloat(defaultColor);
        
        if (ImGui.beginMenu("设置标签颜色")) {
            boolean hasSelection = controller.hasSelection();
            int selectedCount = hasSelection ? controller.getSelectedContainers().size() : 1;
            
            ImGui.textDisabled(String.format("已选择 %d 个容器", selectedCount));
            ImGui.separator();
            
            // 标签颜色
            ImGui.text("标签颜色:");
            ImGui.sameLine();
            if (ContainerColorPicker.renderColorPicker("标签颜色", tagColorBuffer)) {
                int newTagColor = ContainerColorPicker.colorFloatToInt(tagColorBuffer);
                if (hasSelection && selectedCount > 1) {
                    // 批量设置
                    controller.batchSetContainerTagColors(newTagColor);
                } else {
                    // 单个设置
                    controller.setMetadata(container.getId(), "tagColor", newTagColor);
                }
            }
            
            ImGui.separator();
            
            // 重置颜色
            boolean hasTagColor = container.getMetadata("tagColor") != null;
            if (ImGui.menuItem("重置标签颜色", null, false, hasTagColor)) {
                if (hasSelection && selectedCount > 1) {
                    // 批量重置
                    for (Container c : controller.getSelectedContainers()) {
                        if (c.getMetadata("tagColor") != null) {
                            c.removeMetadata("tagColor");
                        }
                    }
                } else {
                    container.removeMetadata("tagColor");
                }
            }
            
            ImGui.endMenu();
        }
    }
}