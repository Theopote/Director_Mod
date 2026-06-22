package com.director.ui.tools.impl;

import com.director.ui.actor.path.ActorPathController;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolLifecycle;

public final class ActorPathTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorPathTool() {
        super("actor_path", Icons.PATH);
    }

    @Override
    public void onActivate() {
        ActorPathController.getInstance().activate();
    }

    @Override
    public void onDeactivate() {
        ActorPathController.getInstance().deactivate();
    }
}
