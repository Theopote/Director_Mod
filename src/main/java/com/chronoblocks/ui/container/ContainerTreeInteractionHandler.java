package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerType;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * 负责容器树所有与用户交互相关的逻辑，如点击、拖放、上下文菜单等。
 */
class ContainerTreeInteractionHandler {

    static final String DND_PAYLOAD_TYPE = "CHRONOBLOCKS_CONTAINER";
    private static final String POPUP_CONTAINER_CONTEXT = "ContainerContextMenu";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerTreeInteractionHandler.class);

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

        if (isDoubleClicked) {
            inlineRenamer.startRenaming(container);
        }

        if (isLeftClicked) {
            boolean multiSelect = ImGui.getIO().getKeyCtrl();
            controller.selectContainer(container.getId(), multiSelect);
        }
    }

    void setupDragAndDrop(Container container, boolean hovered) {
        if (hovered && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(DND_PAYLOAD_TYPE, container.getId().toString());
            ImGui.text("拖动容器: " + container.getName());
            ImGui.endDragDropSource();
        }

        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(DND_PAYLOAD_TYPE);
            if (payload != null) {
                handleDropPayload(container, (String) payload);
            }
            ImGui.endDragDropTarget();
        }
    }

    void handleRootDropArea() {
        if (!ImGui.isWindowHovered() || ImGui.isAnyItemHovered()) {
            return;
        }
        if (ImGui.beginDragDropTarget()) {
            Object payload = ImGui.acceptDragDropPayload(DND_PAYLOAD_TYPE);
            if (payload != null) {
                try {
                    UUID draggedId = UUID.fromString((String) payload);
                    controller.moveContainer(draggedId, null, -1);
                    LOGGER.info("[ContainerTreeInteractionHandler] 将容器 {} 移动到根目录", draggedId);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("[ContainerTreeInteractionHandler] 无法解析拖拽载荷为 UUID: {}", payload);
                }
            }
            ImGui.endDragDropTarget();
        }
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
            if (!hasSelection) ImGui.beginDisabled();
            if (ImGui.menuItem("将选中容器分组", "Ctrl+G")) {
                controller.groupSelectedContainers();
            }
            if (!hasSelection) ImGui.endDisabled();

            boolean clipboardEmpty = controller.isClipboardEmpty();
            if (clipboardEmpty) ImGui.beginDisabled();
            if (ImGui.menuItem("粘贴为根容器", "Ctrl+V")) {
                controller.pasteClipboard(null, -1);
            }
            if (clipboardEmpty) ImGui.endDisabled();

            ImGui.separator();

            if (ImGui.menuItem(state.isExpandAll() ? "折叠全部" : "展开全部")) {
                controller.toggleExpandAll();
            }

            if (ImGui.menuItem("清空所有容器...")) {
                controller.clearAllContainers();
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
    }

    private void renderGroupContextMenu(Container container) {
        // 基本操作
        if (ImGui.menuItem("重命名", "F2")) {
            inlineRenamer.startRenaming(container);
        }

        boolean clipboardEmpty = controller.isClipboardEmpty();
        if (clipboardEmpty) ImGui.beginDisabled();
        if (ImGui.menuItem("粘贴到此处", "Ctrl+V")) {
            Container parent = container.getParent();
            UUID parentId = parent != null ? parent.getId() : null;
            int insertIndex;
            if (parent != null) {
                insertIndex = parent.indexOfChild(container);
            } else {
                insertIndex = controller.getRootContainers().indexOf(container);
            }
            controller.pasteClipboard(parentId, insertIndex);
        }
        if (clipboardEmpty) ImGui.endDisabled();

        if (ImGui.menuItem("粘贴为子容器", "Ctrl+Shift+V", false, !clipboardEmpty)) {
            controller.pasteClipboard(container.getId(), -1);
        }

        ImGui.separator();

        // 组织操作
        Set<Container> selectedContainers = controller.getSelectedContainers();
        boolean hasSelection = !selectedContainers.isEmpty();

        if (!hasSelection) ImGui.beginDisabled();
        if (ImGui.menuItem("将选中容器分组", "Ctrl+G")) {
            controller.groupSelectedContainers();
        }
        if (!hasSelection) ImGui.endDisabled();

        if (ImGui.menuItem("删除", "Del")) {
            controller.deleteSelectedContainers();
        }

        ImGui.separator();

        // 新建子容器（组特有）
        if (ImGui.beginMenu("新建子容器")) {
            for (ContainerType type : ContainerType.values()) {
                if (ImGui.menuItem(type.getDisplayName())) {
                    controller.createChildContainer(type, container.getId());
                }
            }
            ImGui.endMenu();
        }

        ImGui.separator();

        // 层级操作
        if (ImGui.menuItem("移到顶层")) {
            controller.moveContainer(container.getId(), null, -1);
        }

        if (ImGui.menuItem("上移", "Ctrl+Up")) {
            controller.moveContainerByOffset(container.getId(), -1);
        }

        if (ImGui.menuItem("下移", "Ctrl+Down")) {
            controller.moveContainerByOffset(container.getId(), 1);
        }

        ImGui.separator();

        // 显示/隐藏（切换容器状态）
        boolean hidden = container.isHidden();
        if (ImGui.menuItem(hidden ? "显示" : "隐藏", "V")) {
            controller.toggleContainerState(container.getId());
        }

        // 锁定
        boolean locked = container.isLocked();
        if (ImGui.menuItem(locked ? "解锁" : "锁定", "L")) {
            controller.setContainerLocked(container.getId(), !locked);
        }
    }

    private void renderContainerContextMenu(Container container) {
        // 基本操作
        if (ImGui.menuItem("重命名", "F2")) {
            inlineRenamer.startRenaming(container);
        }

        if (ImGui.menuItem("复制容器", "Ctrl+C")) {
            controller.copyContainerToClipboard(container.getId());
        }

        boolean clipboardEmpty = controller.isClipboardEmpty();
        if (clipboardEmpty) ImGui.beginDisabled();
        if (ImGui.menuItem("粘贴到此处", "Ctrl+V")) {
            Container parent = container.getParent();
            UUID parentId = parent != null ? parent.getId() : null;
            int insertIndex;
            if (parent != null) {
                insertIndex = parent.indexOfChild(container);
            } else {
                insertIndex = controller.getRootContainers().indexOf(container);
            }
            controller.pasteClipboard(parentId, insertIndex);
        }
        if (clipboardEmpty) ImGui.endDisabled();

        ImGui.separator();

        // 组织操作
        Set<Container> selectedContainers = controller.getSelectedContainers();
        boolean hasSelection = !selectedContainers.isEmpty();

        if (!hasSelection) ImGui.beginDisabled();
        if (ImGui.menuItem("将选中容器分组", "Ctrl+G")) {
            controller.groupSelectedContainers();
        }
        if (!hasSelection) ImGui.endDisabled();

        if (ImGui.menuItem("删除", "Del")) {
            controller.deleteSelectedContainers();
        }

        ImGui.separator();

        // 层级操作
        if (ImGui.menuItem("移到顶层")) {
            controller.moveContainer(container.getId(), null, -1);
        }

        if (ImGui.menuItem("上移", "Ctrl+Up")) {
            controller.moveContainerByOffset(container.getId(), -1);
        }

        if (ImGui.menuItem("下移", "Ctrl+Down")) {
            controller.moveContainerByOffset(container.getId(), 1);
        }

        ImGui.separator();

        // 方块相关操作（容器特有）
        boolean intakeEnabled = container.isIntakeEnabled();
        if (ImGui.menuItem(intakeEnabled ? "停止实时接收" : "开启实时接收", "I")) {
            controller.toggleIntake(container.getId());
        }

        boolean hidden = container.isHidden();
        if (ImGui.menuItem(hidden ? "显示方块" : "隐藏方块", "V")) {
            controller.toggleContainerState(container.getId());
        }

        if (ImGui.menuItem("释放容器", "R")) {
            controller.getState().setPendingReleaseContainerId(container.getId());
            ImGui.openPopup("ConfirmRelease");
        }

        ImGui.separator();

        // 锁定
        boolean locked = container.isLocked();
        if (ImGui.menuItem(locked ? "解锁" : "锁定", "L")) {
            controller.setContainerLocked(container.getId(), !locked);
        }
    }

    /**
     * 处理拖拽到某个目标 Container 上的行为：
     * - 目标是组：作为其子容器
     * - 否则：移动到目标节点之前（同级排序）
     */
    private void handleDropPayload(Container container, String payloadId) {
        try {
            UUID draggedId = UUID.fromString(payloadId);
            Container draggedContainer = controller.getContainer(draggedId);
            if (draggedContainer == null || draggedContainer == container) {
                return;
            }

            boolean targetIsGroup = container.getType() == ContainerType.GROUP;
            boolean cyclic = container.isDescendantOf(draggedContainer);
            if (cyclic) {
                LOGGER.warn("[ContainerTreeInteractionHandler] 拖拽会造成循环引用，已忽略");
                return;
            }

            if (targetIsGroup) {
                // 组内：作为子节点追加
                controller.moveContainerAsChild(draggedId, container.getId());
                LOGGER.info("[ContainerTreeInteractionHandler] 移动 {} 到组 {}", draggedContainer.getName(), container.getName());
            } else {
                // 普通节点：移动到目标之前，利用 CommandService 内的相对排序逻辑
                controller.moveContainerBefore(draggedId, container.getId());
                LOGGER.info("[ContainerTreeInteractionHandler] 将 {} 移动到 {} 之前", draggedContainer.getName(), container.getName());
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[ContainerTreeInteractionHandler] 无法解析拖拽载荷为 UUID: {}", payloadId);
        }
    }

    /**
     * 图标点击的业务处理：锁定/可见性切换。
     */
    void handleIconClicks(Container container, boolean lockClicked, boolean visibilityClicked) {
        if (lockClicked) {
            controller.setContainerLocked(container.getId(), !container.isLocked());
        }
        if (visibilityClicked) {
            // 切换容器状态（HIDDEN/VISIBLE），这会控制 Minecraft 中方块的显示/隐藏
            controller.toggleContainerState(container.getId());
        }
    }
}