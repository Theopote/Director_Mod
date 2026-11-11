package com.director.ui.container;

import com.director.core.container.Container;
import imgui.ImGui;
import imgui.type.ImString;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;

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
    private final ImString search = new ImString(256);

    public ContainerTreeView(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        InlineRenamer inlineRenamer = new InlineRenamer(controller, state);
        this.interactionHandler = new ContainerTreeInteractionHandler(controller, state, inlineRenamer);
        this.renderer = new ContainerTreeRenderer(controller, state, interactionHandler, inlineRenamer);
    }

    public void render() {
        ImGui.setNextItemWidth(-1f);
        ImGui.inputTextWithHint("##containerSearch",
                DirectorI18n.translate(DirectorLang.ContainerPanel.SEARCH), search);
        if (!ImGui.beginChild("ContainerTree", 0, 0, true)) {
            ImGui.endChild();
            return;
        }

        interactionHandler.beginFrame();
        List<Container> roots = new ArrayList<>(controller.getRootContainers());
        renderer.renderTree(roots, search.get());
        interactionHandler.handleRootDropArea();
        interactionHandler.handleCanvasContextMenu();
        ImGui.endChild();
    }
}
