package com.director.ui.dialogs;

import com.director.config.DirectorConfig;
import com.director.timeline.snapping.TimelineSnapSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogManagerSettingsTest {
    private final DirectorConfig.ConfigData.UISettings ui = DirectorConfig.getConfig().ui;
    private float previousDefaultFps;
    private boolean previousAutoSaveEnabled;
    private int previousAutoSaveIntervalSeconds;

    @BeforeEach
    void captureConfig() {
        previousDefaultFps = ui.defaultFps;
        previousAutoSaveEnabled = ui.autoSaveEnabled;
        previousAutoSaveIntervalSeconds = ui.autoSaveIntervalSeconds;
    }

    @AfterEach
    void restoreConfig() {
        ui.defaultFps = previousDefaultFps;
        ui.autoSaveEnabled = previousAutoSaveEnabled;
        ui.autoSaveIntervalSeconds = previousAutoSaveIntervalSeconds;
    }

    @Test
    void timelineSettingsStayInDraftUntilOneValidCommit() {
        FakeTimelineSettings timeline = new FakeTimelineSettings();
        DialogManager dialogs = new DialogManager();
        dialogs.setTimelineSettingsAccessor(timeline);

        dialogs.showTimelineSettingsDialog();
        DialogManager.TimelineSettingsDraft draft = dialogs.getTimelineSettingsDraft();
        assertNotSame(timeline.snapSettings, draft.snapSettings);

        draft.frameRate = 48.0f;
        draft.timeUnit = 2;
        draft.snapSettings.setGridInterval(0.25f);
        draft.snapSettings.setPixelThreshold(12.0f);

        assertEquals(60.0f, timeline.frameRate);
        assertEquals(1, timeline.timeUnit);
        assertEquals(0.1f, timeline.snapSettings.getGridInterval());
        assertEquals(0, timeline.commits);

        assertTrue(dialogs.commitTimelineSettings());
        assertEquals(1, timeline.commits);
        assertEquals(48.0f, timeline.frameRate);
        assertEquals(2, timeline.timeUnit);
        assertEquals(0.25f, timeline.snapSettings.getGridInterval());
        assertEquals(12.0f, timeline.snapSettings.getPixelThreshold());
    }

    @Test
    void cancellingDiscardsTimelineDraftAndReopeningReadsLatestValues() {
        FakeTimelineSettings timeline = new FakeTimelineSettings();
        DialogManager dialogs = new DialogManager();
        dialogs.setTimelineSettingsAccessor(timeline);

        dialogs.showTimelineSettingsDialog();
        DialogManager.TimelineSettingsDraft draft = dialogs.getTimelineSettingsDraft();
        draft.frameRate = 12.0f;
        draft.snapSettings.setEnabled(false);

        timeline.frameRate = 24.0f;
        timeline.snapSettings.setGridInterval(0.5f);
        dialogs.closeTimelineSettingsDialog();
        assertNull(dialogs.getTimelineSettingsDraft());

        dialogs.showTimelineSettingsDialog();
        draft = dialogs.getTimelineSettingsDraft();
        assertEquals(24.0f, draft.frameRate);
        assertTrue(draft.snapSettings.isEnabled());
        assertEquals(0.5f, draft.snapSettings.getGridInterval());
        assertEquals(0, timeline.commits);
    }

    @Test
    void invalidTimelineDraftNeverReachesAccessor() {
        FakeTimelineSettings timeline = new FakeTimelineSettings();
        DialogManager dialogs = new DialogManager();
        dialogs.setTimelineSettingsAccessor(timeline);
        dialogs.showTimelineSettingsDialog();
        DialogManager.TimelineSettingsDraft draft = dialogs.getTimelineSettingsDraft();

        draft.frameRate = Float.NaN;
        assertFalse(dialogs.commitTimelineSettings());

        draft.frameRate = 60.0f;
        draft.timeUnit = 3;
        assertFalse(dialogs.commitTimelineSettings());

        draft.timeUnit = 0;
        draft.snapSettings.setPixelThreshold(Float.NaN);
        assertFalse(dialogs.commitTimelineSettings());
        assertEquals(0, timeline.commits);
    }

    @Test
    void generalSettingsDraftSupportsCancelAndDefaultResetWithoutSaving() {
        ui.defaultFps = 47.5f;
        ui.autoSaveEnabled = false;
        ui.autoSaveIntervalSeconds = 11;
        DialogManager dialogs = new DialogManager();

        dialogs.showSettingsDialog();
        DialogManager.GeneralSettingsDraft draft = dialogs.getGeneralSettingsDraft();
        assertEquals(47.5f, draft.defaultFps);
        assertFalse(draft.autoSaveEnabled);
        assertEquals(11, draft.autoSaveIntervalSeconds);

        draft.resetDefaults();
        assertEquals(DirectorConfig.DEFAULT_FRAME_RATE, draft.defaultFps);
        assertEquals(DirectorConfig.DEFAULT_AUTO_SAVE_ENABLED, draft.autoSaveEnabled);
        assertEquals(DirectorConfig.DEFAULT_AUTO_SAVE_INTERVAL_SECONDS, draft.autoSaveIntervalSeconds);
        assertEquals(47.5f, ui.defaultFps);
        assertFalse(ui.autoSaveEnabled);
        assertEquals(11, ui.autoSaveIntervalSeconds);

        draft.defaultFps = Float.POSITIVE_INFINITY;
        assertFalse(dialogs.commitGeneralSettings());
        dialogs.closeSettingsDialog();
        assertNull(dialogs.getGeneralSettingsDraft());

        ui.defaultFps = 55.0f;
        dialogs.showSettingsDialog();
        assertEquals(55.0f, dialogs.getGeneralSettingsDraft().defaultFps);
    }

    @Test
    void layoutPresetsAreScannedOnlyOnOpenOrExplicitRefresh() {
        List<String> diskPresets = new ArrayList<>(List.of("Alpha", "Beta"));
        AtomicInteger scans = new AtomicInteger();
        DialogManager.LayoutAccessor layouts = new DialogManager.LayoutAccessor() {
            @Override
            public boolean saveLayoutPreset(String presetName) {
                if (!diskPresets.contains(presetName)) {
                    diskPresets.add(presetName);
                }
                return true;
            }

            @Override
            public List<String> listLayoutPresets() {
                scans.incrementAndGet();
                return List.copyOf(diskPresets);
            }

            @Override
            public boolean loadLayoutPreset(String presetName) {
                return true;
            }

            @Override
            public boolean deleteLayoutPreset(String presetName) {
                return diskPresets.remove(presetName);
            }
        };
        DialogManager dialogs = new DialogManager();
        dialogs.setLayoutAccessor(layouts);

        dialogs.showLoadLayoutDialog();
        assertEquals(1, scans.get());
        assertEquals(List.of("Alpha", "Beta"), dialogs.cachedLayoutPresets());
        dialogs.cachedLayoutPresets().size();
        assertEquals(1, scans.get());

        dialogs.refreshLayoutPresets();
        assertEquals(2, scans.get());

        assertTrue(dialogs.saveLayoutPreset("Gamma"));
        assertEquals(List.of("Alpha", "Beta", "Gamma"), dialogs.cachedLayoutPresets());
        assertTrue(dialogs.deleteLayoutPreset("Beta"));
        assertEquals(List.of("Alpha", "Gamma"), dialogs.cachedLayoutPresets());
        assertEquals(2, scans.get());

        dialogs.closeLoadLayoutDialog();
        dialogs.showLoadLayoutDialog();
        assertEquals(3, scans.get());
        assertEquals(List.of("Alpha", "Gamma"), dialogs.cachedLayoutPresets());
    }

    private static final class FakeTimelineSettings implements DialogManager.TimelineSettingsAccessor {
        private final TimelineSnapSettings snapSettings = new TimelineSnapSettings();
        private float frameRate = 60.0f;
        private int timeUnit = 1;
        private int commits;

        @Override
        public TimelineSnapSettings getSnapSettings() {
            return snapSettings;
        }

        @Override
        public float getFrameRate() {
            return frameRate;
        }

        @Override
        public int getTimeUnit() {
            return timeUnit;
        }

        @Override
        public boolean commitSettings(float frameRate, int timeUnit, TimelineSnapSettings snapSettings) {
            this.frameRate = frameRate;
            this.timeUnit = timeUnit;
            this.snapSettings.copyFrom(snapSettings);
            commits++;
            return true;
        }
    }
}
