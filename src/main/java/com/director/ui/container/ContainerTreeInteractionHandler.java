package com.director.ui.container;

import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.ui.timeline.container.ContainerTimelineService;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    static final String DND_PAYLOAD_TYPE = "DIRECTOR_CONTAINER";
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
            if (singleSelected && !isGroup && !isTimelineControlled(container)) {
                inlineRenamer.startRenaming(container);
            }
        }

        if (isLeftClicked) {
            // 诊断「组内点不中」：若点不中时此日志从不出现，说明 isItemClicked 未命中（ImGui/叠层）；若出现则问题在 selectContainer 或 SelectionModel
            if (container.getParent() != null) {
                LOGGER.debug("[ContainerTree] 组内节点收到左键点击: id={} name={}", container.getId(), container.getName());
            }

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
            renderContextMenu_MoveToFromGroup();

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
        int selCount = controller.getSelectedContainers().size();
        if (selCount > 1) {
            ImGui.textColored(0.0f, 0.588f, 1.0f, 1.0f, "已选择 " + selCount + " 个容器");
            ImGui.sameLine();
            ImGui.textDisabled("(" + container.getName() + ")");
        } else {
            ImGui.textColored(0.0f, 0.588f, 1.0f, 1.0f, container.getName());
        }
        if (isTimelineControlled(container)) {
            ImGui.sameLine();
            ImGui.textDisabled("(时间线接管)");
        }
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
        boolean timelineControlled = isTimelineControlled(container);
        if (ImGui.menuItem("粘贴为子容器", "Ctrl+Shift+V", false, !clipboardEmpty && !timelineControlled)) {
            controller.pasteClipboard(container.getId(), -1);
        }

        ImGui.separator();
        renderContextMenu_CommonOrganizationActions();
        renderContextMenu_MoveToFromGroup();

        ImGui.separator();
        // 组特有：新建子容器
        if (timelineControlled) ImGui.beginDisabled();
        if (ImGui.beginMenu("新建子容器")) {
            for (ContainerType type : ContainerType.values()) {
                if (ImGui.menuItem(type.getDisplayName())) {
                    controller.createChildContainer(type, container.getId());
                }
            }
            ImGui.endMenu();
        }
        if (timelineControlled) ImGui.endDisabled();

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
        renderContextMenu_MoveToFromGroup();

        ImGui.separator();
        renderContextMenu_CommonMovementActions(container);

        ImGui.separator();
        // 容器特有：方块操作
        boolean timelineControlled = isTimelineControlled(container);
        if (ImGui.menuItem(container.isIntakeEnabled() ? "停止实时接收" : "开启实时接收",
                "I", false, !timelineControlled)) {
            controller.toggleIntake(container.getId());
        }
        if (ImGui.menuItem("释放容器", "R", false, !timelineControlled)) {
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
            boolean isOnTimeline = isTimelineControlled(container);
            
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

    private void renderContextMenu_CommonEditActions(Container container) {
        renderMenuItem("重命名", "F2", isTimelineControlled(container), () -> {
            inlineRenamer.startRenaming(container);
        });
    }

    private void renderContextMenu_CommonOrganizationActions() {
        boolean hasSelection = controller.hasSelection();
        boolean timelineSelection = hasTimelineControlledSelection();
        renderMenuItem("将选中容器分组", "Ctrl+G", !hasSelection || timelineSelection, controller::groupSelectedContainers);
        renderMenuItem("合并选中容器", "Ctrl+M", !controller.canMergeSelection() || timelineSelection, () -> controller.mergeSelection(null));
        renderMenuItem("删除", "Del", timelineSelection, () -> {
            LOGGER.info("[ContainerTreeInteractionHandler] 点击删除，标记待打开删除确认弹窗");
            controller.getState().setPendingDeletionRequested(true);
            ImGui.closeCurrentPopup();
        });
    }

    /**
     * 「移动到组」：有选中且存在可目标组时，子菜单列出组，选后 moveContainers(ids, groupId, -1)。
     * 「从组中移出」：有选中且至少一个在组内时，移到上一级（按目标父级分批 moveContainers）。
     * 仅在 hasSelection 且（存在可移动到的组 或 存在在组内的选中）时显示本区块。
     */
    private void renderContextMenu_MoveToFromGroup() {
        if (!controller.hasSelection()) return;
        if (hasTimelineControlledSelection()) return;
        java.util.Collection<Container> selected = controller.getSelectedContainers();
        List<Container> validGroups = collectValidTargetGroups(selected);
        boolean anyInGroup = selected.stream().anyMatch(c -> c.getParent() != null);
        if (validGroups.isEmpty() && !anyInGroup) return;

        ImGui.separator();
        if (!validGroups.isEmpty()) {
            if (ImGui.beginMenu("移动到组")) {
                for (Container g : validGroups) {
                    if (ImGui.menuItem(g.getName())) {
                        List<UUID> ids = selected.stream().map(Container::getId).toList();
                        controller.moveContainers(ids, g.getId(), -1);
                        LOGGER.debug("[ContainerTreeInteractionHandler] 将 {} 个容器移动到组: {}", ids.size(), g.getName());
                    }
                }
                ImGui.endMenu();
            }
        }
        if (anyInGroup) {
            renderMenuItem("从组中移出", "", false, this::executeMoveOutOfGroup);
        }
    }

    /** 从根递归收集 GROUP，并排除：自身在选中中、或为任一选中容器的后代（不可移入）。 */
    private List<Container> collectValidTargetGroups(java.util.Collection<Container> selected) {
        List<Container> out = new ArrayList<>();
        for (Container root : controller.getRootContainers()) {
            collectValidGroupsRec(root, selected, out);
        }
        return out;
    }

    private void collectValidGroupsRec(Container c, java.util.Collection<Container> selected, List<Container> out) {
        if (c.getType() == ContainerType.GROUP) {
            boolean invalid = selected.contains(c);
            if (!invalid) {
                for (Container s : selected) {
                    if (c.isDescendantOf(s)) { invalid = true; break; }
                }
            }
            if (!invalid && !isTimelineControlled(c)) out.add(c);
        }
        for (Container child : c.getChildren()) {
            collectValidGroupsRec(child, selected, out);
        }
    }

    private void executeMoveOutOfGroup() {
        java.util.Collection<Container> selected = controller.getSelectedContainers();
        Map<UUID, List<UUID>> byTarget = new HashMap<>(); // key: 目标父级 id，null 表示根
        for (Container c : selected) {
            Container p = c.getParent();
            if (p == null) continue;
            UUID grandparent = (p.getParent() != null) ? p.getParent().getId() : null;
            byTarget.computeIfAbsent(grandparent, k -> new ArrayList<>()).add(c.getId());
        }
        for (Map.Entry<UUID, List<UUID>> e : byTarget.entrySet()) {
            controller.moveContainers(e.getValue(), e.getKey(), -1);
        }
        if (!byTarget.isEmpty()) {
            LOGGER.debug("[ContainerTreeInteractionHandler] 从组中移出: {} 个目标父级", byTarget.size());
        }
    }

    private void renderContextMenu_CommonMovementActions(Container container) {
        // 「移到顶层」已由「从组中移出」+ 拖到空白区 覆盖，故移除
        boolean disabled = isTimelineControlled(container);
        renderMenuItem("上移", "Ctrl+Up", disabled,
            () -> controller.moveContainerByOffset(container.getId(), -1));
        renderMenuItem("下移", "Ctrl+Down", disabled,
            () -> controller.moveContainerByOffset(container.getId(), 1));
    }

    private void renderContextMenu_CommonStateActions(Container container, String visibilityLabel) {
        int selCount = controller.getSelectedContainers().size();
        boolean timelineSelection = hasTimelineControlledSelection();

        if (selCount > 1) {
            // ── 多选：批量可见性 ──
            if (timelineSelection) ImGui.beginDisabled();
            if (ImGui.beginMenu("批量可见性")) {
                ImGui.textDisabled("对 " + selCount + " 个容器操作");
                ImGui.separator();
                if (ImGui.menuItem("全部显示")) {
                    controller.batchSetContainerState(
                        com.director.core.container.ContainerState.VISIBLE);
                }
                if (ImGui.menuItem("全部隐藏")) {
                    controller.batchSetContainerState(
                        com.director.core.container.ContainerState.HIDDEN);
                }
                ImGui.endMenu();
            }
            if (timelineSelection) ImGui.endDisabled();

            // ── 多选：批量锁定 ──
            if (timelineSelection) ImGui.beginDisabled();
            if (ImGui.beginMenu("批量锁定")) {
                ImGui.textDisabled("对 " + selCount + " 个容器操作");
                ImGui.separator();
                if (ImGui.menuItem("全部锁定")) {
                    controller.batchSetContainerLocked(true);
                }
                if (ImGui.menuItem("全部解锁")) {
                    controller.batchSetContainerLocked(false);
                }
                ImGui.endMenu();
            }
            if (timelineSelection) ImGui.endDisabled();
        } else {
            // ── 单选：原有行为 ──
            boolean disabled = isTimelineControlled(container);
            boolean hidden = container.isHidden();
            renderMenuItem(hidden ? "显示" : visibilityLabel, "V", disabled,
                () -> controller.toggleContainerState(container.getId()));

            boolean locked = container.isLocked();
            renderMenuItem(locked ? "解锁" : "锁定", "L", disabled,
                () -> controller.setContainerLocked(container.getId(), !locked));
        }
    }
    
    private void renderContextMenu_ColorActions(Container container) {
        // 获取当前标签颜色（从metadata或默认值）
        Integer tagColor = (Integer) container.getMetadata("tagColor", null);
        
        // 转换为 float 数组用于颜色选择器（如果没有标签色，使用白色作为默认值）
        int defaultColor = tagColor != null ? tagColor : 0xFFFFFFFF;
        float[] tagColorBuffer = ContainerColorPicker.colorIntToFloat(defaultColor);
        
        boolean timelineSelection = hasTimelineControlledSelection();
        if (timelineSelection) ImGui.beginDisabled();
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
                    // 批量重置（通过命令系统，支持撤销）
                    controller.batchSetContainerTagColors(null);
                } else {
                    controller.setMetadata(container.getId(), "tagColor", null);
                }
            }
            
            ImGui.endMenu();
        }
        if (timelineSelection) ImGui.endDisabled();
    }

    private boolean hasTimelineControlledSelection() {
        for (Container container : controller.getSelectedContainers()) {
            if (isTimelineControlled(container)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTimelineControlled(Container container) {
        if (container == null) {
            return false;
        }
        if (ContainerTimelineService.getInstance().isContainerOnTimeline(container.getId())) {
            return true;
        }
        for (Container child : container.getChildren()) {
            if (isTimelineControlled(child)) {
                return true;
            }
        }
        return false;
    }
}
