package com.director.ui.timeline.state;

import com.director.config.DirectorConfig;
import com.director.timeline.model.TimelineModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSaveManagerTest {
    private final DirectorConfig.ConfigData.UISettings ui = DirectorConfig.getConfig().ui;
    private boolean previousAutoSaveEnabled;
    private int previousAutoSaveIntervalSeconds;

    @BeforeEach
    void captureConfig() {
        previousAutoSaveEnabled = ui.autoSaveEnabled;
        previousAutoSaveIntervalSeconds = ui.autoSaveIntervalSeconds;
    }

    @AfterEach
    void restoreConfig() {
        ui.autoSaveEnabled = previousAutoSaveEnabled;
        ui.autoSaveIntervalSeconds = previousAutoSaveIntervalSeconds;
    }

    @Test
    void tickUsesLatestConfigButFlushNowIgnoresAutoSaveSwitch() throws Exception {
        TestTimelineModel model = new TestTimelineModel();
        AutoSaveManager autoSave = new AutoSaveManager(model);
        setLastSaveTime(autoSave, System.currentTimeMillis() - 10_000L);

        ui.autoSaveEnabled = true;
        ui.autoSaveIntervalSeconds = 3600;
        autoSave.markDirty();
        autoSave.tick();
        assertEquals(0, model.saveCalls);

        ui.autoSaveIntervalSeconds = 1;
        autoSave.tick();
        assertEquals(1, model.saveCalls);

        ui.autoSaveEnabled = false;
        autoSave.markDirty();
        setLastSaveTime(autoSave, System.currentTimeMillis() - 10_000L);
        autoSave.tick();
        assertEquals(1, model.saveCalls);

        assertTrue(autoSave.flushNow());
        assertEquals(2, model.saveCalls);
    }

    @Test
    void failedSaveKeepsDirtyStateForManualFlush() throws Exception {
        TestTimelineModel model = new TestTimelineModel();
        model.saveResult = false;
        AutoSaveManager autoSave = new AutoSaveManager(model);
        setLastSaveTime(autoSave, System.currentTimeMillis() - 10_000L);
        ui.autoSaveEnabled = true;
        ui.autoSaveIntervalSeconds = 1;

        autoSave.markDirty();
        autoSave.tick();
        assertEquals(1, model.saveCalls);
        assertFalse(autoSave.flushNow());
        assertEquals(2, model.saveCalls);

        model.saveResult = true;
        assertTrue(autoSave.flushNow());
        assertEquals(3, model.saveCalls);
    }

    private static void setLastSaveTime(AutoSaveManager autoSave, long value) throws Exception {
        Field field = AutoSaveManager.class.getDeclaredField("lastSaveTime");
        field.setAccessible(true);
        field.setLong(autoSave, value);
    }

    private static final class TestTimelineModel extends TimelineModel {
        private boolean saveResult = true;
        private int saveCalls;

        private TestTimelineModel() {
            super(null);
        }

        @Override
        public boolean save() {
            saveCalls++;
            return saveResult;
        }
    }
}
