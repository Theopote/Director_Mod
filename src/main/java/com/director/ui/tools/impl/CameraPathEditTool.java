package com.director.ui.tools.impl;

import com.director.i18n.ToolI18n;
import com.director.ui.camera.path.CameraPathEditController;
import com.director.ui.theme.Icons;
import com.director.ui.tools.EditorTool;
import com.director.ui.tools.ToolGroup;
import com.director.ui.tools.ToolLifecycle;
import com.director.ui.tools.ToolRegistry;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

public final class CameraPathEditTool implements EditorTool, ToolLifecycle {
    public static final String ID = "camera_path_edit";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return ToolI18n.name(ID);
    }

    @Override
    public String icon() {
        return Icons.PATH;
    }

    @Override
    public String getDescription() {
        return ToolI18n.description(ID);
    }

    @Override
    public ToolGroup getGroup() {
        return ToolGroup.TIMELINE;
    }

    @Override
    public boolean isEnabled() {
        return MinecraftClient.getInstance().world != null;
    }

    @Override
    public boolean isActive() {
        return Objects.equals(ToolRegistry.get().getActiveNonSelectionToolId(), ID);
    }

    @Override
    public void onActivate() {
        CameraPathEditController.getInstance().activate();
    }

    @Override
    public void onDeactivate() {
        CameraPathEditController.getInstance().deactivate();
    }
}
