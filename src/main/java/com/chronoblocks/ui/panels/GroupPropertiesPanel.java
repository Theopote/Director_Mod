package com.chronoblocks.ui.panels;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.ui.container.ContainerPanelController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.List;

/**
 * 组属性面板：用于展示和编辑分组容器的基础信息。
 * 所有修改通过 ContainerPanelController 执行命令，支持撤销/重做。
 */
public class GroupPropertiesPanel {

    private static final int MAX_CHILDREN_VISIBLE = 10;

    private final ContainerPanelController controller;
    private final ImString nameBuffer = new ImString(256);
    private final ImString descBuffer = new ImString(512);

    public GroupPropertiesPanel(ContainerPanelController controller) {
        this.controller = controller;
    }

    public void render(Container group) {
        if (group == null || group.getType() != ContainerType.GROUP) {
            renderEmptyState();
            return;
        }

        ImGui.textColored(0.5f, 0.8f, 1.0f, 1.0f, "组属性");
        ImGui.sameLine();
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f,
            String.format("(ID: %s)", group.getId().toString().substring(0, 8)));
        ImGui.separator();

        ImGui.beginChild("GroupPropertiesContent", 0, 0, false);

        renderBasicInfo(group);
        ImGui.separator();
        renderChildrenOverview(group);

        ImGui.endChild();
    }

    private void renderBasicInfo(Container group) {
        ImGui.textColored(0.8f, 0.9f, 1.0f, 1.0f, "基本信息");

        nameBuffer.set(group.getName());
        if (ImGui.inputText("组名称", nameBuffer)) {
            String newName = nameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(group.getName())) {
                controller.renameContainer(group.getId(), newName);
            }
        }

        Object desc = group.getMetadata("description", "");
        descBuffer.set(desc != null ? desc.toString() : "");
        if (ImGui.inputTextMultiline("备注", descBuffer, 0, 80)) {
            controller.setMetadata(group.getId(), "description", descBuffer.get());
        }

        ImGui.spacing();
        ImGui.textDisabled("统计：");
        ImGui.text(String.format("  子容器: %d", group.getChildren().size()));
        ImGui.text(String.format("  总方块: %d", group.getTotalBlockCount()));
    }

    private void renderChildrenOverview(Container group) {
        if (!ImGui.collapsingHeader("包含的容器", ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        List<Container> children = group.getChildren();
        if (children.isEmpty()) {
            ImGui.textDisabled("暂无子容器");
            return;
        }
        int limit = Math.min(children.size(), MAX_CHILDREN_VISIBLE);
        for (int i = 0; i < limit; i++) {
            Container child = children.get(i);
            ImGui.bulletText(String.format("%s %s (%d 方块)",
                child.getType().getIcon(),
                child.getName(),
                child.getTotalBlockCount()));
        }
        if (children.size() > MAX_CHILDREN_VISIBLE) {
            ImGui.textDisabled("其余 " + (children.size() - MAX_CHILDREN_VISIBLE) + " 个");
        }
    }

    private void renderEmptyState() {
        ImGui.textDisabled("(未选择组)");
        ImGui.spacing();
        ImGui.textWrapped("请选择一个组以查看其属性。");
    }
}

