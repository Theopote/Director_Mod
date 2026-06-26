package com.director.ui.actor.path;

import com.director.actor.path.ActorPathDefinition;
import com.director.actor.path.ActorPathLibraryManager;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 演员路径库面板：列出、重命名、选择、删除已保存路径。
 */
public final class ActorPathLibraryPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathLibraryPanel.class);
    private static final int NAME_BUFFER = 128;

    private final ImString renameBuffer = new ImString("", NAME_BUFFER);
    private UUID renamingPathId = null;
    private UUID selectedPathId = null;

    public void render() {
        if (!ImGui.collapsingHeader(Icons.PATH + " 演员路径库")) {
            return;
        }

        ImGui.textWrapped("绘制的路径会保存在当前存档中。左键点击空白处完成绘制；再次右键开始新路径。");
        ImGui.spacing();

        var paths = ActorPathLibraryManager.getAllPaths();
        if (paths.isEmpty()) {
            ImGui.textDisabled("暂无已保存路径");
            return;
        }

        for (ActorPathDefinition path : paths) {
            boolean selected = path.getId().equals(selectedPathId);
            ImGui.pushID(path.getId().toString());

            if (renamingPathId != null && renamingPathId.equals(path.getId())) {
                renderRenameRow(path);
            } else {
                renderPathRow(path, selected);
            }

            ImGui.popID();
        }
    }

    public UUID getSelectedPathId() {
        return selectedPathId;
    }

    private void renderPathRow(ActorPathDefinition path, boolean selected) {
        String label = path.getName() + " (" + path.getWaypoints().size() + " 锚点)";
        if (ImGui.selectable(label, selected)) {
            selectedPathId = path.getId();
            ActorPathController.getInstance().loadPathForEditing(path.getId());
        }

        ImGui.sameLine();
        if (ImGui.smallButton(Icons.EDIT + "##rename")) {
            renamingPathId = path.getId();
            renameBuffer.set(path.getName());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重命名");
        }

        ImGui.sameLine();
        if (ImGui.smallButton(Icons.DELETE + "##delete")) {
            if (ActorPathLibraryManager.removePath(path.getId())) {
                if (path.getId().equals(selectedPathId)) {
                    selectedPathId = null;
                }
                if (path.getId().equals(renamingPathId)) {
                    renamingPathId = null;
                }
                ActorPathController.getInstance().onPathRemovedFromLibrary(path.getId());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("删除路径");
        }
    }

    private void renderRenameRow(ActorPathDefinition path) {
        ImGui.setNextItemWidth(-80);
        if (ImGui.inputText("##renameInput", renameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            commitRename(path.getId());
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.CHECK + "##ok")) {
            commitRename(path.getId());
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.CLOSE + "##cancel")) {
            renamingPathId = null;
        }
    }

    private void commitRename(UUID pathId) {
        String newName = renameBuffer.get().trim();
        if (newName.isEmpty()) {
            return;
        }
        if (!ActorPathLibraryManager.getInstance().isNameAvailable(newName, pathId)) {
            LOGGER.warn("[ActorPathLibraryPanel] 路径名称已存在: {}", newName);
            return;
        }
        if (ActorPathLibraryManager.renamePath(pathId, newName)) {
            renamingPathId = null;
        }
    }
}
