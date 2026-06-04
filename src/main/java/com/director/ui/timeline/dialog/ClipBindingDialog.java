package com.director.ui.timeline.dialog;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerType;
import com.director.ui.timeline.commands.BindClipToContainerCommand;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.theme.UITheme;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 片段容器绑定对话框
 */
public final class ClipBindingDialog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClipBindingDialog.class);
    
    private static TimelineClip clip;
    private static List<Container> containers = Collections.emptyList();
    private static int selectedIndex = -1;
    private static boolean shouldOpen = false;
    private static TimelineModel model;
    private static TimelineCommandHistory commandHistory;
    private static Runnable onDataChanged;

    public static void initialize(TimelineModel m, TimelineCommandHistory h, Runnable dataChangedCallback) {
        model = m;
        commandHistory = h;
        onDataChanged = dataChangedCallback;
    }

    public static void open(TimelineClip c) {
        if (c == null) {
            return;
        }
        clip = c;
        containers = ContainerManager.getInstance().getAllContainers().stream()
            .filter(ClipBindingDialog::isBindableContainer)
            .toList();
        selectedIndex = -1;
        
        if (c.getBoundContainerId() != null) {
            for (int i = 0; i < containers.size(); i++) {
                Container container = containers.get(i);
                if (container.getId().equals(c.getBoundContainerId())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedIndex < 0 && !containers.isEmpty()) {
            selectedIndex = 0;
        }
        shouldOpen = true;
    }

    public static void renderIfNeeded() {
        if (clip == null) {
            return;
        }
        
        final String popupId = "绑定容器##ClipBindingPopup";
        if (shouldOpen) {
            ImGui.openPopup(popupId);
            shouldOpen = false;
        }
        
        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (containers.isEmpty()) {
                ImGui.text("当前没有可绑定的容器。");
            } else if (ImGui.beginListBox("##ClipBindingList")) {
                for (int i = 0; i < containers.size(); i++) {
                    Container container = containers.get(i);
                    String label = container.getName() != null && !container.getName().isBlank()
                            ? container.getName()
                            : container.getId().toString();
                    boolean selected = selectedIndex == i;
                    if (ImGui.selectable(label, selected)) {
                        selectedIndex = i;
                    }
                }
                ImGui.endListBox();
            }

            boolean hasSelection = selectedIndex >= 0 && selectedIndex < containers.size();

            if (ImGui.button("绑定", 0, UITheme.Spacing.BUTTON_HEIGHT) && hasSelection) {
                bindToSelected();
                ImGui.closeCurrentPopup();
            }

            if (clip != null && clip.getBoundContainerId() != null) {
                ImGui.sameLine();
                if (ImGui.button("取消绑定", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    unbind();
                    ImGui.closeCurrentPopup();
                }
            }

            ImGui.sameLine();
            if (ImGui.button("关闭", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                close();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        } else if (!shouldOpen) {
            close();
        }
    }

    private static void bindToSelected() {
        if (clip == null || model == null || commandHistory == null) {
            return;
        }
        
        if (selectedIndex >= 0 && selectedIndex < containers.size()) {
            Container chosen = containers.get(selectedIndex);
            commandHistory.executeCommand(
                new BindClipToContainerCommand(model, clip, chosen.getId(), chosen.getName()));
            if (onDataChanged != null) {
                onDataChanged.run();
            }
            LOGGER.debug("[Timeline] 绑定片段 {} 到容器 {}", clip.getLabel(), chosen.getName());
        }
        close();
    }

    private static void unbind() {
        if (clip == null || model == null || commandHistory == null) {
            return;
        }
        
        commandHistory.executeCommand(
            new BindClipToContainerCommand(model, clip, null, null));
        if (onDataChanged != null) {
            onDataChanged.run();
        }
        close();
    }

    private static void close() {
        clip = null;
        containers = Collections.emptyList();
        selectedIndex = -1;
        shouldOpen = false;
    }

    private static boolean isBindableContainer(Container container) {
        if (container == null || container.getType() == ContainerType.GROUP) {
            return false;
        }
        if (clip != null && container.getId().equals(clip.getBoundContainerId())) {
            return true;
        }
        return !ContainerTimelineService.getInstance().isContainerOnTimeline(container.getId());
    }
}

