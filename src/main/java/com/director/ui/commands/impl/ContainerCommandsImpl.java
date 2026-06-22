package com.director.ui.commands.impl;

import com.director.ui.commands.ContainerCommands;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.panels.ContainerPanel;

public final class ContainerCommandsImpl implements ContainerCommands {

    private final IPanelRegistry registry;

    public ContainerCommandsImpl(IPanelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void requestDeleteSelected() {
        ContainerPanel panel = registry.getPanel(ContainerPanel.class);
        if (panel != null) {
            panel.requestDeleteSelected();
        }
    }
}
