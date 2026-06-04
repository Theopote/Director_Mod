package com.director.ui.container;

import com.director.core.actions.SelectionToContainerAction;
import com.director.core.actions.SelectionToDemolitionContainerAction;
import com.director.core.container.Container;
import com.director.core.container.ContainerAnimationPreset;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.core.container.AnimationMode;
import com.director.core.container.commands.*;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

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
        if (isTimelineControlled(parent, "添加子容器")) {
            return null;
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
        if (hasTimelineControlledContainer(selection, "删除")) {
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

    /**
     * 从当前选区生成“拆除容器”（用于清除轨道）。
     */
    public void generateDemolitionContainerFromSelection() {
        try {
            SelectionToDemolitionContainerAction.execute();
        } catch (Exception ex) {
            LOGGER.error("[ContainerCommandService] 从选区生成拆除容器失败", ex);
        }
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
        if (isTimelineControlled(targetParent, "粘贴子容器")) {
            return;
        }
        PasteContainersCommand command = new PasteContainersCommand(
            containerManager,
            clipboard.getTemplates(),
            targetParent,
            insertIndex
        );
        execute(command);
    }

    /* ---------------------- 分组 / 合并 ---------------------- */

    public Container groupSelectedContainers() {
        Collection<Container> selection = containerManager.getSelectedContainersView();
        if (selection.isEmpty()) {
            return null;
        }
        // 锁定保护：任一容器被锁定则拒绝分组
        for (Container c : selection) {
            if (c != null && c.isLocked()) {
                LOGGER.warn("[ContainerCommandService] 分组失败：存在被锁定的容器 {}", c.getName());
                return null;
            }
        }
        if (hasTimelineControlledContainer(selection, "分组")) {
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
        // 锁定保护：任一容器被锁定则拒绝合并
        for (Container c : selection) {
            if (c != null && c.isLocked()) {
                LOGGER.warn("[ContainerCommandService] 合并失败：存在被锁定的容器 {}", c.getName());
                return null;
            }
        }
        if (hasTimelineControlledContainer(selection, "合并")) {
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
        if (isTimelineControlled(container, "移动")) {
            return;
        }
        if (isTimelineControlled(targetParent, "接收移动")) {
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
        if (isTimelineControlled(dragged, "移动")) {
            return;
        }
        if (isTimelineControlled(parent, "接收移动")) {
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
        if (isTimelineControlled(container, "移动")) {
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

    /**
     * 批量移动容器到同一父级下，一次 undo/redo。
     * 会过滤：null、循环（目标为自身或自身子孙）、仅保留顶层（父子同在时只移父）。
     *
     * @param ids            要移动的容器 ID，按原树顺序排序后执行
     * @param targetParentId 目标父级 ID，null 表示根
     * @param targetIndex    起始插入索引，&lt;0 表示追加到末尾
     */
    public void moveContainers(List<UUID> ids, UUID targetParentId, int targetIndex) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Container targetParent = targetParentId != null ? containerManager.getContainer(targetParentId) : null;
        if (targetParentId != null && targetParent == null) {
            return;
        }
        if (targetParent != null && targetParent.getType() != ContainerType.GROUP) {
            targetParent = null;
        }
        if (isTimelineControlled(targetParent, "接收移动")) {
            return;
        }

        List<Container> resolved = new ArrayList<>();
        for (UUID id : ids) {
            Container c = containerManager.getContainer(id);
            if (c == null) continue;
            if (isTimelineControlled(c, "移动")) continue;
            if (targetParent != null && (c == targetParent || targetParent.isDescendantOf(c))) {
                continue;
            }
            if (targetParentId != null && targetParentId.equals(id)) {
                continue;
            }
            resolved.add(c);
        }
        if (resolved.isEmpty()) return;

        List<Container> topLevel = containerManager.getTopLevelSelection(resolved);
        if (topLevel.isEmpty()) return;

        List<Container> rootView = containerManager.getRootContainersView();
        // 按原树中的 (父, 索引) 稳定排序
        topLevel.sort((a, b) -> {
            Container pa = a.getParent();
            Container pb = b.getParent();
            int cmp = compareRef(pa != null ? pa.getId() : null, pb != null ? pb.getId() : null);
            if (cmp != 0) return cmp;
            int ia = pa != null ? pa.indexOfChild(a) : rootView.indexOf(a);
            int ib = pb != null ? pb.indexOfChild(b) : rootView.indexOf(b);
            return Integer.compare(ia, ib);
        });

        List<MoveContainersCommand.Item> moveItems = new ArrayList<>();
        for (Container c : topLevel) {
            Container oldParent = c.getParent();
            UUID oldParentId = oldParent != null ? oldParent.getId() : null;
            int oldIndex = oldParent != null
                ? oldParent.indexOfChild(c)
                : rootView.indexOf(c);
            moveItems.add(new MoveContainersCommand.Item(
                c.getId(),
                oldParentId,
                oldIndex,
                c.isLocked(),
                c.getState()
            ));
        }

        // 同父移动且目标落在「选中块内部」[min, max+1] 时 no-op，避免顺序错乱与 undo 错位（见 MoveIndexCorrection）
        List<Integer> sameParentOldIndices = new ArrayList<>();
        for (MoveContainersCommand.Item it : moveItems) {
            if (MoveIndexCorrection.sameRef(it.oldParentId, targetParentId)) {
                sameParentOldIndices.add(it.oldIndex);
            }
        }
        if (MoveIndexCorrection.isNoOpWhenDroppingIntoSelectedBlock(sameParentOldIndices, targetIndex)) {
            return;
        }

        execute(new MoveContainersCommand(containerManager, targetParentId, targetIndex, moveItems));
    }

    private static int compareRef(UUID a, UUID b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private void moveContainerRelative(UUID draggedId, UUID referenceId, boolean insertBefore) {
        Container dragged = containerManager.getContainer(draggedId);
        Container reference = containerManager.getContainer(referenceId);
        if (dragged == null || reference == null || dragged == reference) {
            return;
        }
        if (isTimelineControlled(dragged, "移动")) {
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
        if (isTimelineControlled(container, "重命名")) {
            return;
        }
        execute(new RenameContainerCommand(containerManager, container, newName));
    }

    public void setContainerLocked(UUID containerId, boolean locked) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        if (isTimelineControlled(container, "锁定")) {
            return;
        }
        execute(new SetContainerLockedCommand(containerManager, container, locked));
    }

    public void toggleContainerState(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        if (isTimelineControlled(container, "切换可见性")) {
            return;
        }
        execute(new ToggleContainerStateCommand(container));
    }

    public void setContainerType(UUID containerId, ContainerType newType) {
        Container container = containerManager.getContainer(containerId);
        if (container == null || newType == null) {
            return;
        }
        if (isTimelineControlled(container, "修改类型")) {
            return;
        }
        // 不允许将类型改为 GROUP
        if (newType == ContainerType.GROUP) {
            LOGGER.warn("[ContainerCommandService] 不允许将容器类型改为 GROUP: {}", container.getName());
            return;
        }
        execute(new SetContainerTypeCommand(containerManager, container, newType));
    }

    public void setPresetMode(UUID containerId, AnimationMode mode) {
		updatePreset(containerId, preset -> preset.setMode(mode));
    }

    public void setPresetDuration(UUID containerId, float duration) {
		updatePreset(containerId, preset -> preset.setSuggestedDuration(duration));
    }

    public void setPresetDelay(UUID containerId, float delay) {
		updatePreset(containerId, preset -> preset.setDelay(delay));
    }

    public void setPresetEasing(UUID containerId, String easing) {
		updatePreset(containerId, preset -> preset.setEasing(easing));
    }

    public void setPresetOrdering(UUID containerId, String ordering) {
		updatePreset(containerId, preset -> preset.setOrdering(ordering));
    }

	/**
	 * 统一的预设更新入口：基于当前默认预设复制一份，应用修改函数后下发命令。
	 */
	public void updatePreset(UUID containerId, Consumer<ContainerAnimationPreset> operation) {
		if (operation == null) {
			return;
		}
		Container container = containerManager.getContainer(containerId);
		if (container == null) {
			return;
		}
		if (isTimelineControlled(container, "修改动画预设")) {
			return;
		}
		ContainerAnimationPreset preset = container.getDefaultAnimationPreset().copy();
		operation.accept(preset);
		execute(new SetContainerPresetCommand(containerManager, container, preset));
	}

    public void setMetadata(UUID containerId, String key, Object value) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        if (isTimelineControlled(container, "修改元数据")) {
            return;
        }
        execute(new SetContainerMetadataCommand(containerManager, container, key, value));
    }

    public void batchSetMetadata(Collection<Container> containers, String key, Object value) {
        if (containers == null || containers.isEmpty() || key == null || key.isBlank()) {
            return;
        }
        if (hasTimelineControlledContainer(containers, "批量修改元数据")) {
            return;
        }
        execute(new BatchSetContainerMetadataCommand(containerManager, containers, key, value));
    }

    /**
     * 批量设置容器锁定状态（一次 undo）
     */
    public void batchSetContainerLocked(Collection<Container> containers, boolean locked) {
        if (containers == null || containers.isEmpty()) {
            return;
        }
        if (hasTimelineControlledContainer(containers, "批量锁定")) {
            return;
        }
        execute(new BatchSetContainerLockedCommand(containerManager, containers, locked));
    }

    /**
     * 批量设置容器可见性状态（一次 undo）
     */
    public void batchSetContainerState(Collection<Container> containers, com.director.core.container.ContainerState state) {
        if (containers == null || containers.isEmpty() || state == null) {
            return;
        }
        if (hasTimelineControlledContainer(containers, "批量切换可见性")) {
            return;
        }
        execute(new BatchSetContainerStateCommand(containers, state));
    }

    public void releaseContainer(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        if (isTimelineControlled(container, "释放")) {
            return;
        }
        execute(new ReleaseContainerCommand(containerManager, container));
    }

    public void toggleIntake(UUID containerId) {
        Container container = containerManager.getContainer(containerId);
        if (container == null) {
            return;
        }
        if (isTimelineControlled(container, "实时接收")) {
            return;
        }
        if (container.getType() == ContainerType.GROUP) {
            LOGGER.warn("[ContainerCommandService] 组无法开启实时接收: {}", container.getName());
            return;
        }
        if (!container.isIntakeEnabled() && container.isLocked()) {
            LOGGER.warn("[ContainerCommandService] 已锁定容器无法开启实时接收: {}", container.getName());
            return;
        }
        execute(new ToggleIntakeCommand(containerManager, container));
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
        if (isTimelineControlled(first, "交换") || isTimelineControlled(second, "交换")) {
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

    private boolean hasTimelineControlledContainer(Collection<Container> containers, String action) {
        if (containers == null || containers.isEmpty()) {
            return false;
        }
        for (Container container : containers) {
            if (isTimelineControlled(container, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTimelineControlled(Container container, String action) {
        if (container == null) {
            return false;
        }
        var timelineService = com.director.ui.timeline.container.ContainerTimelineService.getInstance();
        if (timelineService.isContainerOnTimeline(container.getId())) {
            LOGGER.warn("[ContainerCommandService] 容器 {} 已在时间线上，拒绝{}",
                    container.getName(), action);
            return true;
        }
        for (Container child : container.getChildren()) {
            if (isTimelineControlled(child, action)) {
                LOGGER.warn("[ContainerCommandService] 容器 {} 包含已在时间线上的子容器，拒绝{}",
                        container.getName(), action);
                return true;
            }
        }
        return false;
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

