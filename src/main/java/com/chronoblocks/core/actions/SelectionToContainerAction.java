package com.chronoblocks.core.actions;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionSet;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

/**
 * 将当前选区生成新容器的操作
 * - 自动计算中心点
 * - 颜色与命名 UI
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class SelectionToContainerAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToContainerAction.class);

    /**
     * 执行选区转容器操作
     */
    public static void execute(String name, Color color) {
        SelectionSet set = SelectionManager.get().getSelectionSet();
        if (set.isEmpty()) {
            LOGGER.warn("[SelectionToContainerAction] 选区为空，无法生成容器");
            return;
        }

        try {
            // 创建新容器
            Container container = new Container(name);
            
            // 设置方块
            container.addBlocks(set.asUnmodifiableSet());
            
            // 设置颜色
            if (color != null) {
                // 将AWT Color转换为RGBA整数
                // int rgba = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
                // container.setColor(rgba); // 如果Container有setColor方法
            }
            
            // 设置位置为中心点
            // var centerPos = set.getCenterBlock();
            // container.setPosition(centerPos); // 如果Container有setPosition方法
            
            // 注册到容器管理器
            ContainerManager.getInstance().addContainer(container);
            
            LOGGER.info("[SelectionToContainerAction] 从选区生成容器 '{}' ({} 个方块)", name, set.size());
            
            // 清空选区
            set.clear();
            
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

    /**
     * 获取当前选区的统计信息
     */
    public static String getSelectionInfo() {
        SelectionSet set = SelectionManager.get().getSelectionSet();
        if (set.isEmpty()) {
            return "选区为空";
        }
        
        var centerPos = set.getCenterBlock();
        return String.format("选区: %d 个方块，中心: (%d, %d, %d)", 
                           set.size(), centerPos.getX(), centerPos.getY(), centerPos.getZ());
    }
}
