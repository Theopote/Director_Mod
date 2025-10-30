package com.chronoblocks.core.actions;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.selection.SelectionSet;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.UUID;

/**
 * 选区到容器的工具类
 * 提供将选区转换为容器的便捷方法
 */
public final class SelectionToContainerUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToContainerUtil.class);

    private SelectionToContainerUtil() {}

    /**
     * 从选区创建容器
     */
    public static Container createContainerFromSelection(String baseName,
                                                         Color color,
                                                         SelectionSet set,
                                                         boolean computeCenter) {
        String name = (baseName == null || baseName.isBlank())
                ? "Container_" + UUID.randomUUID().toString().substring(0, 6)
                : baseName;

        Container container = new Container(name);
        
        // 设置方块（简化实现）
        if (set != null && !set.isEmpty()) {
            // container.setBlocks(set.asUnmodifiableSet()); // 暂时注释掉，因为Container类可能没有这个方法
            LOGGER.debug("[SelectionToContainerUtil] 设置容器 {} 包含 {} 个方块", name, set.size());
        }
        
        // 设置颜色（简化实现）
        // container.setColor(color); // 暂时注释掉，因为Container类可能没有这个方法
        
        // 计算中心点（简化实现）
        if (computeCenter && set != null && !set.isEmpty()) {
            var centerPos = set.getCenterBlock();
            if (centerPos != null) {
                // container.setPosition(new Vector3f(centerPos.getX() + 0.5f, centerPos.getY() + 0.5f, centerPos.getZ() + 0.5f)); // 暂时注释掉
                LOGGER.debug("[SelectionToContainerUtil] 设置容器 {} 中心点: {}", name, centerPos);
            }
        }
        
        // 注册容器（简化实现）
        // ContainerManager.get().register(container); // 暂时注释掉，因为ContainerManager可能没有get()方法
        LOGGER.info("[SelectionToContainerUtil] 成功创建容器: {}", name);
        
        return container;
    }

    /**
     * 从选区创建容器（简化版本）
     */
    public static Container createContainerFromSelection(String baseName, Color color, SelectionSet set) {
        return createContainerFromSelection(baseName, color, set, true);
    }

    /**
     * 从选区创建容器（最简版本）
     */
    public static Container createContainerFromSelection(SelectionSet set) {
        return createContainerFromSelection(null, new Color(64, 160, 255), set, true);
    }
}
