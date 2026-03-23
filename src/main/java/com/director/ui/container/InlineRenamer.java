package com.director.ui.container;

import com.director.core.container.Container;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 专职处理树节点的内联重命名输入逻辑（带"编辑会话"）。
 * 
 * 设计目标：
 * 1) 明确会话生命周期：start → editing → commit/cancel → end
 * 2) 允许外部事件安全结束会话（折叠/移动/删除/selection变化）
 * 3) 避免焦点抖动：只在会话开始时请求一次键盘焦点
 * 
 * 关键约束：
 * - 一次只允许编辑一个 Container
 * - 只有在 render(editingContainer) 时才会真正提交（保证提交对象正确）
 */
class InlineRenamer {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlineRenamer.class);
    private static final int MAX_RENAME_BUFFER_SIZE = 256;

    private final ContainerPanelController controller;
    private final ContainerPanelState state;
    private final ImString renameBuffer = new ImString(MAX_RENAME_BUFFER_SIZE);

    // 会话状态
    private UUID renameBufferContainerId;
    private boolean commitRequested;          // 外部请求提交标记
    private String commitRequestedValue;       // 请求提交时的值
    private boolean focusRequested;           // 只在会话开始时请求一次 focus

    InlineRenamer(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    // -------------------- Public Session API --------------------

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
     * 检查是否正在重命名指定容器
     */
    boolean isRenaming(Container container) {
        return container != null
            && state.getRenamingContainerId() != null
            && state.getRenamingContainerId().equals(container.getId());
    }

    /**
     * 开始重命名指定容器（从容器当前 name 初始化 buffer）
     */
    void startRenaming(Container container) {
        if (container == null) {
            LOGGER.warn("[InlineRenamer] startRenaming 被空容器调用");
            return;
        }
        
        UUID id = container.getId();
        state.setRenamingContainerId(id);
        renameBuffer.set(container.getName());
        renameBufferContainerId = id;
        
        // 重置会话状态
        commitRequested = false;
        commitRequestedValue = null;
        focusRequested = true;  // 标记需要请求焦点
        
        LOGGER.debug("[InlineRenamer] startRenaming: {} ({})", container.getName(), id);
    }

    /**
     * 外部请求提交（例如点击其他节点、打开菜单前、按快捷键切换等）
     * 不会立即提交，而是等待 input 失活后再提交
     */
    void requestCommit() {
        if (!isEditing()) {
            return;
        }
        if (!commitRequested) {
            commitRequested = true;
            commitRequestedValue = renameBuffer.get();
            LOGGER.debug("[InlineRenamer] requestCommit: {}", commitRequestedValue);
        }
    }

    /**
     * 取消当前编辑会话（不提交更改）
     * 应在以下情况调用：
     * - 选择被程序性改变
     * - 树节点被折叠
     * - 容器被移动/删除
     */
    void cancelEditing() {
        cancelEditing(null);
    }

    /**
     * 取消当前编辑会话（带原因，用于调试）
     */
    void cancelEditing(String reason) {
        if (!isEditing()) {
            return;
        }
        LOGGER.debug("[InlineRenamer] cancelEditing: reason={}, editingId={}", 
            reason != null ? reason : "unknown", state.getRenamingContainerId());
        clearRenameState();
    }

    /**
     * 当某个节点被折叠时调用：如果当前编辑对象在这个子树内，安全取消。
     * 取消而不是提交：避免"折叠导致的误提交/误焦点"。
     */
    void cancelIfEditingInSubtree(Container subtreeRoot) {
        if (!isEditing() || subtreeRoot == null) {
            return;
        }
        
        UUID editingId = state.getRenamingContainerId();
        if (editingId == null) {
            return;
        }
        
        // 检查编辑对象是否在子树内
        Container editingContainer = controller.getContainer(editingId);
        if (editingContainer != null && editingContainer.isDescendantOf(subtreeRoot)) {
            cancelEditing("subtreeCollapsed");
        }
    }

    /**
     * 当某个容器将被移动/删除时调用：如果正在编辑它（或它的子树），取消会话。
     */
    void cancelIfEditingAffected(Container affectedRoot) {
        cancelIfEditingInSubtree(affectedRoot);
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
            LOGGER.info("[InlineRenamer] commit rename: '{}' -> '{}'", container.getName(), newName);
            controller.renameContainer(container.getId(), newName);
        } else {
            LOGGER.debug("[InlineRenamer] commit skipped (empty or same)");
        }
        clearRenameState();
    }

    // -------------------- Render --------------------

    void render(Container container) {
        if (container == null) {
            // 编辑对象不存在了（被删/重建），安全结束
            clearRenameState();
            return;
        }

        // 防止错 render：只允许渲染当前编辑对象
        if (!isRenaming(container)) {
            return;
        }

        ensureBufferSynced(container);
        focusOnceIfNeeded();

        // 用稳定唯一ID，避免树里ID冲突
        String inputId = "##InlineRename_" + container.getId();

        boolean enterPressed = ImGui.inputText(
            inputId,
            renameBuffer,
            ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll
        );

        boolean afterEdit = ImGui.isItemDeactivatedAfterEdit();
        boolean itemDeactivated = ImGui.isItemDeactivated();
        boolean itemActive = ImGui.isItemActive();

        // 1) ESC：无条件取消
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            cancelEditing("escape");
            return;
        }

        // 2) Enter：请求提交（延迟到 item 不 active）
        if (enterPressed) {
            requestCommit();
        }

        // 3) 由 ImGui 判断"编辑结束后失活" -> 提交
        if (afterEdit) {
            commitEditing();
            clearRenameState();
            return;
        }

        // 4) 延迟提交：当 input 不再 active 时提交
        if (commitRequested && !itemActive) {
            if (commitRequestedValue != null) {
                renameBuffer.set(commitRequestedValue);
            }
            commitEditing();
            clearRenameState();
            return;
        }

        // 5) 普通失活（比如点击别处）：
        //    - 如果外部已 requestCommit -> 上面会提交
        //    - 否则默认取消（更安全，避免"点空白误提交空名字/半成品"）
        if (!commitRequested && itemDeactivated && !itemActive) {
            cancelEditing("focusLost");
        }
    }

    // -------------------- Internal --------------------

    private void ensureBufferSynced(Container container) {
        if (renameBufferContainerId == null || !renameBufferContainerId.equals(container.getId())) {
            renameBuffer.set(container.getName());
            renameBufferContainerId = container.getId();
            commitRequested = false;
            commitRequestedValue = null;
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
        commitRequested = false;
        commitRequestedValue = null;
        focusRequested = false;
    }
}