package com.chronoblocks.ui.tools.impl;

import com.chronoblocks.ui.actor.path.ActorPathController;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.tools.ToolLifecycle;

public final class ActorPathTool extends AbstractWorldTool implements ToolLifecycle {

    public ActorPathTool() {
        super("actor_path", "演员路径", Icons.PATH, "在世界中绘制路径并生成移动片段。");
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
