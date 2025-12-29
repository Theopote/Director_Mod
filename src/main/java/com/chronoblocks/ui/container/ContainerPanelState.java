package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ContainerPanelState 持有所有容器面板相关的 UI 状态。
 * 这些状态是纯粹的视图层数据，不应该包含任何副作用。
 */
public final class ContainerPanelState {

    private float leftPanelWidthRatio = 0.5f;
    private boolean expandAll = true;
    private UUID renamingContainerId;
    private UUID contextMenuContainerId;
    private final Set<UUID> expandedNodes = new HashSet<>();

    private Container hoveredContainer;
    private UUID pendingReleaseContainerId;
    private boolean pendingDeletionRequested;

    /**
     * 顶部按钮显示模式：
     * - false：仅图标（更紧凑）
     * - true：图标 + 文字（更易读）
     */
    private boolean showToolbarText = false;

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

    public void toggleNode(UUID nodeId) {
        if (!expandedNodes.add(nodeId)) {
            expandedNodes.remove(nodeId);
        }
    }

    public boolean isNodeExpanded(UUID nodeId) {
        return expandAll || expandedNodes.contains(nodeId);
    }
}

