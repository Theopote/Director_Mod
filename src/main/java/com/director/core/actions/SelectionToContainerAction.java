package com.director.core.actions;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionSet;
import com.director.core.container.ContainerManager;
import com.director.core.container.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Objects;

/**
 * 将当前选区生成新容器的操作
 * - 自动计算中心点
 * - 颜色与命名 UI
 * 
 * @author Director Team
 * @version 1.0
 */
public final class SelectionToContainerAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToContainerAction.class);

    /**
     * 执行选区转容器操作
     */
    public static void execute(String name, Color color) {
        SelectionSet set = DirectorServices.get().selection().getSelectionSet();
        if (set.isEmpty()) {
            LOGGER.warn("[SelectionToContainerAction] 选区为空，无法生成容器");
            return;
        }

        try {
            // 使用统一入口：ContainerManager.createFromSelection，确保设置levelKey并捕获快照
            var selectionState = DirectorServices.get().selection().getCurrentSelection();
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            net.minecraft.world.World world = mc != null ? mc.world : null;

            boolean hiddenDefault = true;
            Container container = DirectorServices.get().containers().createFromSelection(selectionState, world, hiddenDefault);
            if (container != null) {
                if (color != null) {
                    // 如需设置颜色，可在此处调用 container.setColor(...)
                }
                LOGGER.info("[SelectionToContainerAction] 从选区生成容器 '{}' ({} 个方块)", name, set.size());
                
                // 清空选区（容器创建成功后清除）
                DirectorServices.get().selection().clearSelection();
                LOGGER.debug("[SelectionToContainerAction] 已清除选区");
            }

        } catch (Exception e) {
            LOGGER.error("[SelectionToContainerAction] 生成容器失败", e);
        }
    }

    /**
     * 执行选区转容器操作（使用默认名称和颜色）
     */
    public static void execute() {
        String defaultName = "Container_" + System.currentTimeMillis();
        Color defaultColor = new Color(0.2f, 0.8f, 1.0f, 1.0f);
        execute(defaultName, defaultColor);
    }

    /**
     * 执行选区转容器操作（使用默认颜色）
     */
    public static void execute(String name) {
        Color defaultColor = new Color(0.2f, 0.8f, 1.0f, 1.0f);
        execute(name, defaultColor);
    }
}
