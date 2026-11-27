package com.director.timeline.model;

import com.director.timeline.snapping.TimelineSnapSettings;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineSettingsTest {
    @Test
    void applySettingsCommitsAllValuesAndNotifiesOnce() {
        TestTimelineModel model = new TestTimelineModel();
        TimelineSnapSettings liveSnapSettings = model.getSnapSettings();
        AtomicInteger notifications = new AtomicInteger();
        model.addModelChangeListener(changeType -> notifications.incrementAndGet());
        TimelineSnapSettings draft = new TimelineSnapSettings();
        draft.setEnabled(false);
        draft.setGridInterval(0.25f);
        draft.setPixelThreshold(14.0f);

        assertTrue(model.applySettings(48.0f, 2, draft));

        assertEquals(48.0f, model.getFrameRate());
        assertEquals(2, model.getTimeUnit());
        assertSame(liveSnapSettings, model.getSnapSettings());
        assertFalse(model.getSnapSettings().isEnabled());
        assertEquals(0.25f, model.getSnapSettings().getGridInterval());
        assertEquals(14.0f, model.getSnapSettings().getPixelThreshold());
        assertEquals(1, notifications.get());
        assertEquals(1, model.saveCalls);
    }

    @Test
    void invalidSettingsLeaveAllLiveValuesUnchanged() {
        TestTimelineModel model = new TestTimelineModel();
        AtomicInteger notifications = new AtomicInteger();
        model.addModelChangeListener(changeType -> notifications.incrementAndGet());
        float originalFrameRate = model.getFrameRate();
        int originalTimeUnit = model.getTimeUnit();
        float originalInterval = model.getSnapSettings().getGridInterval();

        assertFalse(model.applySettings(Float.NaN, 2, new TimelineSnapSettings()));
        assertFalse(model.applySettings(60.0f, 3, new TimelineSnapSettings()));
        assertFalse(model.applySettings(60.0f, 2, null));

        TimelineSnapSettings invalidSnapSettings = new TimelineSnapSettings();
        invalidSnapSettings.setPixelThreshold(Float.NaN);
        assertFalse(model.applySettings(60.0f, 2, invalidSnapSettings));

        assertEquals(originalFrameRate, model.getFrameRate());
        assertEquals(originalTimeUnit, model.getTimeUnit());
        assertEquals(originalInterval, model.getSnapSettings().getGridInterval());
        assertEquals(0, notifications.get());
        assertEquals(0, model.saveCalls);
    }

    private static final class TestTimelineModel extends TimelineModel {
        private int saveCalls;

        private TestTimelineModel() {
            super(null);
        }

        @Override
        public boolean save() {
            saveCalls++;
            return true;
        }
    }
}
