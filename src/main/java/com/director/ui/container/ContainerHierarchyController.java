package com.director.ui.container;

import java.util.List;
import java.util.UUID;

/**
 * 负责容器的层级、排序与拖拽相关操作。
 */
public class ContainerHierarchyController {

    private final ContainerCommandService commandService;

    public ContainerHierarchyController(ContainerCommandService commandService) {
        this.commandService = commandService;
    }

    public void moveContainer(UUID containerId, UUID targetParentId, int index) {
        commandService.moveContainer(containerId, targetParentId, index);
    }

    /**
     * 批量移动容器（一次 undo）。拖拽多选到组/根/节点前时使用。
     */
    public void moveContainers(List<UUID> ids, UUID targetParentId, int targetIndex) {
        commandService.moveContainers(ids, targetParentId, targetIndex);
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
}