package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

import java.util.*;

/**
 * 批量设置容器颜色的命令（支持撤销/重做）
 */
public class BatchSetContainerColorsCommand implements ContainerCommand {
    
    private final ContainerManager manager;
    private final List<UUID> containerIds;
    private final Integer titleColor;  // null 表示不设置标题颜色
    private final Integer iconColor;   // null 表示不设置图标颜色
    private final Map<UUID, Integer> oldTitleColors = new HashMap<>();
    private final Map<UUID, Integer> oldIconColors = new HashMap<>();
    
    public BatchSetContainerColorsCommand(ContainerManager manager, 
                                         Collection<Container> containers,
                                         Integer titleColor, 
                                         Integer iconColor) {
        this.manager = manager;
        this.containerIds = new ArrayList<>();
        this.titleColor = titleColor;
        this.iconColor = iconColor;
        
        for (Container container : containers) {
            if (container != null) {
                containerIds.add(container.getId());
            }
        }
    }
    
    @Override
    public void execute() {
        oldTitleColors.clear();
        oldIconColors.clear();
        
        for (UUID id : containerIds) {
            Container container = manager.getContainer(id);
            if (container == null) continue;
            
            if (titleColor != null) {
                Integer oldTitleColor = (Integer) container.getMetadata("titleColor", null);
                if (oldTitleColor == null) {
                    oldTitleColor = container.getColor(); // 如果没有设置，使用默认颜色
                }
                oldTitleColors.put(id, oldTitleColor);
                container.setMetadata("titleColor", titleColor);
            }
            
            if (iconColor != null) {
                Integer oldIconColor = (Integer) container.getMetadata("iconColor", null);
                if (oldIconColor == null) {
                    oldIconColor = 0xFFFFFFFF; // 默认白色
                }
                oldIconColors.put(id, oldIconColor);
                container.setMetadata("iconColor", iconColor);
            }
        }
    }
    
    @Override
    public void undo() {
        for (UUID id : containerIds) {
            Container container = manager.getContainer(id);
            if (container == null) continue;
            
            if (titleColor != null) {
                Integer oldColor = oldTitleColors.get(id);
                if (oldColor != null) {
                    if (oldColor.equals(container.getColor())) {
                        container.removeMetadata("titleColor");
                    } else {
                        container.setMetadata("titleColor", oldColor);
                    }
                }
            }
            
            if (iconColor != null) {
                Integer oldColor = oldIconColors.get(id);
                if (oldColor != null) {
                    if (oldColor.equals(0xFFFFFFFF)) {
                        container.removeMetadata("iconColor");
                    } else {
                        container.setMetadata("iconColor", oldColor);
                    }
                }
            }
        }
    }
    
    @Override
    public String getDescription() {
        int count = containerIds.size();
        String parts = "";
        if (titleColor != null) parts += "标题";
        if (iconColor != null) parts += (parts.isEmpty() ? "" : "和") + "图标";
        return String.format("批量设置 %d 个容器的%s颜色", count, parts);
    }
}

