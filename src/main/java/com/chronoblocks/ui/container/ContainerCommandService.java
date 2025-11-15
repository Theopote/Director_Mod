package com.chronoblocks.ui.container;

import com.chronoblocks.core.actions.SelectionToContainerAction;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerAnimationPreset;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.container.commands.*;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 封装所有与容器编辑相关的业务操作，供 UI 控制层调用。
 */
public class ContainerCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerCommandService.class);

    private final ContainerManager containerManager;
    private final TimelineCommandHistory commandHistory;
    private final ContainerClipboard clipboard = new ContainerClipboard();

    public ContainerCommandService(ContainerManager containerManager,
                                   TimelineCommandHistory commandHistory) {
        this.containerManager = containerManager;
        this.commandHistory = commandHistory;
    }

    /* ---------------------- 创建 / 删除 ---------------------- */

    public Container createContainer(String baseName, ContainerType type, UUID parentId) {
        Container parent = parentId != null ? containerManager.getContainer(parentId) : null;
        if (parent != null && parent.getType() != ContainerType.GROUP) {
            parent = null;
        }
        String uniqueName = containerManager.generateUniqueName(baseName);
        CreateContainerCommand command = new CreateContainerCommand(containerManager, uniqueName, type, parent);
        execute(command);
        return command.getCreatedContainer();
    }

    public void deleteSelectedContainers() {
        Collection<Container> selection = containerManager.getSelectedContainersView();
        if (selection.isEmpty()) {
            return;
        }
        execute(new DeleteContainersCommand(containerManager, selection));
    }

    public void clearAllContainers() {
        execute(new ClearAllContainersCommand(containerManager));
    }

    public void generateContainerFromSelection() {
        try {
            SelectionToContainerAction.execute();
        } catch (Exception ex) {
            LOGGER.error("[ContainerCommandService] 从选区生成容器失败", ex);
        }
    }

    /* ---------------------- 复制 / 粘贴 ---------------------- */

    public void copySelectionToClipboard() {
        clipboard.capture(containerManager, containerManager.getSelectedContainersView());
    }

    public void copyContainerToClipboard(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container != null) {
            clipboard.capture(containerManager, Collections.singletonList(container));
        }
    }

    public boolean isClipboardEmpty() {
        return clipboard.isEmpty();
    }

    public void pasteClipboard(UUID targetParentId, int insertIndex) {
        if (clipboard.isEmpty()) {
            return;
        }
        Container targetParent = targetParentId != null ? containerManager.getContainer(targetParentId) : null;
        if (targetParent != null && targetParent.getType() != ContainerType.GROUP) {
            targetParent = null;
            insertIndex = -1;
        }
        PasteContainersCommand command = new PasteContainersCommand(
            containerManager,
            clipboard.getTemplates(),
            targetParent,
            insertIndex
        );
        execute(command);
    }

    public void duplicateSelectedContainers() {
        Collection<Container> selection = containerManager.getSelectedContainersView();
        if (selection.isEmpty()) {
            return;
        }
        execute(new DuplicateContainersCommand(containerManager, selection));
    }

    public void duplicateContainer(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new DuplicateContainersCommand(containerManager, Collections.singletonList(container)));
    }

    /* ---------------------- 分组 / 合并 ---------------------- */

    public Container groupSelectedContainers() {
        Collection<Container> selection = containerManager.getSelectedContainersView();
        if (selection.isEmpty()) {
            return null;
        }
        GroupContainersCommand command = new GroupContainersCommand(containerManager, selection, null);
        execute(command);
        return command.getGroupContainer();
    }

    public Container mergeSelection(String mergeName) {
        Collection<Container> selection = containerManager.getSelectedContainersView();
        if (selection.isEmpty()) {
            return null;
        }
        MergeContainersCommand command = new MergeContainersCommand(containerManager, selection, mergeName);
        execute(command);
        return command.getMergedContainer();
    }

    /* ---------------------- 排序 / 拖拽 ---------------------- */

    public void moveContainer(UUID containerId, UUID targetParentId, int index) {
        Container container = containerManager.getContainer(containerId);
        Container targetParent = targetParentId != null ? containerManager.getContainer(targetParentId) : null;
        if (container == null) {
            return;
        }
        execute(new MoveContainerCommand(containerManager, container, targetParent, index));
    }

    public void moveContainerAsChild(UUID draggedId, UUID parentId) {
        Container dragged = containerManager.getContainer(draggedId);
        Container parent = parentId != null ? containerManager.getContainer(parentId) : null;
        if (dragged == null) {
            return;
        }
        if (parent != null) {
            if (parent == dragged || parent.isDescendantOf(dragged) || parent.getType() != ContainerType.GROUP) {
                parent = null;
            }
        }
        execute(new MoveContainerCommand(containerManager, dragged, parent, -1));
    }

    public void moveContainerBefore(UUID draggedId, UUID referenceId) {
        moveContainerRelative(draggedId, referenceId, true);
    }

    public void moveContainerAfter(UUID draggedId, UUID referenceId) {
        moveContainerRelative(draggedId, referenceId, false);
    }

    public void moveContainerByOffset(UUID containerId, int offset) {
        if (offset == 0) {
            return;
        }
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        Container parent = container.getParent();
        List<Container> siblings = parent != null
            ? parent.getChildrenView()
            : containerManager.getRootContainersView();
        int currentIndex = parent != null
            ? parent.indexOfChild(container)
            : siblings.indexOf(container);
        if (currentIndex < 0) {
            return;
        }
        int targetIndex = Math.max(0, Math.min(siblings.size() - 1, currentIndex + offset));
        if (targetIndex == currentIndex) {
            return;
        }
        execute(new MoveContainerCommand(containerManager, container, parent, targetIndex));
    }

    private void moveContainerRelative(UUID draggedId, UUID referenceId, boolean insertBefore) {
        Container dragged = containerManager.getContainer(draggedId);
        Container reference = containerManager.getContainer(referenceId);
        if (dragged == null || reference == null || dragged == reference) {
            return;
        }
        if (reference.isDescendantOf(dragged)) {
            return;
        }
        Container targetParent = reference.getParent();
        List<Container> siblings = targetParent != null
            ? targetParent.getChildrenView()
            : containerManager.getRootContainersView();

        int referenceIndex = targetParent != null
            ? targetParent.indexOfChild(reference)
            : siblings.indexOf(reference);
        if (referenceIndex < 0) {
            return;
        }

        int targetIndex = insertBefore ? referenceIndex : referenceIndex + 1;
        Container draggedParent = dragged.getParent();
        if (Objects.equals(draggedParent, targetParent)) {
            int currentIndex = draggedParent != null
                ? draggedParent.indexOfChild(dragged)
                : siblings.indexOf(dragged);
            if (currentIndex < 0) {
                return;
            }
            if (currentIndex < targetIndex) {
                targetIndex--;
            }
            if (currentIndex == targetIndex) {
                return;
            }
        }
        execute(new MoveContainerCommand(containerManager, dragged, targetParent, targetIndex));
    }

    /* ---------------------- 属性变更 ---------------------- */

    public void renameContainer(UUID containerId, String newName) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new RenameContainerCommand(containerManager, container, newName));
    }

    public void setContainerLocked(UUID containerId, boolean locked) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new SetContainerLockedCommand(containerManager, container, locked));
    }

    public void toggleContainerState(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new ToggleContainerStateCommand(container));
    }

    public void setContainerState(UUID containerId, com.chronoblocks.core.container.ContainerState state) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new ToggleContainerStateCommand(container, state));
    }

    public void setContainerColor(UUID containerId, int color) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new SetContainerColorCommand(containerManager, container, color));
    }

    public void setContainerOpacity(UUID containerId, float opacity) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new SetContainerOpacityCommand(containerManager, container, opacity));
    }

    public void setContainerPreset(UUID containerId, ContainerAnimationPreset preset) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new SetContainerPresetCommand(containerManager, container, preset));
    }

    public void setPresetMode(UUID containerId, AnimationMode mode) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset().copy();
        preset.setMode(mode);
        execute(new SetContainerPresetCommand(containerManager, container, preset));
    }

    public void setPresetDuration(UUID containerId, float duration) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset().copy();
        preset.setSuggestedDuration(duration);
        execute(new SetContainerPresetCommand(containerManager, container, preset));
    }

    public void setPresetDelay(UUID containerId, float delay) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset().copy();
        preset.setDelay(delay);
        execute(new SetContainerPresetCommand(containerManager, container, preset));
    }

    public void setPresetEasing(UUID containerId, String easing) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset().copy();
        preset.setEasing(easing);
        execute(new SetContainerPresetCommand(containerManager, container, preset));
    }

    public void setPresetOrdering(UUID containerId, String ordering) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        ContainerAnimationPreset preset = container.getDefaultAnimationPreset().copy();
        preset.setOrdering(ordering);
        execute(new SetContainerPresetCommand(containerManager, container, preset));
    }

    public void setMetadata(UUID containerId, String key, Object value) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        execute(new SetContainerMetadataCommand(containerManager, container, key, value));
    }

    public void setActiveContainer(UUID containerId) {
        if (containerId == null) {
            containerManager.setActive((Container) null);
            return;
        }
        Container container = containerManager.getContainer(containerId);
        if (container != null && container.getType() != ContainerType.GROUP) {
            containerManager.setActive(container);
        } else if (container != null) {
            LOGGER.warn("[ContainerCommandService] 组不能被设为激活容器: {}", container.getName());
        }
    }

    public void releaseContainer(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        containerManager.release(container.getId());
        if (containerManager.isActive(container)) {
            containerManager.setActive((UUID) null);
        }
    }

    public void toggleIntake(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        if (container.getType() == ContainerType.GROUP) {
            LOGGER.warn("[ContainerCommandService] 组无法开启实时接收: {}", container.getName());
            return;
        }
        boolean newState = !container.isIntakeEnabled();
        container.setIntakeEnabled(newState);
        if (newState) {
            containerManager.setActive(container);
        } else if (containerManager.isActive(container)) {
            containerManager.setActive((UUID) null);
        }
    }

    public void swapContainers(UUID firstId, UUID secondId) {
        if (firstId == null || secondId == null || firstId.equals(secondId)) {
            return;
        }
        Container first = containerManager.getContainer(firstId);
        Container second = containerManager.getContainer(secondId);
        if (first == null || second == null) {
            return;
        }
        execute(new SwapContainersCommand(containerManager, firstId, secondId));
    }

    /* ---------------------- 查询相关 ---------------------- */

    public boolean hasSelection() {
        return !containerManager.getSelectedContainersView().isEmpty();
    }

    public boolean canUndo() {
        return commandHistory.canUndo();
    }

    public boolean canRedo() {
        return commandHistory.canRedo();
    }

    public void undo() {
        if (commandHistory.canUndo()) {
            commandHistory.undo();
        }
    }

    public void redo() {
        if (commandHistory.canRedo()) {
            commandHistory.redo();
        }
    }

    /* ---------------------- 命令执行 ---------------------- */

    private void execute(ContainerCommand command) {
        if (command == null) {
            return;
        }
        commandHistory.executeCommand(new ContainerTimelineCommandAdapter(command));
    }

    /* ---------------------- 内部剪贴板 ---------------------- */

    private static final class ContainerClipboard {
        private final List<Container> templates = new ArrayList<>();

        void capture(ContainerManager manager, Collection<Container> sources) {
            templates.clear();
            if (manager == null || sources == null || sources.isEmpty()) {
                return;
            }
            List<Container> topLevel = manager.getTopLevelSelection(sources);
            for (Container source : topLevel) {
                if (source != null) {
                    templates.add(copyTemplate(source));
                }
            }
        }

        boolean isEmpty() {
            return templates.isEmpty();
        }

        List<Container> getTemplates() {
            return Collections.unmodifiableList(templates);
        }

        private static Container copyTemplate(Container source) {
            Container clone = source.clone();
            restoreNames(clone, source);
            return clone;
        }

        private static void restoreNames(Container copy, Container source) {
            copy.setName(source.getName());
            List<Container> copyChildren = copy.getChildren();
            List<Container> sourceChildren = source.getChildren();
            int limit = Math.min(copyChildren.size(), sourceChildren.size());
            for (int i = 0; i < limit; i++) {
                restoreNames(copyChildren.get(i), sourceChildren.get(i));
            }
        }
    }
}

