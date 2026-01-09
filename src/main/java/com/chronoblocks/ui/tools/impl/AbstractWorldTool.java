package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.ui.tools.EditorTool;
import com.chronoblocks.ui.tools.ToolGroup;
import com.chronoblocks.ui.tools.ToolRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

abstract class AbstractWorldTool implements EditorTool {
    private final String id;
    private final String displayName;
    private final String icon;
    private final String description;

    protected AbstractWorldTool(String id, String displayName, String icon, String description) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.description = description != null ? description : "";
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getIcon() {
        return icon;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ToolGroup getGroup() {
        return ToolGroup.WORLD;
    }

    @Override
    public boolean isEnabled() {
        return MinecraftClient.getInstance().world != null;
    }

    @Override
    public boolean isActive() {
        return Objects.equals(ToolRegistry.get().getActiveNonSelectionToolId(), id);
    }
}
