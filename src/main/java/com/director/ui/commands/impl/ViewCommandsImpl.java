package com.director.ui.commands.impl;

import com.director.ui.commands.ViewCommands;
import com.director.ui.coordinator.UIPanelManager;

public final class ViewCommandsImpl implements ViewCommands {

    private final UIPanelManager panelManager;

    public ViewCommandsImpl(UIPanelManager panelManager) {
        this.panelManager = panelManager;
    }

    @Override
    public void showPanel(String panelId) {
        panelManager.showPanel(panelId);
    }

    @Override
    public void hidePanel(String panelId) {
        panelManager.hidePanel(panelId);
    }

    @Override
    public void togglePanel(String panelId) {
        panelManager.togglePanel(panelId);
    }

    @Override
    public void showAllPanels() {
        panelManager.showAllPanels();
    }

    @Override
    public void hideAllPanels() {
        panelManager.hideAllPanels();
    }
}
