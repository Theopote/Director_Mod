package com.director.ui.dialogs;

import com.director.config.DirectorConfig;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.timeline.snapping.TimelineSnapSettings;
import com.director.ui.feedback.InlineNotice;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private final List<String> loadLayoutPresets = new ArrayList<>();
    private TimelineSettingsDraft timelineSettingsDraft;
    private String timelineSettingsErrorKey;
    private GeneralSettingsDraft generalSettingsDraft;
    private String generalSettingsErrorKey;

    public interface LayoutAccessor {
        boolean saveLayoutPreset(String presetName);
        List<String> listLayoutPresets();
        boolean loadLayoutPreset(String presetName);
        boolean deleteLayoutPreset(String presetName);
    }

    private LayoutAccessor layoutAccessor;

    public void setLayoutAccessor(LayoutAccessor accessor) {
        this.layoutAccessor = accessor;
    }

    public interface TimelineSettingsAccessor {
        TimelineSnapSettings getSnapSettings();

        float getFrameRate();

        int getTimeUnit();

        boolean commitSettings(float frameRate, int timeUnit, TimelineSnapSettings snapSettings);
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
        generalSettingsDraft = GeneralSettingsDraft.capture();
        generalSettingsErrorKey = null;
        showSettingsDialog.set(true);
    }

    public void closeSettingsDialog() {
        showSettingsDialog.set(false);
        generalSettingsDraft = null;
        generalSettingsErrorKey = null;
    }

    public void showTimelineSettingsDialog() {
        timelineSettingsDraft = TimelineSettingsDraft.capture(timelineSettingsAccessor);
        timelineSettingsErrorKey = null;
        showTimelineSettingsDialog.set(true);
    }

    public void closeTimelineSettingsDialog() {
        showTimelineSettingsDialog.set(false);
        timelineSettingsDraft = null;
        timelineSettingsErrorKey = null;
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
        refreshLayoutPresets();
        showLoadLayoutDialog.set(true);
    }

    public void closeLoadLayoutDialog() {
        showLoadLayoutDialog.set(false);
    }

    void refreshLayoutPresets() {
        loadLayoutPresets.clear();
        if (layoutAccessor != null) {
            loadLayoutPresets.addAll(layoutAccessor.listLayoutPresets());
        }
    }

    List<String> cachedLayoutPresets() {
        return loadLayoutPresets;
    }

    boolean saveLayoutPreset(String presetName) {
        if (layoutAccessor == null || !layoutAccessor.saveLayoutPreset(presetName)) {
            return false;
        }
        if (showLoadLayoutDialog.get() && !loadLayoutPresets.contains(presetName)) {
            loadLayoutPresets.add(presetName);
            loadLayoutPresets.sort(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                    .thenComparing(Comparator.naturalOrder()));
        }
        return true;
    }

    boolean deleteLayoutPreset(String presetName) {
        if (layoutAccessor == null || !layoutAccessor.deleteLayoutPreset(presetName)) {
            return false;
        }
        loadLayoutPresets.remove(presetName);
        return true;
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

        // ImGui 的 Esc/窗口关闭按钮会直接修改 p_open，此时同样丢弃未提交草稿。
        if (!showSettingsDialog.get()) {
            generalSettingsDraft = null;
            generalSettingsErrorKey = null;
        }
        if (!showTimelineSettingsDialog.get()) {
            timelineSettingsDraft = null;
            timelineSettingsErrorKey = null;
        }
    }

    private void renderAboutDialog() {
        String title = tr(DirectorLang.Menu.HELP_ABOUT);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(400, 300);

        if (ImGui.beginPopupModal(title, showAboutDialog)) {
            try {
                UITheme.title(Icons.ANIMATION + " Director Editor");

                renderGroup(tr(DirectorLang.SystemDialog.ABOUT_VERSION_INFO), () -> {
                    ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_VERSION));
                    ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_MINECRAFT));
                    ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_JAVA));
                });

                UITheme.separator();

                renderGroup(tr(DirectorLang.SystemDialog.ABOUT_DESCRIPTION),
                        () -> ImGui.textWrapped(tr(DirectorLang.SystemDialog.ABOUT_DESCRIPTION_DETAIL)));

                UITheme.separator();

                renderGroup(tr(DirectorLang.SystemDialog.ABOUT_CREDITS), () -> {
                    ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_COPYRIGHT));
                    ImGui.text(tr(DirectorLang.SystemDialog.ABOUT_BUILT_WITH));
                });

                UITheme.separator();

                if (ImGui.button(tr(DirectorLang.Common.OK), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeAboutDialog();
                }
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void renderSettingsDialog() {
        String title = tr(DirectorLang.SystemDialog.SETTINGS_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(500, 340);

        if (ImGui.beginPopupModal(title, showSettingsDialog)) {
            try {
                UITheme.title(Icons.SETTINGS + " " + title);

                GeneralSettingsDraft draft = generalSettingsDraft;
                if (draft == null) {
                    draft = GeneralSettingsDraft.capture();
                    generalSettingsDraft = draft;
                }
                GeneralSettingsDraft activeDraft = draft;

                renderGroup(tr(DirectorLang.SystemDialog.SETTINGS_GENERAL), () -> {
                    float[] frameRateRef = {activeDraft.defaultFps};
                    if (ImGui.dragFloat(
                            tr(DirectorLang.SystemDialog.SETTINGS_DEFAULT_FPS),
                            frameRateRef, 1.0f,
                            DirectorConfig.MIN_FRAME_RATE, DirectorConfig.MAX_FRAME_RATE, "%.0f"
                    )) {
                        activeDraft.defaultFps = frameRateRef[0];
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.SystemDialog.SETTINGS_DEFAULT_FPS_TOOLTIP));
                    }

                    ImBoolean autoSaveRef = new ImBoolean(activeDraft.autoSaveEnabled);
                    if (ImGui.checkbox(tr(DirectorLang.SystemDialog.SETTINGS_AUTO_SAVE), autoSaveRef)) {
                        activeDraft.autoSaveEnabled = autoSaveRef.get();
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.SystemDialog.SETTINGS_AUTO_SAVE_TOOLTIP));
                    }

                    if (activeDraft.autoSaveEnabled) {
                        int[] intervalRef = {activeDraft.autoSaveIntervalSeconds};
                        if (ImGui.dragInt(
                                tr(DirectorLang.SystemDialog.SETTINGS_AUTO_SAVE_INTERVAL),
                                intervalRef, 1,
                                DirectorConfig.MIN_AUTO_SAVE_INTERVAL_SECONDS,
                                DirectorConfig.MAX_AUTO_SAVE_INTERVAL_SECONDS
                        )) {
                            activeDraft.autoSaveIntervalSeconds = intervalRef[0];
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip(tr(DirectorLang.SystemDialog.SETTINGS_AUTO_SAVE_INTERVAL_TOOLTIP));
                        }
                    }
                });

                UITheme.separator();

                if (generalSettingsErrorKey != null) {
                    InlineNotice.draw(InlineNotice.Level.ERROR, tr(generalSettingsErrorKey));
                    UITheme.separator();
                }

                float buttonWidth = (ImGui.getContentRegionAvailX() - 20) / 3.0f;
                if (ImGui.button(tr(DirectorLang.Common.OK), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                    if (commitGeneralSettings()) {
                        closeSettingsDialog();
                    }
                }
                ImGui.sameLine();
                if (ImGui.button(tr(DirectorLang.SystemDialog.SETTINGS_RESET_DEFAULTS),
                        buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                    activeDraft.resetDefaults();
                    generalSettingsErrorKey = null;
                }
                ImGui.sameLine();
                if (ImGui.button(tr(DirectorLang.Common.CANCEL), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeSettingsDialog();
                }
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void renderTimelineSettingsDialog() {
        String title = tr(DirectorLang.Menu.TIMELINE_SETTINGS);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(500, 550);

        if (ImGui.beginPopupModal(title, showTimelineSettingsDialog)) {
            try {
                UITheme.title(Icons.TIMELINE + " " + title);

                if (timelineSettingsAccessor == null) {
                    InlineNotice.draw(InlineNotice.Level.ERROR,
                            tr(DirectorLang.SystemDialog.TimelineSettings.ACCESS_ERROR));
                    if (ImGui.button(tr(DirectorLang.Common.CLOSE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                        closeTimelineSettingsDialog();
                    }
                    return;
                }

                TimelineSettingsDraft draft = timelineSettingsDraft;
                if (draft == null) {
                    draft = TimelineSettingsDraft.capture(timelineSettingsAccessor);
                    timelineSettingsDraft = draft;
                }
                if (draft == null) {
                    InlineNotice.draw(InlineNotice.Level.ERROR,
                            tr(DirectorLang.SystemDialog.TimelineSettings.UNAVAILABLE));
                    if (ImGui.button(tr(DirectorLang.Common.CLOSE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                        closeTimelineSettingsDialog();
                    }
                    return;
                }

                TimelineSettingsDraft activeDraft = draft;
                TimelineSnapSettings snapSettings = activeDraft.snapSettings;
                renderGroup(tr(DirectorLang.SystemDialog.TimelineSettings.PROJECT_GROUP), () -> {
                    float[] frameRateRef = {activeDraft.frameRate};
                    if (ImGui.dragFloat(
                            tr(DirectorLang.SystemDialog.TimelineSettings.FRAME_RATE),
                            frameRateRef, 1.0f, 1.0f, 240.0f, "%.0f"
                    )) {
                        activeDraft.frameRate = frameRateRef[0];
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.FRAME_RATE_TOOLTIP));
                    }

                    String[] timeUnitOptions = {
                            tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_SECONDS),
                            tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_FRAMES),
                            tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_TICKS),
                    };
                    ImInt timeUnitRef = new ImInt(activeDraft.timeUnit);
                    if (ImGui.combo(tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT),
                            timeUnitRef, timeUnitOptions, timeUnitOptions.length)) {
                        activeDraft.timeUnit = timeUnitRef.get();
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.TIME_UNIT_TOOLTIP));
                    }
                });

                UITheme.separator();

                renderGroup(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_GROUP), () -> {
                    ImBoolean enabledRef = new ImBoolean(snapSettings.isEnabled());
                    if (ImGui.checkbox(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_ENABLED), enabledRef)) {
                        snapSettings.setEnabled(enabledRef.get());
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(tr(DirectorLang.SystemDialog.TimelineSettings.SNAP_ENABLED_TOOLTIP));
                    }
                });

                if (snapSettings.isEnabled()) {
                    UITheme.separator();

                    renderGroup(tr(DirectorLang.SystemDialog.TimelineSettings.GRID_GROUP), () -> {
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
                    });

                    UITheme.separator();

                    renderGroup(tr(DirectorLang.SystemDialog.TimelineSettings.OTHER_SNAP_GROUP), () -> {
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
                    });

                    UITheme.separator();

                    renderGroup(tr(DirectorLang.SystemDialog.TimelineSettings.THRESHOLD_GROUP), () -> {
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
                    });
                }

                UITheme.separator();

                if (timelineSettingsErrorKey != null) {
                    InlineNotice.draw(InlineNotice.Level.ERROR, tr(timelineSettingsErrorKey));
                    UITheme.separator();
                }

                if (ImGui.button(tr(DirectorLang.Common.OK), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    if (commitTimelineSettings()) {
                        closeTimelineSettingsDialog();
                    }
                }
                ImGui.sameLine();
                if (ImGui.button(tr(DirectorLang.Common.CANCEL), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeTimelineSettingsDialog();
                }
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void renderShortcutsDialog() {
        String title = tr(DirectorLang.SystemDialog.SHORTCUTS_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(700, 600);

        if (ImGui.beginPopupModal(title, showShortcutsDialog)) {
            try {
                UITheme.title(Icons.KEYBOARD + " " + title);

                if (ImGui.beginChild("##ShortcutsScroll", 0, -ImGui.getFrameHeightWithSpacing() - 10)) {
                    try {
                        var shortcutsByCategory = com.director.ui.input.ShortcutBindings.getAllShortcutsByCategory();

                        boolean firstCategory = true;
                        for (var entry : shortcutsByCategory.entrySet()) {
                            if (!firstCategory) {
                                UITheme.separator();
                            }
                            firstCategory = false;

                            renderGroup(entry.getKey(), () -> {
                                for (String shortcut : entry.getValue()) {
                                    ImGui.text(shortcut);
                                }
                            });
                        }
                    } finally {
                        ImGui.endChild();
                    }
                }

                UITheme.separator();

                if (ImGui.button(tr(DirectorLang.Common.CLOSE), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
                    closeShortcutsDialog();
                }
            } finally {
                ImGui.endPopup();
            }
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
            try {
                UITheme.colored(UITheme.Semantic.DANGER,
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
            } finally {
                ImGui.endPopup();
            }
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
            try {
                UITheme.title(Icons.SAVE + " " + title);

                ImGui.spacing();
                ImGui.text(tr(DirectorLang.SystemDialog.SAVE_LAYOUT_PROMPT));
                ImGui.spacing();

                ImGui.setNextItemWidth(-1);
                ImGui.inputText("##LayoutName", saveLayoutNameBuffer);

                ImGui.spacing();
                ImGui.separator();
                ImGui.spacing();

                String layoutName = saveLayoutNameBuffer.get().trim();
                boolean canSave = !layoutName.isEmpty() && layoutAccessor != null;

                ImGui.beginGroup();
                try {
                    float buttonWidth = (ImGui.getContentRegionAvailX() - 10) / 2.0f;
                    if (ImGui.button(tr(DirectorLang.Common.SAVE), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                        if (canSave && saveLayoutPreset(layoutName)) {
                            closeSaveLayoutDialog();
                        }
                    }

                    ImGui.sameLine();

                    if (ImGui.button(tr(DirectorLang.Common.CANCEL), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
                        closeSaveLayoutDialog();
                    }
                } finally {
                    ImGui.endGroup();
                }

                if (layoutName.isEmpty()) {
                    ImGui.spacing();
                    UITheme.colored(UITheme.Semantic.WARNING,
                            Icons.WARNING + " " + tr(DirectorLang.SystemDialog.SAVE_LAYOUT_NAME_REQUIRED)
                    );
                }
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void renderLoadLayoutDialog() {
        String title = tr(DirectorLang.SystemDialog.LOAD_LAYOUT_TITLE);
        ImGui.openPopup(title);
        ImGui.setNextWindowSize(500, 400);

        if (ImGui.beginPopupModal(title, showLoadLayoutDialog)) {
            try {
                UITheme.title(Icons.FOLDER + " " + title);

                ImGui.spacing();

                if (layoutAccessor == null) {
                    UITheme.colored(UITheme.Semantic.DANGER,
                            Icons.ERROR + " " + tr(DirectorLang.SystemDialog.LOAD_LAYOUT_ACCESSOR_UNAVAILABLE)
                    );
                    ImGui.spacing();
                    if (ImGui.button(tr(DirectorLang.Common.CLOSE), -1, UITheme.Spacing.BUTTON_HEIGHT)) {
                        closeLoadLayoutDialog();
                    }
                    return;
                }

                List<String> presets = List.copyOf(loadLayoutPresets);

                if (presets.isEmpty()) {
                    UITheme.colored(UITheme.Semantic.WARNING,
                            Icons.WARNING + " " + tr(DirectorLang.SystemDialog.LOAD_LAYOUT_NONE_FOUND)
                    );
                    ImGui.spacing();
                    ImGui.text(tr(DirectorLang.SystemDialog.LOAD_LAYOUT_SAVE_FIRST));
                    ImGui.spacing();
                    ImGui.separator();
                    ImGui.spacing();
                    renderLoadLayoutButtons();
                } else {
                    ImGui.text(tr(DirectorLang.SystemDialog.LOAD_LAYOUT_SELECT_PROMPT));
                    ImGui.spacing();

                    float listHeight = ImGui.getContentRegionAvailY() - UITheme.Spacing.BUTTON_HEIGHT - 20;
                    if (ImGui.beginChild("##LayoutList", 0, listHeight, true)) {
                        try {
                            for (String preset : presets) {
                                if (ImGui.selectable(preset, false)) {
                                    if (layoutAccessor.loadLayoutPreset(preset)) {
                                        closeLoadLayoutDialog();
                                        com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                                    }
                                }

                                if (ImGui.beginPopupContextItem("##PresetContext_" + preset)) {
                                    try {
                                        if (ImGui.menuItem(Icons.DELETE + " " + tr(DirectorLang.Common.DELETE))) {
                                            deleteLayoutPreset(preset);
                                        }
                                    } finally {
                                        ImGui.endPopup();
                                    }
                                }
                            }
                        } finally {
                            ImGui.endChild();
                        }
                    }

                    ImGui.spacing();
                    UITheme.separator();
                    ImGui.spacing();

                    renderLoadLayoutButtons();
                }
            } finally {
                ImGui.endPopup();
            }
        }
    }

    private void renderLoadLayoutButtons() {
        float buttonWidth = (ImGui.getContentRegionAvailX() - 10) / 2.0f;
        if (ImGui.button(tr(DirectorLang.Common.REFRESH), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
            refreshLayoutPresets();
        }
        ImGui.sameLine();
        if (ImGui.button(tr(DirectorLang.Common.CLOSE), buttonWidth, UITheme.Spacing.BUTTON_HEIGHT)) {
            closeLoadLayoutDialog();
        }
    }

    TimelineSettingsDraft getTimelineSettingsDraft() {
        return timelineSettingsDraft;
    }

    GeneralSettingsDraft getGeneralSettingsDraft() {
        return generalSettingsDraft;
    }

    boolean commitTimelineSettings() {
        TimelineSettingsDraft draft = timelineSettingsDraft;
        timelineSettingsErrorKey = null;
        if (draft == null || timelineSettingsAccessor == null) {
            timelineSettingsErrorKey = DirectorLang.SystemDialog.TimelineSettings.UNAVAILABLE;
            return false;
        }
        if (!draft.isValid()) {
            timelineSettingsErrorKey = DirectorLang.SystemDialog.TimelineSettings.INVALID_VALUES;
            return false;
        }
        if (!timelineSettingsAccessor.commitSettings(draft.frameRate, draft.timeUnit, draft.snapSettings)) {
            timelineSettingsErrorKey = DirectorLang.SystemDialog.TimelineSettings.COMMIT_ERROR;
            return false;
        }
        return true;
    }

    boolean commitGeneralSettings() {
        GeneralSettingsDraft draft = generalSettingsDraft;
        generalSettingsErrorKey = null;
        if (draft == null || !draft.isValid()) {
            generalSettingsErrorKey = DirectorLang.SystemDialog.SETTINGS_INVALID_VALUES;
            return false;
        }
        if (!DirectorConfig.updateUISettings(
                draft.defaultFps, draft.autoSaveEnabled, draft.autoSaveIntervalSeconds)) {
            generalSettingsErrorKey = DirectorLang.SystemDialog.SETTINGS_SAVE_ERROR;
            return false;
        }
        return true;
    }

    static final class TimelineSettingsDraft {
        float frameRate;
        int timeUnit;
        final TimelineSnapSettings snapSettings = new TimelineSnapSettings();

        static TimelineSettingsDraft capture(TimelineSettingsAccessor accessor) {
            if (accessor == null || accessor.getSnapSettings() == null) {
                return null;
            }
            TimelineSettingsDraft draft = new TimelineSettingsDraft();
            draft.frameRate = accessor.getFrameRate();
            draft.timeUnit = accessor.getTimeUnit();
            draft.snapSettings.copyFrom(accessor.getSnapSettings());
            return draft;
        }

        boolean isValid() {
            return Float.isFinite(frameRate) && frameRate >= 1.0f && frameRate <= 240.0f
                    && timeUnit >= 0 && timeUnit <= 2
                    && snapSettings.isValid();
        }
    }

    static final class GeneralSettingsDraft {
        float defaultFps;
        boolean autoSaveEnabled;
        int autoSaveIntervalSeconds;

        static GeneralSettingsDraft capture() {
            GeneralSettingsDraft draft = new GeneralSettingsDraft();
            draft.defaultFps = DirectorConfig.getDefaultFrameRate();
            draft.autoSaveEnabled = DirectorConfig.isAutoSaveEnabled();
            draft.autoSaveIntervalSeconds = DirectorConfig.getAutoSaveIntervalSeconds();
            return draft;
        }

        boolean isValid() {
            return DirectorConfig.isValidDefaultFrameRate(defaultFps)
                    && DirectorConfig.isValidAutoSaveIntervalSeconds(autoSaveIntervalSeconds);
        }

        void resetDefaults() {
            defaultFps = DirectorConfig.DEFAULT_FRAME_RATE;
            autoSaveEnabled = DirectorConfig.DEFAULT_AUTO_SAVE_ENABLED;
            autoSaveIntervalSeconds = DirectorConfig.DEFAULT_AUTO_SAVE_INTERVAL_SECONDS;
        }
    }

    private void renderGroup(String title, Runnable content) {
        UITheme.beginGroup(title);
        try {
            content.run();
        } finally {
            UITheme.endGroup();
        }
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }
}
