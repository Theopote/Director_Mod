package com.chronoblocks.ui.container;

import com.chronoblocks.core.container.Container;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.UUID;

/**
 * 管理容器面板内的所有弹窗。
 * 当前版本仅迁移了删除容器等已有弹窗，后续可以逐步扩展。
 */
public class ContainerDialogs {

    private final ContainerPanelController controller;
    private final ContainerPanelState state;

    public ContainerDialogs(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    public void render() {
        renderDeletionConfirmDialog();
        renderReleaseConfirmDialog();
    }

    private void renderDeletionConfirmDialog() {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal("ConfirmDeletion", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(Icons.WARNING + " 您确定要删除选中的容器吗？");
            ImGui.separator();

            if (ImGui.button("确定删除", 120, 0)) {
                controller.deleteSelectedContainers();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderReleaseConfirmDialog() {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal("ConfirmRelease", ImGuiWindowFlags.AlwaysAutoResize)) {
            UUID targetId = state.getPendingReleaseContainerId();
            Container target = targetId != null ? controller.getContainer(targetId) : null;

            if (target == null) {
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }

            ImGui.text(Icons.WARNING + " 释放容器 \"" + target.getName() + "\" ?");
            ImGui.separator();
            ImGui.textWrapped("释放后容器将被删除，方块将重新显示在世界中。");
            ImGui.spacing();

            if (ImGui.button("确定释放", 120, 0)) {
                controller.releaseContainer(targetId);
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消", 120, 0)) {
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}

