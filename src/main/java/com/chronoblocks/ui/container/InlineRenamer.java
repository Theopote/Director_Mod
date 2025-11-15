package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 专职处理树节点的内联重命名输入逻辑，确保 ContainerTreeView 保持精简。
 */
class InlineRenamer {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineRenamer.class);
    private static final int MAX_RENAME_BUFFER_SIZE = 256;

    private final ContainerPanelController controller;
    private final ContainerPanelState state;
    private final ImString renameBuffer = new ImString(MAX_RENAME_BUFFER_SIZE);

    private UUID renameBufferContainerId;
    private boolean renamePendingClose;
    private String renamePendingValue;

    InlineRenamer(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    boolean isRenaming(Container container) {
        return container != null
            && state.getRenamingContainerId() != null
            && state.getRenamingContainerId().equals(container.getId());
    }

    void startRenaming(Container container) {
        if (container == null) {
            LOGGER.warn("[InlineRenamer] startRenaming 被空容器调用");
            return;
        }
        state.setRenamingContainerId(container.getId());
        renameBuffer.set(container.getName());
        renameBufferContainerId = container.getId();
        renamePendingClose = false;
        renamePendingValue = null;
    }

    void render(Container container) {
        if (container == null) {
            clearRenameState();
            return;
        }
        ensureBufferSynced(container);
        focusIfNeeded();

        boolean enterPressed = ImGui.inputText("##InlineRename", renameBuffer,
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);

        boolean afterEdit = ImGui.isItemDeactivatedAfterEdit();
        boolean itemDeactivated = ImGui.isItemDeactivated();
        boolean itemActive = ImGui.isItemActive();

        if (afterEdit) {
            finalizeInlineRename(container, renameBuffer.get());
            clearRenameState();
            return;
        }

        if (enterPressed && !renamePendingClose) {
            renamePendingClose = true;
            renamePendingValue = renameBuffer.get();
        }

        if (renamePendingClose && !itemActive) {
            finalizeInlineRename(container, renamePendingValue);
            clearRenameState();
            return;
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            clearRenameState();
            return;
        }

        if (!renamePendingClose && itemDeactivated && !itemActive) {
            clearRenameState();
        }
    }

    private void ensureBufferSynced(Container container) {
        if (renameBufferContainerId == null || !renameBufferContainerId.equals(container.getId())) {
            renameBuffer.set(container.getName());
            renameBufferContainerId = container.getId();
            renamePendingClose = false;
            renamePendingValue = null;
        }
    }

    private void focusIfNeeded() {
        if (!renamePendingClose) {
            ImGui.setKeyboardFocusHere();
        }
    }

    private void finalizeInlineRename(Container container, String rawName) {
        if (container == null || rawName == null) {
            return;
        }
        String newName = rawName.trim();
        if (newName.isEmpty() || newName.equals(container.getName())) {
            return;
        }
        controller.renameContainer(container.getId(), newName);
    }

    private void clearRenameState() {
        state.setRenamingContainerId(null);
        renameBufferContainerId = null;
        renamePendingClose = false;
        renamePendingValue = null;
    }
}