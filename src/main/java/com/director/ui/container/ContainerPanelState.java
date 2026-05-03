package com.director.ui.container;

import com.director.core.container.Container;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ContainerPanelState 持有所有容器面板相关的 UI 状态。
 * 这些状态是纯粹的视图层数据，不应该包含任何副作用。
 * <p>
 * 状态分类：
 * - PersistentState：应被布局系统保存/恢复的状态（用户偏好、布局比例等）
 * - EphemeralState：瞬时交互状态（不应被保存，如待确认的删除请求）
 */
public final class ContainerPanelState {

    // ==================== PersistentState（应被布局系统保存/恢复） ====================
    
    /**
     * 左侧面板宽度比例（0.0 - 1.0）
     */
    private float leftPanelWidthRatio = 0.5f;

    /**
     * 右侧属性面板是否折叠（默认折叠，更紧凑）
     */
    private boolean propertiesCollapsed = true;

    /**
     * 折叠前左侧宽度比例（用于恢复）
     */
    private float leftRatioBeforeCollapse = 0.5f;

    /**
     * 树节点展开模式：
     * - true：展开全部节点
     * - false：仅展开用户手动展开的节点
     */
    private boolean expandAll = true;

    /**
     * 用户手动展开的节点集合（当 expandAll = false 时生效）
     */
    private final Set<UUID> expandedNodes = new HashSet<>();

    /**
     * 顶部按钮显示模式：
     * - false：仅图标（更紧凑）
     * - true：图标 + 文字（更易读）
     */
    private boolean showToolbarText = false;

    // ==================== EphemeralState（瞬时交互状态，不应被保存） ====================

    /**
     * 当前正在重命名的容器ID（内联重命名会话）
     */
    private UUID renamingContainerId;

    /**
     * 当前显示上下文菜单的容器ID
     */
    private UUID contextMenuContainerId;

    /**
     * 当前悬停的容器（用于高亮显示）
     */
    private Container hoveredContainer;

    /**
     * 待释放的容器ID（用于确认对话框）
     */
    private UUID pendingReleaseContainerId;

    /**
     * 是否已请求删除操作（用于确认对话框）
     */
    private boolean pendingDeletionRequested;

    public float getLeftPanelWidthRatio() {
        return leftPanelWidthRatio;
    }

    public void setLeftPanelWidthRatio(float leftPanelWidthRatio) {
        this.leftPanelWidthRatio = leftPanelWidthRatio;
    }

    public boolean isExpandAll() {
        return expandAll;
    }

    public void setExpandAll(boolean expandAll) {
        this.expandAll = expandAll;
    }

    public void expandAllNodes() {
        this.expandAll = true;
        this.expandedNodes.clear();
    }

    public void collapseAllNodes() {
        this.expandAll = false;
        this.expandedNodes.clear();
    }

    public void disableExpandAllWithState(java.util.Collection<java.util.UUID> nodesToKeepExpanded) {
        this.expandAll = false;
        this.expandedNodes.clear();
        if (nodesToKeepExpanded != null) {
            this.expandedNodes.addAll(nodesToKeepExpanded);
        }
    }

    public void disableExpandAllByCollapsing(UUID collapsedNodeId) {
        this.expandAll = false;
        if (collapsedNodeId != null) {
            this.expandedNodes.remove(collapsedNodeId);
        }
    }

    public UUID getRenamingContainerId() {
        return renamingContainerId;
    }

    public void setRenamingContainerId(UUID renamingContainerId) {
        this.renamingContainerId = renamingContainerId;
    }

    public UUID getContextMenuContainerId() {
        return contextMenuContainerId;
    }

    public void setContextMenuContainerId(UUID contextMenuContainerId) {
        this.contextMenuContainerId = contextMenuContainerId;
    }

    public Set<UUID> getExpandedNodes() {
        return expandedNodes;
    }

    public Container getHoveredContainer() {
        return hoveredContainer;
    }

    public void setHoveredContainer(Container hoveredContainer) {
        this.hoveredContainer = hoveredContainer;
    }

    public UUID getPendingReleaseContainerId() {
        return pendingReleaseContainerId;
    }

    public void setPendingReleaseContainerId(UUID pendingReleaseContainerId) {
        this.pendingReleaseContainerId = pendingReleaseContainerId;
    }

    public boolean isPendingDeletionRequested() {
        return pendingDeletionRequested;
    }

    public void setPendingDeletionRequested(boolean pendingDeletionRequested) {
        this.pendingDeletionRequested = pendingDeletionRequested;
    }

    public boolean isShowToolbarText() {
        return showToolbarText;
    }

    public void setShowToolbarText(boolean showToolbarText) {
        this.showToolbarText = showToolbarText;
    }

    public boolean isPropertiesCollapsed() {
        return propertiesCollapsed;
    }

    public void setPropertiesCollapsed(boolean propertiesCollapsed) {
        this.propertiesCollapsed = propertiesCollapsed;
    }

    public float getLeftRatioBeforeCollapse() {
        return leftRatioBeforeCollapse;
    }

    public void setLeftRatioBeforeCollapse(float leftRatioBeforeCollapse) {
        // 保护合法性，防止布局恢复时出现异常值
        this.leftRatioBeforeCollapse = Math.max(0.1f, Math.min(0.9f, leftRatioBeforeCollapse));
    }

    public void toggleNode(UUID nodeId) {
        if (!expandedNodes.add(nodeId)) {
            expandedNodes.remove(nodeId);
        }
    }

    public boolean isNodeExpanded(UUID nodeId) {
        return expandAll || expandedNodes.contains(nodeId);
    }
}

