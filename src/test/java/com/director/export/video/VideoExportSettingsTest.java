package com.director.export.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VideoExportSettingsTest {
    @Test void stillHasExactTimeAndIndependentPngValidation() {
        var settings=settings();settings.format=VideoExportSettings.FORMAT_PNG_STILL;
        settings.outputPath="frame.png";settings.width=1919;settings.stillTime=123.456f;
        settings.rangeMode=VideoExportSettings.RANGE_CUSTOM;settings.customEndTime=0;
        settings.x264Preset="unused";settings.audioChannels=0;
        assertTrue(settings.validate());assertEquals(123.456f,settings.copy().stillTime);
        settings.outputPath="frame.mp4";assertFalse(settings.validate());settings.outputPath="frame.png";
        for(float time:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY}) {settings.stillTime=time;assertFalse(settings.validate());}
    }
    private VideoExportSettings settings() {
        VideoExportSettings settings = new VideoExportSettings();
        settings.outputPath = "output.mp4";
        return settings;
    }

    @Test void rejectsOddMp4DimensionsButAllowsOddPngDimensions() {
        var settings = settings();
        settings.width = 1919;
        assertFalse(settings.validate());
        assertTrue(settings.validationError().contains("偶数"));
        settings.format = VideoExportSettings.FORMAT_PNG_SEQUENCE;
        assertTrue(settings.validate());
    }

    @Test void rejectsNonFiniteEmptyReversedAndExcessiveRanges() {
        var settings = settings();
        settings.rangeMode = VideoExportSettings.RANGE_CUSTOM;
        for (float end : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 0, -1, Float.MAX_VALUE}) {
            settings.customEndTime = end;
            assertFalse(settings.validate(), "end=" + end);
        }
        settings.customEndTime = 10;
        settings.customStartTime = Float.NaN;
        assertFalse(settings.validate());
    }

    @Test void rejectsUnsafeDimensionsAndUnknownModesAndPresets() {
        var settings = settings();
        settings.width = 32768;
        assertFalse(settings.validate());
        settings.width = 1920;
        settings.format = 9;
        assertFalse(settings.validate());
        settings.format = VideoExportSettings.FORMAT_H264_MP4;
        settings.x264Preset = "typo";
        assertFalse(settings.validate());
        settings.x264Preset = " MEDIUM ";
        assertTrue(settings.validate());
    }

    @Test void pngCannotResolveItsBasenameToTheParentDirectory() {
        var settings = settings();
        settings.format = VideoExportSettings.FORMAT_PNG_SEQUENCE;
        for (String path : new String[] {".png", "..", "/"}) {
            settings.outputPath = path;
            assertFalse(settings.validate(), path);
        }
    }

    @Test void snapshotIsIndependentOfSubsequentDialogEdits() {
        var settings = settings();
        settings.nativeResolution = true;
        settings.x264Preset = "fast";
        settings.x264Crf = 24;
        var snapshot = settings.copy();
        settings.width = 1280;
        settings.outputPath = "other.mp4";
        settings.enableAudio = false;
        assertEquals(1920, snapshot.width);
        assertEquals("output.mp4", snapshot.outputPath);
        assertTrue(snapshot.enableAudio);
        assertTrue(snapshot.nativeResolution);
        assertEquals("fast", snapshot.x264Preset);
        assertEquals(24, snapshot.x264Crf);
    }

    @Test void frameCountsCoverFractionalFramesWithoutFloatBoundaryExtras() {
        assertEquals(3, VideoExportSettings.frameCount(0, 0.1f, 30));
        assertEquals(30, VideoExportSettings.frameCount(0, 1f, 30));
        assertEquals(31, VideoExportSettings.frameCount(0, 1.01f, 30));
        assertEquals(1, VideoExportSettings.frameCount(0, 0.001f, 30));
        assertThrows(IllegalArgumentException.class, () -> VideoExportSettings.frameCount(0, Float.MAX_VALUE, 30));
    }
}
