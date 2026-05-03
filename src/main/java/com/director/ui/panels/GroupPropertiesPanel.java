package com.director.ui.panels;

import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.ui.container.ContainerPanelController;
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
    // ImString 容量按 UTF-8 字节计，中文输入需要更大缓冲区。
    private final ImString nameBuffer = new ImString(1024);
    private final ImString descBuffer = new ImString(2048);
    private java.util.UUID currentEditingGroupId = null;

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

        syncBuffersIfNeeded(group);

        ImGui.inputText("组名称", nameBuffer, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        boolean nameEditFinished = ImGui.isItemDeactivatedAfterEdit();
        if (nameEditFinished || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Enter)) {
            String newName = nameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(group.getName())) {
                controller.renameContainer(group.getId(), newName);
            }
        }

        if (ImGui.inputTextMultiline("备注", descBuffer, 0, 80)) {
            controller.setMetadata(group.getId(), "description", descBuffer.get());
        }

        ImGui.spacing();
        ImGui.textDisabled("统计：");
        ImGui.text(String.format("  子容器: %d", group.getChildren().size()));
        ImGui.text(String.format("  总方块: %d", group.getTotalBlockCount()));
    }

    private void syncBuffersIfNeeded(Container group) {
        if (group == null) {
            currentEditingGroupId = null;
            return;
        }
        if (currentEditingGroupId != null && currentEditingGroupId.equals(group.getId())) {
            return;
        }

        currentEditingGroupId = group.getId();
        nameBuffer.set(group.getName());

        Object desc = group.getMetadata("description", "");
        descBuffer.set(desc != null ? desc.toString() : "");
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

