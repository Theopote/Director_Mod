package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

import java.util.UUID;

/**
 * 重命名容器命令。
 */
public class RenameContainerCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final UUID containerId;
    private final String requestedName;
    private String oldName;
    private String appliedName;

    public RenameContainerCommand(ContainerManager manager, Container container, String newName) {
        this.manager = manager;
        this.containerId = container.getId();
        this.requestedName = newName;
        this.oldName = container.getName();
    }

    @Override
    public void execute() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            oldName = container.getName();
            if (appliedName == null) {
                appliedName = resolveUniqueName(container, requestedName);
            }
            manager.renameContainer(container, appliedName);
        }
    }

    @Override
    public void undo() {
        Container container = manager.getContainer(containerId);
        if (container != null) {
            manager.renameContainer(container, oldName);
        }
    }

    @Override
    public String getDescription() {
        return "重命名容器";
    }

    private String resolveUniqueName(Container container, String desired) {
        if (desired == null || desired.isBlank()) {
            return oldName;
        }
        String trimmed = desired.trim();
        String normalized = collapseUniformPairDuplication(trimmed);
        if (normalized.isBlank()) {
            return oldName;
        }

        Container existing = manager.getContainer(normalized);
        if (existing == null || existing.getId().equals(containerId)) {
            return normalized;
        }
        return manager.generateUniqueName(normalized);
    }

    /**
     * 兼容某些运行环境下文本输入事件被重复注入的问题：
     * 当且仅当整个名称呈现“每个字符都重复一次”的模式时进行折叠。
     */
    private static String collapseUniformPairDuplication(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        int[] codePoints = value.codePoints().toArray();
        if (codePoints.length < 4 || (codePoints.length & 1) != 0) {
            return value;
        }

        StringBuilder collapsed = new StringBuilder(codePoints.length / 2);
        for (int i = 0; i < codePoints.length; i += 2) {
            int first = codePoints[i];
            int second = codePoints[i + 1];
            if (first != second) {
                return value;
            }
            collapsed.appendCodePoint(first);
        }
        return collapsed.toString();
    }
}

