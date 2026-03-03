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
        // 兜底：如存在待打开标记且当前未处于打开状态，则主动 openPopup，确保下一帧能够显示
        if (state.isPendingDeletionRequested() && !ImGui.isPopupOpen("ConfirmDeletion")) {
            org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] 兜底：检测到待删除标记，主动打开 ConfirmDeletion");
            ImGui.openPopup("ConfirmDeletion");
        }
        if (state.getPendingReleaseContainerId() != null && !ImGui.isPopupOpen("ConfirmRelease")) {
            org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] 兜底：检测到待释放目标，主动打开 ConfirmRelease");
            ImGui.openPopup("ConfirmRelease");
        }
        renderDeletionConfirmDialog();
        renderReleaseConfirmDialog();
        renderClearAllConfirmDialog();
    }

	/**
	 * 通用确认弹窗渲染：负责定位到屏幕中心、打开指定 id 的模态弹窗，渲染消息与“确定/取消”按钮。
	 * 返回值不需要，逻辑在 onConfirm 内处理。
	 */
	private void renderConfirmDialog(Runnable onConfirm) {
		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
		if (ImGui.beginPopupModal("ConfirmClearAll", ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.text(Icons.WARNING + " " + "您确定要清空所有容器吗？");
			ImGui.separator();
			if (ImGui.button("确定", 120, 0)) {
				if (onConfirm != null) {
					onConfirm.run();
				}
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button("取消", 120, 0)) {
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
		}
	}

    private void renderDeletionConfirmDialog() {
		if (ImGui.isPopupOpen("ConfirmDeletion")) {
			org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] 检测到 ConfirmDeletion 请求打开");
		}
		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);
		if (ImGui.beginPopupModal("ConfirmDeletion", ImGuiWindowFlags.AlwaysAutoResize)) {
			org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] ConfirmDeletion 弹窗可见");
			ImGui.text(Icons.WARNING + " 您确定要删除选中的容器吗？");
			ImGui.separator();
			if (ImGui.button("确定删除", 120, 0)) {
				org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] 确认删除被点击 -> 调用 controller.deleteSelectedContainers()");
				controller.deleteSelectedContainers();
				state.setPendingDeletionRequested(false);
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button("取消", 120, 0)) {
				org.slf4j.LoggerFactory.getLogger(ContainerDialogs.class).info("[ContainerDialogs] 删除取消");
				state.setPendingDeletionRequested(false);
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
		}
    }

    private void renderClearAllConfirmDialog() {
		renderConfirmDialog(controller::clearAllContainers);
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

