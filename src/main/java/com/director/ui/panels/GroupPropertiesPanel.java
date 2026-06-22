package com.director.ui.panels;

import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.container.ContainerPanelController;
import com.director.ui.util.UIUtils;
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
    private final ImString nameBuffer = UIUtils.createInputBuffer(UIUtils.DEFAULT_NAME_MAX_CHARACTERS);
    private final ImString descBuffer = UIUtils.createInputBuffer(UIUtils.DEFAULT_DESCRIPTION_MAX_CHARACTERS);
    private java.util.UUID currentEditingGroupId = null;

    public GroupPropertiesPanel(ContainerPanelController controller) {
        this.controller = controller;
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }

    public void render(Container group) {
        if (group == null || group.getType() != ContainerType.GROUP) {
            renderEmptyState();
            return;
        }

        ImGui.textColored(0.5f, 0.8f, 1.0f, 1.0f, tr(DirectorLang.Properties.EmbeddedGroup.TITLE));
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
        ImGui.textColored(0.8f, 0.9f, 1.0f, 1.0f, tr(DirectorLang.Properties.EmbeddedGroup.BASIC_INFO));

        syncBuffersIfNeeded(group);

        ImGui.inputText(tr(DirectorLang.Properties.EmbeddedGroup.GROUP_NAME), nameBuffer, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        boolean nameEditFinished = ImGui.isItemDeactivatedAfterEdit();
        if (nameEditFinished || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Enter)) {
            String newName = UIUtils.cleanString(nameBuffer.get());
            if (!newName.isEmpty() && !newName.equals(group.getName())) {
                controller.renameContainer(group.getId(), newName);
            }
        }

        if (ImGui.inputTextMultiline(tr(DirectorLang.Properties.EmbeddedGroup.NOTES), descBuffer, 0, 80)) {
            controller.setMetadata(group.getId(), "description", UIUtils.cleanString(descBuffer.get()));
        }

        ImGui.spacing();
        ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedGroup.STATS_HEADER));
        ImGui.text(tr(DirectorLang.Properties.EmbeddedGroup.CHILD_CONTAINERS, group.getChildren().size()));
        ImGui.text(tr(DirectorLang.Properties.EmbeddedGroup.TOTAL_BLOCKS, group.getTotalBlockCount()));
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
        if (!ImGui.collapsingHeader(tr(DirectorLang.Properties.EmbeddedGroup.INCLUDED_CONTAINERS), ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        List<Container> children = group.getChildren();
        if (children.isEmpty()) {
            ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedGroup.NO_CHILDREN));
            return;
        }
        int limit = Math.min(children.size(), MAX_CHILDREN_VISIBLE);
        for (int i = 0; i < limit; i++) {
            Container child = children.get(i);
            ImGui.bulletText(tr(DirectorLang.Properties.EmbeddedGroup.CHILD_ITEM,
                child.getType().getIcon(),
                child.getName(),
                child.getTotalBlockCount()));
        }
        if (children.size() > MAX_CHILDREN_VISIBLE) {
            ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedGroup.MORE_CHILDREN,
                children.size() - MAX_CHILDREN_VISIBLE));
        }
    }

    private void renderEmptyState() {
        ImGui.textDisabled(tr(DirectorLang.Properties.EmbeddedGroup.NO_GROUP_SELECTED));
        ImGui.spacing();
        ImGui.textWrapped(tr(DirectorLang.Properties.EmbeddedGroup.SELECT_GROUP_HINT));
    }
}
