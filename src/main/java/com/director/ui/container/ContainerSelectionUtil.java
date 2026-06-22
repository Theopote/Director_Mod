package com.director.ui.container;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具类：用于检测 Minecraft 选区状态等。
 */
final class ContainerSelectionUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerSelectionUtil.class);

    private ContainerSelectionUtil() {
    }

    static boolean hasMinecraftSelection() {
        try {
            SelectionManager selectionManager = DirectorServices.get().selection();
            SelectionState selection = selectionManager != null ? selectionManager.getCurrentSelection() : null;
            return selection != null && !selection.isEmpty();
        } catch (RuntimeException t) {
            LOGGER.debug("[ContainerSelectionUtil] 检查Minecraft选区失败: {}", t.getMessage());
            return false;
        }
    }
}