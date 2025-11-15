package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;

/**
 * ContainerTreeView 负责渲染容器树以及相关交互。
 * 当前实现仍然包含较多逻辑，后续可以继续拆分为 renderer/interaction handler。
 */
public class ContainerTreeView {

    private final ContainerPanelController controller;
    private final ContainerTreeInteractionHandler interactionHandler;
    private final ContainerTreeRenderer renderer;

    public ContainerTreeView(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        InlineRenamer inlineRenamer = new InlineRenamer(controller, state);
        this.interactionHandler = new ContainerTreeInteractionHandler(controller, state, inlineRenamer);
        this.renderer = new ContainerTreeRenderer(controller, state, interactionHandler, inlineRenamer);
    }

    public void render() {
        if (!ImGui.beginChild("ContainerTree", 0, 0, true)) {
            ImGui.endChild();
            return;
        }

        List<Container> roots = new ArrayList<>(controller.getRootContainers());
        renderer.renderTree(roots);
        interactionHandler.handleRootDropArea();
        interactionHandler.handleCanvasContextMenu();
        ImGui.endChild();
    }
}