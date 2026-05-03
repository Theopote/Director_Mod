package com.director.ui.container;

import com.director.core.container.Container;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.UUID;

/**
 * \u7BA1\u7406\u5BB9\u5668\u9762\u677F\u5185\u7684\u6240\u6709\u5F39\u7A97\u3002
 */
public class ContainerDialogs {

    private static final String LABEL_CANCEL = "\u53D6\u6D88";
    private static final String LABEL_CONFIRM = "\u786E\u5B9A";

    private final ContainerPanelController controller;
    private final ContainerPanelState state;

    public ContainerDialogs(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    public void render() {
        if (state.isPendingDeletionRequested() && !ImGui.isPopupOpen("ConfirmDeletion")) {
            org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] pending deletion detected, opening ConfirmDeletion");
            ImGui.openPopup("ConfirmDeletion");
        }
        if (state.getPendingReleaseContainerId() != null && !ImGui.isPopupOpen("ConfirmRelease")) {
            org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] pending release detected, opening ConfirmRelease");
            ImGui.openPopup("ConfirmRelease");
        }
        renderDeletionConfirmDialog();
        renderReleaseConfirmDialog();
        renderClearAllConfirmDialog();
    }

    private void renderConfirmDialog(String popupId, String message, String confirmLabel, Runnable onConfirm) {
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(Icons.WARNING + " " + message);
            ImGui.separator();
            if (ImGui.button(confirmLabel, 120, 0)) {
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
            org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] ConfirmDeletion requested");
        }
        ImVec2 center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (ImGui.beginPopupModal("ConfirmDeletion", ImGuiWindowFlags.AlwaysAutoResize)) {
            org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] ConfirmDeletion visible");
            ImGui.text(Icons.WARNING + " " + "\u60A8\u786E\u5B9A\u8981\u5220\u9664\u9009\u4E2D\u7684\u5BB9\u5668\u5417\uFF1F");
            ImGui.separator();
            if (ImGui.button("\u786E\u8BA4\u5220\u9664", 120, 0)) {
                org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] delete confirmed");
                controller.deleteSelectedContainers();
                state.setPendingDeletionRequested(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(LABEL_CANCEL, 120, 0)) {
                org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] delete cancelled");
                state.setPendingDeletionRequested(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void renderClearAllConfirmDialog() {
        renderConfirmDialog(
            "ConfirmClearAll",
            "\u60A8\u786E\u5B9A\u8981\u6E05\u7A7A\u6240\u6709\u5BB9\u5668\u5417\uFF1F",
            LABEL_CONFIRM,
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

            ImGui.text(Icons.WARNING + " " + "\u91CA\u653E\u5BB9\u5668 \"" + target.getName() + "\" ?");
            ImGui.separator();
            ImGui.textWrapped("\u91CA\u653E\u540E\u5BB9\u5668\u5C06\u88AB\u5220\u9664\uFF0C\u65B9\u5757\u5C06\u91CD\u65B0\u663E\u793A\u5728\u4E16\u754C\u4E2D\u3002");
            ImGui.spacing();

            if (ImGui.button("\u786E\u8BA4\u91CA\u653E", 120, 0)) {
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
