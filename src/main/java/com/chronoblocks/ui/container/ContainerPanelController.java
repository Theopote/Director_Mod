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
		private final ContainerAnimationController animationController;
		private final ContainerHierarchyController hierarchyController;

    public ContainerPanelController(ContainerManager containerManager,
                                    TimelineCommandHistory commandHistory,
                                    ContainerPanelState state) {
        this.containerManager = containerManager;
        this.state = state;
        this.commandService = new ContainerCommandService(containerManager, commandHistory);
			this.animationController = new ContainerAnimationController(commandService);
			this.hierarchyController = new ContainerHierarchyController(commandService);
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
        selectContainer(id, multiSelect, false);
    }
    
    public void selectContainer(UUID id, boolean multiSelect, boolean rangeSelect) {
        Container container = getContainer(id);
        if (container != null) {
            // ✅ 选择变化时取消正在进行的重命名（避免编辑会话混乱）
            cancelEditingIfActive();
            
			Container active = containerManager.getActive();
			if (!multiSelect && !rangeSelect && active != null && active.isIntakeEnabled() && active != container) {
				org.slf4j.LoggerFactory.getLogger(ContainerPanelController.class)
					.info("[ContainerPanelController] 选择其他容器 -> 结束激活容器的实时接收: {}", active.getName());
				commandService.toggleIntake(active.getId());
			}
            containerManager.selectContainer(container, multiSelect, rangeSelect);
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

    public void generateDemolitionContainerFromSelection() {
        commandService.generateDemolitionContainerFromSelection();
    }

    public void deleteSelectedContainers() {
        // ✅ 删除容器时取消正在进行的重命名（避免编辑已删除的容器）
        cancelEditingIfActive();
        
        org.slf4j.LoggerFactory.getLogger(ContainerPanelController.class).debug("[ContainerPanelController] 执行删除选中容器");
        commandService.deleteSelectedContainers();
    }

    public void clearAllContainers() {
        org.slf4j.LoggerFactory.getLogger(ContainerPanelController.class).debug("[ContainerPanelController] 执行清空所有容器");
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
        // ✅ 移动容器时取消正在进行的重命名（避免编辑会话混乱）
        cancelEditingIfActive();
		hierarchyController.moveContainer(containerId, targetParentId, index);
    }

    public void moveContainerAsChild(UUID draggedId, UUID parentId) {
        cancelEditingIfActive();
		hierarchyController.moveContainerAsChild(draggedId, parentId);
    }

    public void moveContainerBefore(UUID draggedId, UUID referenceId) {
        cancelEditingIfActive();
		hierarchyController.moveContainerBefore(draggedId, referenceId);
    }

    public void moveContainerAfter(UUID draggedId, UUID referenceId) {
        cancelEditingIfActive();
		hierarchyController.moveContainerAfter(draggedId, referenceId);
    }

    public void moveContainerByOffset(UUID containerId, int offset) {
        cancelEditingIfActive();
		hierarchyController.moveContainerByOffset(containerId, offset);
    }

    public void swapContainers(UUID firstId, UUID secondId) {
        cancelEditingIfActive();
		hierarchyController.swapContainers(firstId, secondId);
    }

    /* -------------------- 属性变更 -------------------- */

    public void renameContainer(UUID containerId, String newName) {
        commandService.renameContainer(containerId, newName);
    }

    public void setContainerType(UUID containerId, com.chronoblocks.core.container.ContainerType newType) {
        commandService.setContainerType(containerId, newType);
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
    
    /**
     * 批量设置选中容器的颜色（标题和/或图标）
     * @param titleColor 标题颜色（ARGB），null 表示不设置
     * @param iconColor 图标颜色（ARGB），null 表示不设置
     */
    public void batchSetContainerColors(Integer titleColor, Integer iconColor) {
        Set<Container> selected = containerManager.getSelectedContainersView();
        if (!selected.isEmpty()) {
            commandService.batchSetContainerColors(selected, titleColor, iconColor);
        }
    }
    
    /**
     * 批量设置选中容器的标签颜色
     * @param tagColor 标签颜色（ARGB）
     */
    public void batchSetContainerTagColors(Integer tagColor) {
        Set<Container> selected = containerManager.getSelectedContainersView();
        if (!selected.isEmpty() && tagColor != null) {
            // 批量设置标签颜色
            for (Container container : selected) {
                commandService.setMetadata(container.getId(), "tagColor", tagColor);
            }
        }
    }

    public void setContainerOpacity(UUID containerId, float opacity) {
        commandService.setContainerOpacity(containerId, opacity);
    }

    public void setPresetMode(UUID containerId, com.chronoblocks.core.container.AnimationMode mode) {
		animationController.setPresetMode(containerId, mode);
    }

    public void setPresetDuration(UUID containerId, float duration) {
		animationController.setPresetDuration(containerId, duration);
    }

    public void setPresetDelay(UUID containerId, float delay) {
		animationController.setPresetDelay(containerId, delay);
    }

    public void setPresetEasing(UUID containerId, String easing) {
		animationController.setPresetEasing(containerId, easing);
    }

    public void setPresetOrdering(UUID containerId, String ordering) {
		animationController.setPresetOrdering(containerId, ordering);
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
        // ✅ 折叠/展开树节点时取消正在进行的重命名（避免节点被折叠导致输入框消失）
        cancelEditingIfActive();
        
        if (state.isExpandAll()) {
            state.collapseAllNodes();
        } else {
            state.expandAllNodes();
        }
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
    
    /**
     * 取消选中所有容器（在空白区域点击时调用）
     */
    public void clearSelection() {
        // ✅ 取消选择时也取消正在进行的重命名
        cancelEditingIfActive();
        containerManager.clearSelection();
    }
    
    /**
     * 获取容器管理器（供交互处理器使用）
     */
    ContainerManager getContainerManager() {
        return containerManager;
    }
    
    /**
     * 取消正在进行的编辑会话（如果存在）
     * 应在以下情况调用：
     * - 选择被程序性改变
     * - 树节点被折叠/展开
     * - 容器被移动/删除
     */
    public void cancelEditingIfActive() {
        if (state.getRenamingContainerId() != null) {
            state.setRenamingContainerId(null);
        }
    }
}

