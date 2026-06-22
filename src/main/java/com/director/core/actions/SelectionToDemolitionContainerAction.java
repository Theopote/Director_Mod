package com.director.core.actions;

import com.director.core.DirectorServices;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 将当前选区生成“拆除容器”的操作：
 * - 捕获选区方块快照（用于可逆清除/回放）
 * - 默认不隐藏世界方块（与容器轨道不同）
 *
 * <p>用途：拖入 {@code 清除轨道(ERASE)}，播放时逐步把方块置 AIR。
 */
public final class SelectionToDemolitionContainerAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToDemolitionContainerAction.class);

    private SelectionToDemolitionContainerAction() {}

    public static void execute() {
        SelectionSet set = DirectorServices.get().selection().getSelectionSet();
        if (set.isEmpty()) {
            LOGGER.warn("[SelectionToDemolitionContainerAction] 选区为空，无法生成拆除容器");
            return;
        }

        try {
            var selectionState = DirectorServices.get().selection().getCurrentSelection();
            net.minecraft.world.World world = null;
            try {
                world = net.minecraft.client.MinecraftClient.getInstance().world;
            } catch (Throwable ignored) {}

            // ★ 关键：拆除容器默认不隐藏世界方块，否则会立刻清空选区
            boolean hiddenDefault = false;
            Container container = DirectorServices.get().containers().createFromSelection(selectionState, world, hiddenDefault);
            if (container != null) {
                container.setType(ContainerType.DEMOLITION);
                // 清空选区（创建成功后清除）
                DirectorServices.get().selection().clearSelection();
                LOGGER.info("[SelectionToDemolitionContainerAction] 从选区生成拆除容器 '{}' ({} 个方块)",
                    container.getName(), set.size());
            }
        } catch (Exception e) {
            LOGGER.error("[SelectionToDemolitionContainerAction] 生成拆除容器失败", e);
        }
    }
}

