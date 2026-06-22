package com.director.ui.commands.impl;

import com.director.ui.commands.LayoutCommands;
import com.director.ui.dialogs.DialogManager;
import com.director.ui.layout.DockSpaceLayoutBuilder;
import com.director.ui.layout.LayoutPersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LayoutCommandsImpl implements LayoutCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(LayoutCommandsImpl.class);

    private final LayoutPersistenceManager layoutManager;
    private final DialogManager dialogManager;
    private final DirectorUILayoutActions layoutActions;

    public LayoutCommandsImpl(
            LayoutPersistenceManager layoutManager,
            DialogManager dialogManager,
            DirectorUILayoutActions layoutActions) {
        this.layoutManager = layoutManager;
        this.dialogManager = dialogManager;
        this.layoutActions = layoutActions;
    }

    @Override
    public void saveCurrentLayout() {
        try {
            layoutManager.saveLayout();
            LOGGER.info("[Layout] Current layout saved");
        } catch (Exception e) {
            LOGGER.error("[Layout] Failed to save layout: {}", e.getMessage(), e);
        }
    }

    @Override
    public void showSaveLayoutDialog() {
        dialogManager.showSaveLayoutDialog();
    }

    @Override
    public void showLoadLayoutDialog() {
        dialogManager.showLoadLayoutDialog();
    }

    @Override
    public boolean saveLayoutPreset(String presetName) {
        return layoutManager.saveLayoutPreset(presetName);
    }

    @Override
    public List<String> listLayoutPresets() {
        return layoutManager.listLayoutPresets();
    }

    @Override
    public boolean loadLayoutPreset(String presetName) {
        try {
            boolean success = layoutManager.loadLayoutPreset(presetName);
            if (success) {
                layoutManager.forceReloadLayout();
                DockSpaceLayoutBuilder.resetLayoutState();
                LOGGER.info("[Layout] Layout preset '{}' loaded and applied", presetName);
            }
            return success;
        } catch (Exception e) {
            LOGGER.error("[Layout] Failed to load layout preset: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteLayoutPreset(String presetName) {
        return layoutManager.deleteLayoutPreset(presetName);
    }

    @Override
    public void restoreDefaultLayout() {
        try {
            Path layoutFile = layoutManager.getLayoutFile();
            if (layoutFile != null && Files.exists(layoutFile)) {
                Files.delete(layoutFile);
                LOGGER.info("[Layout] Deleted layout file: {}", layoutFile);
            }

            Path iniPath = layoutManager.getImGuiIniPath();
            if (iniPath != null && Files.exists(iniPath)) {
                Files.delete(iniPath);
                LOGGER.info("[Layout] Deleted ImGui INI file: {}", iniPath);
            }

            layoutManager.resetLayoutDataToDefault();
            layoutManager.forceReloadLayout();

            layoutActions.clearCurrentDockSpaceState();
            layoutActions.restoreDefaultPanelVisibility();
            layoutActions.requestDockSpaceRelayout();
            DockSpaceLayoutBuilder.resetLayoutState();

            LOGGER.info("[Layout] Layout restored to default");
        } catch (Exception e) {
            LOGGER.error("[Layout] Failed to restore default layout: {}", e.getMessage(), e);
        }
    }

    @Override
    public void reloadLayout() {
        try {
            layoutManager.saveLayout();
            layoutManager.saveImGuiIni();
            layoutManager.forceReloadLayout();
            DockSpaceLayoutBuilder.resetLayoutState();
            LOGGER.info("[Layout] Layout reloaded for current world");
        } catch (Exception e) {
            LOGGER.error("[Layout] Failed to reload layout: {}", e.getMessage(), e);
        }
    }

    @Override
    public void setUIScale(float scale) {
        LOGGER.info("[Layout] Set UI scale: {}", scale);
    }

    @Override
    public void setTheme(String theme) {
        if ("Light".equals(theme)) {
            LOGGER.warn("[Layout] Light theme not implemented yet");
        }
    }
}
