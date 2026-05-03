package com.director.ui.container;

import com.director.core.container.Container;
import com.director.ui.util.UIUtils;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 专职处理树节点的内联重命名输入逻辑。
 */
class InlineRenamer {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineRenamer.class);
    private static final int MAX_RENAME_CHARACTERS = UIUtils.DEFAULT_NAME_MAX_CHARACTERS;

    private final ContainerPanelController controller;
    private final ContainerPanelState state;
    private final ImString renameBuffer = UIUtils.createInputBuffer(MAX_RENAME_CHARACTERS);

    private UUID renameBufferContainerId;
    private String pendingCommitValue;
    private boolean focusRequested;
    private boolean restorePropertiesAfterRename;

    InlineRenamer(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    boolean isEditing() {
        return state.getRenamingContainerId() != null;
    }

    UUID getEditingContainerId() {
        return state.getRenamingContainerId();
    }

    boolean isRenaming(Container container) {
        return container != null
            && state.getRenamingContainerId() != null
            && state.getRenamingContainerId().equals(container.getId());
    }

    void startRenaming(Container container) {
        if (container == null) {
            LOGGER.warn("[InlineRenamer] startRenaming called with null container");
            return;
        }

        UUID id = container.getId();
        state.setRenamingContainerId(id);
        renameBuffer.set(container.getName());
        renameBufferContainerId = id;

        if (!state.isPropertiesCollapsed()) {
            state.setLeftRatioBeforeCollapse(state.getLeftPanelWidthRatio());
            state.setPropertiesCollapsed(true);
            restorePropertiesAfterRename = true;
        } else {
            restorePropertiesAfterRename = false;
        }

        pendingCommitValue = null;
        focusRequested = true;

        LOGGER.debug("[InlineRenamer] startRenaming: {} ({})", container.getName(), id);
    }

    void requestCommit() {
        if (!isEditing()) {
            return;
        }
        if (pendingCommitValue == null) {
            pendingCommitValue = UIUtils.readInputBuffer(renameBuffer);
            LOGGER.debug("[InlineRenamer] requestCommit: {}", pendingCommitValue);
        }
    }

    void cancelEditing() {
        cancelEditing(null);
    }

    void cancelEditing(String reason) {
        if (!isEditing()) {
            return;
        }
        LOGGER.debug("[InlineRenamer] cancelEditing: reason={}, editingId={}",
            reason != null ? reason : "unknown", state.getRenamingContainerId());
        clearRenameState();
    }

    void cancelIfEditingInSubtree(Container subtreeRoot) {
        if (!isEditing() || subtreeRoot == null) {
            return;
        }

        UUID editingId = state.getRenamingContainerId();
        if (editingId == null) {
            return;
        }

        Container editingContainer = controller.getContainer(editingId);
        if (editingContainer != null && editingContainer.isDescendantOf(subtreeRoot)) {
            cancelEditing("subtreeCollapsed");
        }
    }

    void cancelIfEditingAffected(Container affectedRoot) {
        cancelIfEditingInSubtree(affectedRoot);
    }

    void commitEditing() {
        if (state.getRenamingContainerId() == null || renameBufferContainerId == null) {
            return;
        }

        Container container = controller.getContainer(renameBufferContainerId);
        if (container == null) {
            LOGGER.warn("[InlineRenamer] container no longer exists during commit: {}", renameBufferContainerId);
            clearRenameState();
            return;
        }

        String rawName = UIUtils.readInputBuffer(renameBuffer);
        String newName = UIUtils.cleanString(rawName);
        if (!newName.isEmpty() && !newName.equals(container.getName())) {
            LOGGER.info("[InlineRenamer] commit rename: '{}' -> '{}' (chars={}, bytes={}, bufferSize={}, imStringChars={}, rawBytes={})",
                container.getName(),
                newName,
                newName.length(),
                newName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                renameBuffer.getBufferSize(),
                renameBuffer.get().length(),
                rawName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            controller.renameContainer(container.getId(), newName);
        } else {
            LOGGER.debug("[InlineRenamer] commit skipped (empty or same, chars={}, bytes={}, bufferSize={}, imStringChars={}, rawBytes={})",
                newName.length(),
                newName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                renameBuffer.getBufferSize(),
                renameBuffer.get().length(),
                rawName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }
        clearRenameState();
    }

    void render(Container container) {
        if (container == null) {
            clearRenameState();
            return;
        }

        if (!isRenaming(container)) {
            return;
        }

        ensureBufferSynced(container);
        focusOnceIfNeeded();

        String inputId = "##InlineRename_" + container.getId();

        ImGui.setCursorPosX(ImGui.getCursorStartPosX());
        ImGui.setNextItemWidth(-1.0f);
        UIUtils.exposeInputBufferCapacity(renameBuffer);

        boolean enterPressed = ImGui.inputText(
            inputId,
            renameBuffer,
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll
        );

        boolean afterEdit = ImGui.isItemDeactivatedAfterEdit();
        boolean itemDeactivated = ImGui.isItemDeactivated();
        boolean itemActive = ImGui.isItemActive();

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancelEditing("escape");
            return;
        }

        if (enterPressed) {
            requestCommit();
        }

        if (afterEdit) {
            commitEditing();
            clearRenameState();
            return;
        }

        if (pendingCommitValue != null && !itemActive) {
            renameBuffer.set(pendingCommitValue);
            commitEditing();
            clearRenameState();
            return;
        }

        if (pendingCommitValue == null && itemDeactivated && !itemActive) {
            cancelEditing("focusLost");
        }
    }

    private void ensureBufferSynced(Container container) {
        if (renameBufferContainerId == null || !renameBufferContainerId.equals(container.getId())) {
            renameBuffer.set(container.getName());
            renameBufferContainerId = container.getId();
            pendingCommitValue = null;
            focusRequested = true;
        }
    }

    private void focusOnceIfNeeded() {
        if (focusRequested) {
            ImGui.setKeyboardFocusHere();
            focusRequested = false;
        }
    }

    private void clearRenameState() {
        state.setRenamingContainerId(null);
        renameBufferContainerId = null;
        pendingCommitValue = null;
        focusRequested = false;
        if (restorePropertiesAfterRename && state.isPropertiesCollapsed()) {
            state.setPropertiesCollapsed(false);
        }
        restorePropertiesAfterRename = false;
    }
}
