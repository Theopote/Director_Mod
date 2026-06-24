package com.director.ui.dialogs;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * 对话框管理器 - 集中管理所有对话框的显示状态和生命周期
 */
public class DialogManager {

    private final ImBoolean showAboutDialog = new ImBoolean(false);
    private final ImBoolean showSettingsDialog = new ImBoolean(false);
    private final ImBoolean showTimelineSettingsDialog = new ImBoolean(false);
    private final ImBoolean showShortcutsDialog = new ImBoolean(false);
    private final ImBoolean showSaveLayoutDialog = new ImBoolean(false);
    private final ImBoolean showLoadLayoutDialog = new ImBoolean(false);
    private boolean showNewAnimationConfirm = false;

    private final ImString saveLayoutNameBuffer = new ImString("", 256);

    public interface LayoutAccessor {
        boolean saveLayoutPreset(String presetName);
        java.util.List<String> listLayoutPresets();
        boolean loadLayoutPreset(String presetName);
        boolean deleteLayoutPreset(String presetName);
    }

    private LayoutAccessor layoutAccessor;

    public void setLayoutAccessor(LayoutAccessor accessor) {
        this.layoutAccessor = accessor;
    }

    public interface TimelineSettingsAccessor {
        com.director.timeline.snapping.TimelineSnapSettings getSnapSettings();

        default float getFrameRate() {
            return 30.0f;
        }

        default boolean setFrameRate(float fps) {
            return false;
        }

        default int getTimeUnit() {
            return 0;
        }

        default boolean setTimeUnit(int unit) {
            return false;
        }
    }

    private TimelineSettingsAccessor timelineSettingsAccessor;

    public void setTimelineSettingsAccessor(TimelineSettingsAccessor accessor) {
        this.timelineSettingsAccessor = accessor;
    }

    public void showAboutDialog() {
        showAboutDialog.set(true);
    }

    public void closeAboutDialog() {
        showAboutDialog.set(false);
    }

    public void showSettingsDialog() {
        showSettingsDialog.set(true);
    }

    public void closeSettingsDialog() {
        showSettingsDialog.set(false);
    }

    public void showTimelineSettingsDialog() {
        showTimelineSettingsDialog.set(true);
    }

    public void closeTimelineSettingsDialog() {
        showTimelineSettingsDialog.set(false);
    }

    public void showShortcutsDialog() {
        showShortcutsDialog.set(true);
    }

    public void closeShortcutsDialog() {
        showShortcutsDialog.set(false);
    }

    public void showNewAnimationConfirm() {
        showNewAnimationConfirm = true;
    }

    public void closeNewAnimationConfirm() {
        showNewAnimationConfirm = false;
    }

    public void showSaveLayoutDialog() {
        saveLayoutNameBuffer.set("");
        showSaveLayoutDialog.set(true);
    }

    public void closeSaveLayoutDialog() {
        showSaveLayoutDialog.set(false);
    }

    public void showLoadLayoutDialog() {
        showLoadLayoutDialog.set(true);
    }

    public void closeLoadLayoutDialog() {
        showLoadLayoutDialog.set(false);
    }

    public void renderDialogs() {
        if (showAboutDialog.get()) {
            renderAboutDialog();
        }
        if (showSettingsDialog.get()) {
            renderSettingsDialog();
        }
        if (showTimelineSettingsDialog.get()) {
            renderTimelineSettingsDialog();
        }
        if (showShortcutsDialog.get()) {
            renderShortcutsDialog();
        }
        if (showSaveLayoutDialog.get()) {
            renderSaveLayoutDialog();
        }
        if (showLoadLayoutDialog.get()) {
            renderLoadLayoutDialog();
        }
        renderNewAnimationDialog();
    }

    private void renderAboutDialog() {
        String title = tr(DirectorLang.Menu.HELP_ABOUT);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(400, 300);

        if (ImGui.beginPopupModal(title, showAboutDialog)) {
            UITheme.title(Icons.ANIMATION + " Director Editor");

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.ABOUT_VERSION_INFO));
            ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_VERSION));
            ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_MINECRAFT));
            ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_JAVA));
            UITheme.endGroup();

            UITheme.separator();

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.ABOUT_DESCRIPTION));
            ImGui.textWrapped(tr(DirectorLang.SystemDialog.ABOUT_DESCRIPTION_DETAIL));
            UITheme.endGroup();

            UITheme.separator();

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.ABOUT_CREDITS));
            ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_COPYRIGHT));
            ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_BUILT_WITH));
            UITheme.endGroup();

            UITheme.separator();

            if (ImGui.button(tr(DirectorLang.Common.OK), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeAboutDialog();
            }

            ImGui.endPopup();
        }
    }

    private void renderSettingsDialog() {
        String title = tr(DirectorLang.SystemDialog.SETTINGS_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(500, 400);

        if (ImGui.beginPopupModal(title, showSettingsDialog)) {
            UITheme.title(Icons.SETTINGS + " " + title);

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.SETTINGS_GENERAL));
            ImGui.text(tr(DirectorLang.SystemDialog.SETTINGS_AUTO_SAVE));
            ImGui.text(tr(DirectorLang.SystemDialog.SETTINGS_DEFAULT_FPS));
            UITheme.endGroup();

            UITheme.separator();

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.SETTINGS_THEME));
            ImGui.text(tr(DirectorLang.SystemDialog.SETTINGS_THEME_CURRENT));
            UITheme.endGroup();

            UITheme.separator();

            if (ImGui.button(tr(DirectorLang.Common.OK), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeSettingsDialog();
            }
            ImGui.sameLine();
            if (ImGui.button(tr(DirectorLang.Common.CANCEL), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeSettingsDialog();
            }

            ImGui.endPopup();
        }
    }

    private void renderTimelineSettingsDialog() {
        String title = tr(DirectorLang.Menu.TIMELINE_SETTINGS);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(500, 550);

        if (ImGui.beginPopupModal(title, showTimelineSettingsDialog)) {
            UITheme.title(Icons.TIMELINE + " " + title);

            if (timelineSettingsAccessor == null) {
                ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, tr(DirectorLang.SystemDialog.TimelineSettings.ACCESS_ERROR));
                if (ImGui.button(tr(DirectorLang.Common.CLOSE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeTimelineSettingsDialog();
                }
                ImGui.endPopup();
                return;
            }

            com.director.timeline.snapping.TimelineSnapSettings snapSettings =
                    timelineSettingsAccessor.getSnapSettings();

            if (snapSettings == null) {
                ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, tr(DirectorLang.SystemDialog.TimelineSettings.UNAVAILABLE));
                if (ImGui.button(tr(DirectorLang.Common.CLOSE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeTimelineSettingsDialog();
                }
                ImGui.endPopup();
                return;
            }

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.TimelineSettings.PROJECT_GROUP));

            float frameRate = timelineSettingsAccessor.getFrameRate();
            float[] frameRateRef = {frameRate};
            boolean frameRateChanged = ImGui.dragFloat(
                    tr(DirectorLang.SystemDialog.TimelineSettings.FRAME_RATE),
                    frameRateRef, 1.0f, 1.0f, 240.0f, "%.0f"
            );
            if (frameRateChanged) {
                boolean success = timelineSettingsAccessor.setFrameRate(frameRateRef[0]);
                if (!success) {
                    frameRateRef[0] = frameRate;
                    ImGui.sameLine();
                    ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, Icons.WARNING);
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.FRAME_RATE_ERROR_TOOLTIP));
                    }
                }
            }
            if (ImGui.isItemHovered() && !frameRateChanged) {
                ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.FRAME_RATE_TOOLTIP));
            }

            String[] timeUnitOptions = {
                    tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_SECONDS),
                    tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_FRAMES),
                    tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_TICKS),
            };
            ImInt timeUnitRef = new ImInt(timelineSettingsAccessor.getTimeUnit());
            if (ImGui.combo(tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT), timeUnitRef, timeUnitOptions, timeUnitOptions.length)) {
                timelineSettingsAccessor.setTimeUnit(timeUnitRef.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_TOOLTIP));
            }

            UITheme.endGroup();
            UITheme.separator();

            UITheme.beginGroup(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_GROUP));
            ImBoolean enabledRef = new ImBoolean(snapSettings.isEnabled());
            if (ImGui.checkbox(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_ENABLED), enabledRef)) {
                snapSettings.setEnabled(enabledRef.get());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_ENABLED_TOOLTIP));
            }
            UITheme.endGroup();

            if (snapSettings.isEnabled()) {
                UITheme.separator();

                UITheme.beginGroup(tr(DirectorLang.SystemDialog.TimelineSettings.GRID_GROUP));
                ImBoolean snapToGridRef = new ImBoolean(snapSettings.isSnapToGrid());
                if (ImGui.checkbox(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_TO_GRID), snapToGridRef)) {
                    snapSettings.setSnapToGrid(snapToGridRef.get());
                }

                float[] gridIntervalRef = {snapSettings.getGridInterval()};
                if (ImGui.dragFloat(
                        tr(DirectorLang.SystemDialog.TimelineSettings.GRID_INTERVAL),
                        gridIntervalRef, 0.01f, 0.001f, 10.0f, "%.3f"
                )) {
                    snapSettings.setGridInterval(gridIntervalRef[0]);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.GRID_INTERVAL_TOOLTIP));
                }
                UITheme.endGroup();

                UITheme.separator();

                UITheme.beginGroup(tr(DirectorLang.SystemDialog.TimelineSettings.OTHER_SNAP_GROUP));

                ImBoolean snapToClipEdgesRef = new ImBoolean(snapSettings.isSnapToClipEdges());
                if (ImGui.checkbox(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_TO_CLIP_EDGES), snapToClipEdgesRef)) {
                    snapSettings.setSnapToClipEdges(snapToClipEdgesRef.get());
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_TO_CLIP_EDGES_TOOLTIP));
                }

                ImBoolean snapToKeyframesRef = new ImBoolean(snapSettings.isSnapToKeyframes());
                if (ImGui.checkbox(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_TO_KEYFRAMES), snapToKeyframesRef)) {
                    snapSettings.setSnapToKeyframes(snapToKeyframesRef.get());
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_TO_KEYFRAMES_TOOLTIP));
                }

                UITheme.endGroup();
                UITheme.separator();

                UITheme.beginGroup(tr(DirectorLang.SystemDialog.TimelineSettings.THRESHOLD_GROUP));
                float[] pixelThresholdRef = {snapSettings.getPixelThreshold()};
                if (ImGui.dragFloat(
                        tr(DirectorLang.SystemDialog.TimelineSettings.PIXEL_THRESHOLD),
                        pixelThresholdRef, 1.0f, 1.0f, 50.0f, "%.0f"
                )) {
                    snapSettings.setPixelThreshold(pixelThresholdRef[0]);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.PIXEL_THRESHOLD_TOOLTIP));
                }
                UITheme.endGroup();
            }

            UITheme.separator();

            if (ImGui.button(tr(DirectorLang.Common.OK), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeTimelineSettingsDialog();
            }
            ImGui.sameLine();
            if (ImGui.button(tr(DirectorLang.Common.CANCEL), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeTimelineSettingsDialog();
            }

            ImGui.endPopup();
        }
    }

    private void renderShortcutsDialog() {
        String title = tr(DirectorLang.SystemDialog.SHORTCUTS_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(700, 600);

        if (ImGui.beginPopupModal(title, showShortcutsDialog)) {
            UITheme.title(Icons.KEYBOARD + " " + title);

            if (ImGui.beginChild("##ShortcutsScroll", 0, -ImGui.getFrameHeightWithSpacing() - 10)) {
                var shortcutsByCategory = com.director.ui.input.ShortcutBindings.getAllShortcutsByCategory();

                boolean firstCategory = true;
                for (var entry : shortcutsByCategory.entrySet()) {
                    if (!firstCategory) {
                        UITheme.separator();
                    }
                    firstCategory = false;

                    UITheme.beginGroup(entry.getKey());
                    for (String shortcut : entry.getValue()) {
                        ImGui.text(shortcut);
                    }
                    UITheme.endGroup();
                }

                ImGui.endChild();
            }

            UITheme.separator();

            if (ImGui.button(tr(DirectorLang.Common.CLOSE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeShortcutsDialog();
            }

            ImGui.endPopup();
        }
    }

    private void renderNewAnimationDialog() {
        if (!showNewAnimationConfirm) {
            return;
        }

        String title = tr(DirectorLang.SystemDialog.RESET_WORKSPACE_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(420, 190);
        ImBoolean popupOpen = new ImBoolean(true);
        if (ImGui.beginPopupModal(title, popupOpen, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textColored(
                    1.0f, 0.47f, 0.31f, 1.0f,
                    Icons.WARNING + " " + tr(DirectorLang.SystemDialog.RESET_WORKSPACE_WARNING)
            );
            ImGui.spacing();
            ImGui.textWrapped(tr(DirectorLang.SystemDialog.RESET_WORKSPACE_MESSAGE));
            ImGui.spacing();

            if (ImGui.button(tr(DirectorLang.SystemDialog.RESET_WORKSPACE_CONFIRM), 150, UITheme.Spacing.BUTTON_HEIGHT)) {
                onNewAnimationConfirm();
                closeNewAnimationConfirm();
                popupOpen.set(false);
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button(tr(DirectorLang.Common.CANCEL), 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeNewAnimationConfirm();
                popupOpen.set(false);
                ImGui.closeCurrentPopup();
            }

            if (!popupOpen.get()) {
                closeNewAnimationConfirm();
            }

            ImGui.endPopup();
        }
    }

    private Runnable newAnimationConfirmCallback;

    public void setNewAnimationConfirmCallback(Runnable callback) {
        this.newAnimationConfirmCallback = callback;
    }

    private void onNewAnimationConfirm() {
        if (newAnimationConfirmCallback != null) {
            newAnimationConfirmCallback.run();
        }
    }

    private void renderSaveLayoutDialog() {
        String title = tr(DirectorLang.SystemDialog.SAVE_LAYOUT_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(450, 200);

        if (ImGui.beginPopupModal(title, showSaveLayoutDialog)) {
            UITheme.title(Icons.SAVE + " " + title);

            ImGui.spacing();
            ImGui.text(tr(DirectorLang.SystemDialog.SAVE_LAYOUT_PROMPT));
            ImGui.spacing();

            ImGui.setNextItemWidth(-1);
            ImGui.inputText("##LayoutName", saveLayoutNameBuffer);

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            ImGui.beginGroup();
            float buttonWidth = (ImGui.getContentRegionAvailX() - 10) / 2.0f;

            String layoutName = saveLayoutNameBuffer.get().trim();
            boolean canSave = !layoutName.isEmpty() && layoutAccessor != null;

            if (ImGui.button(tr(DirectorLang.Common.SAVE), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (canSave && layoutAccessor.saveLayoutPreset(layoutName)) {
                    closeSaveLayoutDialog();
                }
            }

            ImGui.sameLine();

            if (ImGui.button(tr(DirectorLang.Common.CANCEL), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                closeSaveLayoutDialog();
            }

            ImGui.endGroup();

            if (layoutName.isEmpty()) {
                ImGui.spacing();
                ImGui.textColored(
                        0.8f, 0.6f, 0.2f, 1.0f,
                        Icons.WARNING + " " + tr(DirectorLang.SystemDialog.SAVE_LAYOUT_NAME_REQUIRED)
                );
            }

            ImGui.endPopup();
        }
    }

    private void renderLoadLayoutDialog() {
        String title = tr(DirectorLang.SystemDialog.LOAD_LAYOUT_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(500, 400);

        if (ImGui.beginPopupModal(title, showLoadLayoutDialog)) {
            UITheme.title(Icons.FOLDER + " " + title);

            ImGui.spacing();

            if (layoutAccessor == null) {
                ImGui.textColored(
                        1.0f, 0.3f, 0.3f, 1.0f,
                        Icons.ERROR + " " + tr(DirectorLang.SystemDialog.LOAD_LAYOUT_ACCESSOR_UNAVAILABLE)
                );
                ImGui.spacing();
                if (ImGui.button(tr(DirectorLang.Common.CLOSE), -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeLoadLayoutDialog();
                }
                ImGui.endPopup();
                return;
            }

            java.util.List<String> presets = layoutAccessor.listLayoutPresets();

            if (presets.isEmpty()) {
                ImGui.textColored(
                        0.8f, 0.6f, 0.2f, 1.0f,
                        Icons.WARNING + " " + tr(DirectorLang.SystemDialog.LOAD_LAYOUT_NONE_FOUND)
                );
                ImGui.spacing();
                ImGui.text(tr(DirectorLang.SystemDialog.LOAD_LAYOUT_SAVE_FIRST));
                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();
                if (ImGui.button(tr(DirectorLang.Common.CLOSE), -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeLoadLayoutDialog();
                }
            } else {
                ImGui.text(tr(DirectorLang.SystemDialog.LOAD_LAYOUT_SELECT_PROMPT));
                ImGui.spacing();

                float listHeight = ImGui.getContentRegionAvailY() - UITheme.Spacing.BUTTON_HEIGHT - 20;
                if (ImGui.beginChild("##LayoutList", 0, listHeight, true)) {
                    for (String preset : presets) {
                        if (ImGui.selectable(preset, false)) {
                            if (layoutAccessor.loadLayoutPreset(preset)) {
                                closeLoadLayoutDialog();
                                com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                            }
                        }

                        if (ImGui.beginPopupContextItem("##PresetContext_" + preset)) {
                            if (ImGui.menuItem(Icons.DELETE + " " + tr(DirectorLang.Common.DELETE))) {
                                layoutAccessor.deleteLayoutPreset(preset);
                            }
                            ImGui.endPopup();
                        }
                    }
                }
                ImGui.endChild();

                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();

                if (ImGui.button(tr(DirectorLang.Common.CLOSE), -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeLoadLayoutDialog();
                }
            }

            ImGui.endPopup();
        }
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }
}
