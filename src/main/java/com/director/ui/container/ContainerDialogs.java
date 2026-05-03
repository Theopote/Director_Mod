package com.director.ui.container;

import com.director.core.container.Container;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 管理容器面板内的所有弹窗。
 */
public class ContainerDialogs {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerDialogs.class);
    private static final String LABEL_CANCEL = "取消";
    private static final String LABEL_CONFIRM = "确定";

    private final ContainerPanelController controller;
    private final ContainerPanelState state;

    public ContainerDialogs(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    public void render() {
        if (state.isPendingDeletionRequested() && !ImGui.isPopupOpen("ConfirmDeletion")) {
            LOGGER.info("[ContainerDialogs] pending deletion detected, opening ConfirmDeletion");
            ImGui.openPopup("ConfirmDeletion");
        }
        if (state.getPendingReleaseContainerId() != null && !ImGui.isPopupOpen("ConfirmRelease")) {
            LOGGER.info("[ContainerDialogs] pending release detected, opening ConfirmRelease");
            ImGui.openPopup("ConfirmRelease");
        }
        renderDeletionConfirmDialog();
        renderReleaseConfirmDialog();
        renderClearAllConfirmDialog();
    }

    private void renderConfirmDialog(Runnable onConfirm) {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (ImGui.beginPopupModal("ConfirmClearAll", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(Icons.WARNING + " " + "您确定要清空所有容器吗？");
            ImGui.separator();
            if (ImGui.button(ContainerDialogs.LABEL_CONFIRM, 120, 0)) {
                if (onConfirm != null) {
                    onConfirm.run();
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(LABEL_CANCEL, 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderDeletionConfirmDialog() {
        if (ImGui.isPopupOpen("ConfirmDeletion")) {
            LOGGER.info("[ContainerDialogs] ConfirmDeletion requested");
        }
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (ImGui.beginPopupModal("ConfirmDeletion", ImGuiWindowFlags.AlwaysAutoResize)) {
            LOGGER.info("[ContainerDialogs] ConfirmDeletion visible");
            ImGui.text(Icons.WARNING + " " + "您确定要删除选中的容器吗？");
            ImGui.separator();
            if (ImGui.button("确认删除", 120, 0)) {
                LOGGER.info("[ContainerDialogs] delete confirmed");
                controller.deleteSelectedContainers();
                state.setPendingDeletionRequested(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(LABEL_CANCEL, 120, 0)) {
                LOGGER.info("[ContainerDialogs] delete cancelled");
                state.setPendingDeletionRequested(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderClearAllConfirmDialog() {
        renderConfirmDialog(
                controller::clearAllContainers
        );
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

            ImGui.text(Icons.WARNING + " " + "释放容器 \"" + target.getName() + "\" ?");
            ImGui.separator();
            ImGui.textWrapped("释放后容器将被删除，方块将重新显示在世界中。");
            ImGui.spacing();

            if (ImGui.button("确认释放", 120, 0)) {
                controller.releaseContainer(targetId);
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(LABEL_CANCEL, 120, 0)) {
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
