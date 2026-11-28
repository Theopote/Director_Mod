package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 批量移动容器的复合命令：一次拖放 = 一条 undo 记录。
 * execute 按顺序移动；undo 必须逆序回退，否则同组内 index 会错乱。
 * <p>
 * 同父移动时的 index 修正：目标 index 须扣掉「在它前面且会被移走的数量」；
 * 见 {@link MoveIndexCorrection}。拖入选中块内部的 no-op 在 CommandService 中提前过滤。
 */
public class MoveContainersCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID targetParentId;
    private final int targetIndexBase;

    /** 每项：(containerId, oldParentId, oldIndex) — 构建时快照，undo 时按此恢复 */
    private final List<Item> items;
    private boolean executed;

    public MoveContainersCommand(ContainerManager manager,
                                 UUID targetParentId,
                                 int targetIndexBase,
                                 List<Item> items) {
        this.manager = manager;
        this.targetParentId = targetParentId;
        this.targetIndexBase = targetIndexBase;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    @Override
    public void execute() {
        Container targetParent = targetParentId != null ? manager.getContainer(targetParentId) : null;
        if (targetParentId != null && targetParent == null) {
            return;
        }
        if (targetParent != null && !manager.isEditable(targetParent)) {
            return;
        }
        for (Item item : items) {
            Container container = manager.getContainer(item.containerId);
            if (container == null || !manager.requireEditableTree(container, "批量移动")
                    || (container.getParent() != null && !manager.isEditable(container.getParent()))
                    || (targetParent != null && (targetParent == container
                        || targetParent.isDescendantOf(container)))) {
                return;
            }
        }

        boolean append = targetIndexBase < 0;

        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            Container c = manager.getContainer(it.containerId);
            if (c == null) continue;

            int insertIndex;
            if (append) {
                insertIndex = -1;
            } else {
                int k = 0;
                for (int j = 0; j <= i; j++) {
                    Item x = items.get(j);
                    if (sameRef(x.oldParentId, targetParentId) && x.oldIndex < targetIndexBase) {
                        k++;
                    }
                }
                insertIndex = targetIndexBase + i - k;
            }
            manager.moveContainerToParent(c, targetParent, insertIndex);
        }
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            return;
        }
        for (Item item : items) {
            Container container = manager.getContainer(item.containerId);
            Container oldParent = item.oldParentId != null ? manager.getContainer(item.oldParentId) : null;
            if (container == null || !manager.requireEditableTree(container, "撤销批量移动")
                    || (item.oldParentId != null && oldParent == null)
                    || (container.getParent() != null && !manager.isEditable(container.getParent()))
                    || (oldParent != null && !manager.isEditable(oldParent))
                    || (oldParent != null && (oldParent == container || oldParent.isDescendantOf(container)))) {
                return;
            }
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            Container c = manager.getContainer(it.containerId);
            Container oldParent = it.oldParentId != null ? manager.getContainer(it.oldParentId) : null;
            manager.moveContainerToParent(c, oldParent, it.oldIndex);
            c.setLocked(it.oldLocked);
            c.setState(it.oldState);
        }
        executed = false;
    }

    @Override
    public String getDescription() {
        int n = items.size();
        return n <= 1 ? "移动容器" : ("移动 " + n + " 个容器");
    }

    private static boolean sameRef(UUID a, UUID b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    public static final class Item {
        public final UUID containerId;
        public final UUID oldParentId;
        public final int oldIndex;
        public final boolean oldLocked;
        public final ContainerState oldState;

        public Item(UUID containerId, UUID oldParentId, int oldIndex) {
            this(containerId, oldParentId, oldIndex, false, ContainerState.VISIBLE);
        }

        public Item(UUID containerId, UUID oldParentId, int oldIndex, boolean oldLocked, ContainerState oldState) {
            this.containerId = containerId;
            this.oldParentId = oldParentId;
            this.oldIndex = oldIndex;
            this.oldLocked = oldLocked;
            this.oldState = oldState != null ? oldState : ContainerState.VISIBLE;
        }
    }
}
