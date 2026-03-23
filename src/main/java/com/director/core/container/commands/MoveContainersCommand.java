package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

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
    }

    @Override
    public void undo() {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            Container c = manager.getContainer(it.containerId);
            if (c == null) continue;
            Container oldParent = it.oldParentId != null ? manager.getContainer(it.oldParentId) : null;
            manager.moveContainerToParent(c, oldParent, it.oldIndex);
        }
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

        public Item(UUID containerId, UUID oldParentId, int oldIndex) {
            this.containerId = containerId;
            this.oldParentId = oldParentId;
            this.oldIndex = oldIndex;
        }
    }
}
