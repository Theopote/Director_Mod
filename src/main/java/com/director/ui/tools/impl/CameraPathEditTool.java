package com.director.ui.tools.impl;

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
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Camera Path";
    }

    @Override
    public String getIcon() {
        return Icons.PATH;
    }

    @Override
    public String getDescription() {
        return "Select and move camera path anchors with the Gizmo";
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
