package com.director.ui.tools.impl;

import com.director.i18n.ToolI18n;
import com.director.ui.tools.EditorTool;
import com.director.ui.tools.ToolGroup;
import com.director.ui.tools.ToolRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

abstract class AbstractActorTool implements EditorTool {
    private final String id;
    private final String icon;

    protected AbstractActorTool(String id, String icon) {
        this.id = Objects.requireNonNull(id, "id");
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return ToolI18n.name(id);
    }

    @Override
    public String icon() {
        return icon;
    }

    @Override
    public String getDescription() {
        return ToolI18n.description(id);
    }

    @Override
    public ToolGroup getGroup() {
        return ToolGroup.ACTOR;
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
