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

    /**
     * 检查是否正在重命名指定容器
     */
    boolean isRenaming(Container container) {
        return container != null
            && state.getRenamingContainerId() != null
            && state.getRenamingContainerId().equals(container.getId());
    }

    /**
     * 检查是否正在编辑任何容器（显式会话状态）
     */
    boolean isEditing() {
        return state.getRenamingContainerId() != null;
    }

    /**
     * 获取当前正在编辑的容器ID（显式会话状态）
     */
    UUID getEditingContainerId() {
        return state.getRenamingContainerId();
    }

    /**
     * 开始重命名指定容器
     */
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

    /**
     * 取消当前编辑会话（不提交更改）
     * 应在以下情况调用：
     * - 选择被程序性改变
     * - 树节点被折叠
     * - 容器被移动/删除
     */
    void cancelEditing() {
        if (state.getRenamingContainerId() != null) {
            LOGGER.debug("[InlineRenamer] 取消编辑会话: {}", state.getRenamingContainerId());
            clearRenameState();
        }
    }

    /**
     * 提交当前编辑会话（应用更改）
     * 通常在 Enter 键或失去焦点时调用
     */
    void commitEditing() {
        if (state.getRenamingContainerId() == null || renameBufferContainerId == null) {
            return;
        }
        
        // 尝试获取容器（可能已被删除）
        Container container = controller.getContainer(renameBufferContainerId);
        if (container == null) {
            LOGGER.warn("[InlineRenamer] 提交编辑时容器已不存在: {}", renameBufferContainerId);
            clearRenameState();
            return;
        }
        
        String newName = renameBuffer.get().trim();
        if (!newName.isEmpty() && !newName.equals(container.getName())) {
            controller.renameContainer(container.getId(), newName);
        }
        clearRenameState();
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
            commitEditing();
            return;
        }

        if (enterPressed && !renamePendingClose) {
            renamePendingClose = true;
            renamePendingValue = renameBuffer.get();
        }

        if (renamePendingClose && !itemActive) {
            // 延迟提交：确保 Enter 键处理完成
            if (renamePendingValue != null) {
                renameBuffer.set(renamePendingValue);
            }
            commitEditing();
            return;
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancelEditing();
            return;
        }

        if (!renamePendingClose && itemDeactivated && !itemActive) {
            // 失去焦点时提交编辑（而不是取消）
            commitEditing();
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


    private void clearRenameState() {
        state.setRenamingContainerId(null);
        renameBufferContainerId = null;
        renamePendingClose = false;
        renamePendingValue = null;
    }
}