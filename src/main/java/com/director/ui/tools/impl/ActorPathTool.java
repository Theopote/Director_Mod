package com.director.ui.tools.impl;

import com.director.ui.actor.path.ActorPathController;
import com.director.ui.theme.Icons;
import com.director.ui.tools.ToolLifecycle;

public final class ActorPathTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorPathTool() {
        super("actor_path", "Actor Route", Icons.PATH, "Draw waypoints in the world and create an ActorRouteClip.");
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
