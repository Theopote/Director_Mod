package com.director.ui.container;

import com.director.core.container.Container;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
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

    private final ContainerPanelController controller;
    private final ContainerPanelState state;

    public ContainerDialogs(ContainerPanelController controller, ContainerPanelState state) {
        this.controller = controller;
        this.state = state;
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
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
            ImGui.text(Icons.WARNING + " " + tr(DirectorLang.ContainerPanel.Dialog.CLEAR_ALL_CONFIRM));
            ImGui.separator();
            if (ImGui.button(tr(DirectorLang.Common.OK), 120, 0)) {
                if (onConfirm != null) {
                    onConfirm.run();
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(tr(DirectorLang.Common.CANCEL), 120, 0)) {
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
            ImGui.text(Icons.WARNING + " " + tr(DirectorLang.ContainerPanel.Dialog.DELETE_CONFIRM));
            ImGui.separator();
            if (ImGui.button(tr(DirectorLang.ContainerPanel.Dialog.CONFIRM_DELETE), 120, 0)) {
                LOGGER.info("[ContainerDialogs] delete confirmed");
                controller.deleteSelectedContainers();
                state.setPendingDeletionRequested(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(tr(DirectorLang.Common.CANCEL), 120, 0)) {
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

            ImGui.text(Icons.WARNING + " " + tr(DirectorLang.ContainerPanel.Dialog.RELEASE_TITLE, target.getName()));
            ImGui.separator();
            ImGui.textWrapped(tr(DirectorLang.ContainerPanel.Dialog.RELEASE_MESSAGE));
            ImGui.spacing();

            if (ImGui.button(tr(DirectorLang.ContainerPanel.Dialog.CONFIRM_RELEASE), 120, 0)) {
                controller.releaseContainer(targetId);
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(tr(DirectorLang.Common.CANCEL), 120, 0)) {
                state.setPendingReleaseContainerId(null);
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }
}
