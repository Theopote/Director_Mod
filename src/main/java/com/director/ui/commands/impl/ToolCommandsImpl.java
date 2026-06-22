package com.director.ui.commands.impl;

import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionOperation;
import com.director.core.selection.SelectionOperationSupport;
import com.director.ui.commands.ToolCommands;
import com.director.ui.properties.PropertyContextService;
import com.director.ui.tools.ToolRegistry;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ToolCommandsImpl implements ToolCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolCommandsImpl.class);

    @Override
    public void activateTool(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return;
        }
        ToolRegistry.get().activate(toolId);
        PropertyContextService.get().onToolActivated(toolId);
    }

    @Override
    public void switchToNextTool() {
        var world = MinecraftClient.getInstance().world;
        if (world != null) {
            SelectionManager.get().switchToNextTool(world);
            var id = SelectionManager.get().getCurrentToolId();
            if (id != null) {
                PropertyContextService.get().onToolActivated(id);
            }
            LOGGER.debug("[ToolCommands] 切换到下一个工具");
        }
    }

    @Override
    public void switchToPreviousTool() {
        var world = MinecraftClient.getInstance().world;
        if (world != null) {
            SelectionManager.get().switchToPreviousTool(world);
            var id = SelectionManager.get().getCurrentToolId();
            if (id != null) {
                PropertyContextService.get().onToolActivated(id);
            }
            LOGGER.debug("[ToolCommands] 切换到上一个工具");
        }
    }

    @Override
    public void setSelectionMode(SelectionOperation operation) {
        var currentTool = SelectionManager.get().getCurrentTool();
        if (currentTool instanceof SelectionOperationSupport operationSupport) {
            operationSupport.setOperation(operation);
            LOGGER.debug("[ToolCommands] 设置选择操作模式: {}", operation.getDisplayName());
        } else {
            LOGGER.debug("[ToolCommands] 当前工具不支持操作模式切换: {}",
                    currentTool != null ? currentTool.getId() : "null");
        }
    }

    @Override
    public void clearSelection() {
        SelectionManager.get().clearSelection();
        LOGGER.debug("[ToolCommands] 清除选择");
    }

    @Override
    public String getActiveToolId() {
        return ToolRegistry.get().getActiveToolId();
    }

    @Override
    public boolean isToolActive(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return false;
        }
        return toolId.equals(getActiveToolId());
    }
}
