package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerIntakeSubscriber;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * UI 控制层：负责任务调度、状态同步，与业务命令层解耦。
 * 所有业务操作委托给 {@link ContainerCommandService}，自身保持“无副作用”。
 */
public class ContainerPanelController {

    private final ContainerManager containerManager;
    private final ContainerPanelState state;
    private final ContainerCommandService commandService;

    public ContainerPanelController(ContainerManager containerManager,
                                    TimelineCommandHistory commandHistory,
                                    ContainerPanelState state) {
        this.containerManager = containerManager;
        this.state = state;
        this.commandService = new ContainerCommandService(containerManager, commandHistory);
    }

    /* -------------------- 查询辅助 -------------------- */

    public ContainerPanelState getState() {
        return state;
    }

    public Container getContainer(UUID id) {
        return containerManager.getContainer(id);
    }

    public List<Container> getRootContainers() {
        return containerManager.getRootContainersView();
    }

    public Set<Container> getSelectedContainers() {
        return containerManager.getSelectedContainersView();
    }

    public Container getSelectedContainer() {
        return containerManager.getSelectedContainer();
    }

    public Container getActiveContainer() {
        return containerManager.getActive();
    }

    public boolean isContainerSelected(UUID id) {
        Container container = getContainer(id);
        return container != null && containerManager.isSelected(container);
    }

    public void selectContainer(UUID id, boolean multiSelect) {
        Container container = getContainer(id);
        if (container != null) {
            containerManager.selectContainer(container, multiSelect);
        }
    }

    public boolean isActive(UUID id) {
        Container container = getContainer(id);
        return container != null && containerManager.isActive(container);
    }

    public boolean hasSelection() {
        return commandService.hasSelection();
    }

    public boolean canMergeSelection() {
        return containerManager.canMergeSelection();
    }

    public boolean canUndo() {
        return commandService.canUndo();
    }

    public boolean canRedo() {
        return commandService.canRedo();
    }

    public void undo() {
        commandService.undo();
    }

    public void redo() {
        commandService.redo();
    }

    /* -------------------- 创建 / 删除 -------------------- */

    public void createNewContainer() {
        handlePostCreate(commandService.createContainer("容器", ContainerType.CUSTOM, null));
    }

    public void createNewGroupContainer() {
        handlePostCreate(commandService.createContainer("组", ContainerType.GROUP, null));
    }

    public void createNewContainer(String baseName, ContainerType type) {
        handlePostCreate(commandService.createContainer(baseName, type, null));
    }

    public void createChildContainer(ContainerType type, UUID parentId) {
        handlePostCreate(commandService.createContainer(
            type == ContainerType.GROUP ? "组" : type.getDisplayName(), type, parentId));
    }

    private void handlePostCreate(Container created) {
        if (created != null) {
            containerManager.selectContainer(created);
            state.setRenamingContainerId(created.getId());
        }
    }

    public void generateContainerFromSelection() {
        commandService.generateContainerFromSelection();
    }

    public void deleteSelectedContainers() {
        commandService.deleteSelectedContainers();
    }

    public void clearAllContainers() {
        commandService.clearAllContainers();
    }

    /* -------------------- 复制 / 粘贴 -------------------- */

    public void copySelectionToClipboard() {
        commandService.copySelectionToClipboard();
    }

    public void copyContainerToClipboard(UUID containerId) {
        commandService.copyContainerToClipboard(containerId);
    }

    public boolean isClipboardEmpty() {
        return commandService.isClipboardEmpty();
    }

    public void pasteClipboard(UUID parentId, int insertIndex) {
        commandService.pasteClipboard(parentId, insertIndex);
    }

    public void duplicateSelectedContainers() {
        commandService.duplicateSelectedContainers();
    }

    public void duplicateContainer(UUID containerId) {
        commandService.duplicateContainer(containerId);
    }

    /* -------------------- 分组 / 合并 -------------------- */

    public void groupSelectedContainers() {
        Container group = commandService.groupSelectedContainers();
        if (group != null) {
            state.setRenamingContainerId(group.getId());
        }
    }

    public void mergeSelection(String mergeName) {
        Container merged = commandService.mergeSelection(mergeName);
        if (merged != null) {
            state.setRenamingContainerId(merged.getId());
        }
    }

    /* -------------------- 排序 / 拖拽 -------------------- */

    public void moveContainer(UUID containerId, UUID targetParentId, int index) {
        commandService.moveContainer(containerId, targetParentId, index);
    }

    public void moveContainerAsChild(UUID draggedId, UUID parentId) {
        commandService.moveContainerAsChild(draggedId, parentId);
    }

    public void moveContainerBefore(UUID draggedId, UUID referenceId) {
        commandService.moveContainerBefore(draggedId, referenceId);
    }

    public void moveContainerAfter(UUID draggedId, UUID referenceId) {
        commandService.moveContainerAfter(draggedId, referenceId);
    }

    public void moveContainerByOffset(UUID containerId, int offset) {
        commandService.moveContainerByOffset(containerId, offset);
    }

    public void swapContainers(UUID firstId, UUID secondId) {
        commandService.swapContainers(firstId, secondId);
    }

    /* -------------------- 属性变更 -------------------- */

    public void renameContainer(UUID containerId, String newName) {
        commandService.renameContainer(containerId, newName);
    }

    public void setContainerLocked(UUID containerId, boolean locked) {
        commandService.setContainerLocked(containerId, locked);
    }

    public void toggleContainerState(UUID containerId) {
        commandService.toggleContainerState(containerId);
    }

    public void setContainerState(UUID containerId, com.chronoblocks.core.container.ContainerState state) {
        commandService.setContainerState(containerId, state);
    }

    public void setContainerColor(UUID containerId, int color) {
        commandService.setContainerColor(containerId, color);
    }

    public void setContainerOpacity(UUID containerId, float opacity) {
        commandService.setContainerOpacity(containerId, opacity);
    }

    public void setPresetMode(UUID containerId, com.chronoblocks.core.container.AnimationMode mode) {
        commandService.setPresetMode(containerId, mode);
    }

    public void setPresetDuration(UUID containerId, float duration) {
        commandService.setPresetDuration(containerId, duration);
    }

    public void setPresetDelay(UUID containerId, float delay) {
        commandService.setPresetDelay(containerId, delay);
    }

    public void setPresetEasing(UUID containerId, String easing) {
        commandService.setPresetEasing(containerId, easing);
    }

    public void setPresetOrdering(UUID containerId, String ordering) {
        commandService.setPresetOrdering(containerId, ordering);
    }

    public void setMetadata(UUID containerId, String key, Object value) {
        commandService.setMetadata(containerId, key, value);
    }

    public void setActiveContainer(UUID containerId) {
        commandService.setActiveContainer(containerId);
    }

    public void releaseContainer(UUID containerId) {
        commandService.releaseContainer(containerId);
    }

    public void toggleIntake(UUID containerId) {
        commandService.toggleIntake(containerId);
    }

    public void intakeCurrentSelection() {
        // 优先使用选中的容器，如果没有则使用激活容器
        Container selectedContainer = getSelectedContainer();
        ContainerIntakeSubscriber.getInstance().intakeCurrentSelection(selectedContainer);
    }

    /* -------------------- 状态辅助 -------------------- */

    public void toggleExpandAll() {
        state.setExpandAll(!state.isExpandAll());
    }

    public void setRenamingContainer(UUID containerId) {
        state.setRenamingContainerId(containerId);
    }

    public void clearRenamingContainer() {
        state.setRenamingContainerId(null);
    }

    public void setContextMenuContainer(UUID containerId) {
        state.setContextMenuContainerId(containerId);
    }

    public void clearContextMenuContainer() {
        state.setContextMenuContainerId(null);
    }
}

