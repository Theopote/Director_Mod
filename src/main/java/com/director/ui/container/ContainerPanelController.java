package com.director.ui.container;

import com.director.core.container.Container;
import com.director.core.container.ContainerIntakeSubscriber;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.timeline.commands.TimelineCommandHistory;

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
            cancelEditingIfActive();
			Container active = containerManager.getActive();
			if (!multiSelect && !rangeSelect && active != null && active.isIntakeEnabled() && active != container) {
				org.slf4j.LoggerFactory.getLogger(ContainerPanelController.class)
					.info("[ContainerPanelController] 选择其他容器 -> 结束激活容器的实时接收: {}", active.getName());
				commandService.toggleIntake(active.getId());
			}
            // Shift 范围选择：按「当前可见」线性顺序，折叠的子节点不参与
            java.util.List<Container> visibleOrder = rangeSelect ? getContainersInVisibleOrder() : null;
            containerManager.selectContainer(container, multiSelect, rangeSelect, visibleOrder);
        }
    }

    /** 按树的前序、仅包含当前可见节点（折叠的父节点其子不加入），用于 Shift 范围选择 */
    private java.util.List<Container> getContainersInVisibleOrder() {
        java.util.List<Container> out = new java.util.ArrayList<>();
        for (Container root : getRootContainers()) {
            collectVisibleInOrder(root, out);
        }
        return out;
    }

    private void collectVisibleInOrder(Container c, java.util.List<Container> out) {
        out.add(c);
        if (state.isNodeExpanded(c.getId())) {
            for (Container child : c.getChildren()) {
                collectVisibleInOrder(child, out);
            }
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
        handlePostCreate(commandService.createContainer(
            DirectorI18n.translate(DirectorLang.ContainerPanel.DEFAULT_CONTAINER_NAME), ContainerType.GENERAL, null));
    }

    public void createNewGroupContainer() {
        handlePostCreate(commandService.createContainer(
            DirectorI18n.translate(DirectorLang.ContainerPanel.DEFAULT_GROUP_NAME), ContainerType.GROUP, null));
    }

    public void createNewContainer(String baseName, ContainerType type) {
        handlePostCreate(commandService.createContainer(baseName, type, null));
    }

    public void createChildContainer(ContainerType type, UUID parentId) {
        String baseName = type == ContainerType.GROUP
            ? DirectorI18n.translate(DirectorLang.ContainerPanel.DEFAULT_GROUP_NAME)
            : type.getDisplayName();
        handlePostCreate(commandService.createContainer(baseName, type, parentId));
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

    public void copyContainerToClipboard(UUID containerId) {
        commandService.copyContainerToClipboard(containerId);
    }

    public boolean isClipboardEmpty() {
        return commandService.isClipboardEmpty();
    }

    public void pasteClipboard(UUID parentId, int insertIndex) {
        commandService.pasteClipboard(parentId, insertIndex);
    }

    /* -------------------- 分组 / 合并 -------------------- */

    public void groupSelectedContainers() {
        Container group = commandService.groupSelectedContainers();
        if (group != null) {
            state.setRenamingContainerId(group.getId());
        }
    }

    public void ungroupSelectedContainers() {
        cancelEditingIfActive();
        commandService.ungroupSelectedContainers();
    }

    public boolean canUngroupSelection() {
        return commandService.canUngroupSelection();
    }

    public void mergeSelection(String mergeName) {
        Container merged = commandService.mergeSelection(mergeName);
        if (merged != null) {
            state.setRenamingContainerId(merged.getId());
        }
    }

    /**
     * 批量移动容器（一次 undo）。多选拖到组/根/节点前时调用。
     */
    public void moveContainers(java.util.List<UUID> ids, UUID targetParentId, int targetIndex) {
        cancelEditingIfActive();
        hierarchyController.moveContainers(ids, targetParentId, targetIndex);
    }

    public void moveContainerByOffset(UUID containerId, int offset) {
        cancelEditingIfActive();
		hierarchyController.moveContainerByOffset(containerId, offset);
    }

    /* -------------------- 属性变更 -------------------- */

    public void renameContainer(UUID containerId, String newName) {
        commandService.renameContainer(containerId, newName);
    }

    public void setContainerType(UUID containerId, com.director.core.container.ContainerType newType) {
        commandService.setContainerType(containerId, newType);
    }

    public void setContainerLocked(UUID containerId, boolean locked) {
        commandService.setContainerLocked(containerId, locked);
    }

    public void toggleContainerState(UUID containerId) {
        commandService.toggleContainerState(containerId);
    }
    
    /**
     * 批量设置选中容器的标签颜色
     * @param tagColor 标签颜色（ARGB）
     */
    public void batchSetContainerTagColors(Integer tagColor) {
        Set<Container> selected = containerManager.getSelectedContainersView();
        if (!selected.isEmpty()) {
            // 批量设置标签颜色
            commandService.batchSetMetadata(selected, "tagColor", tagColor);
        }
    }

    /**
     * 批量设置选中容器的锁定状态（一次 undo/redo）
     */
    public void batchSetContainerLocked(boolean locked) {
        Set<Container> selected = containerManager.getSelectedContainersView();
        if (!selected.isEmpty()) {
            commandService.batchSetContainerLocked(selected, locked);
        }
    }

    /**
     * 批量设置选中容器的可见性状态（一次 undo/redo）
     */
    public void batchSetContainerState(com.director.core.container.ContainerState state) {
        Set<Container> selected = containerManager.getSelectedContainersView();
        if (!selected.isEmpty()) {
            commandService.batchSetContainerState(selected, state);
        }
    }

    public void setPresetMode(UUID containerId, com.director.core.container.AnimationMode mode) {
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
    
    /**
     * 取消选中所有容器（在空白区域点击时调用）
     */
    public void clearSelection() {
        // ✅ 取消选择时也取消正在进行的重命名
        cancelEditingIfActive();
        containerManager.clearSelection();
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

