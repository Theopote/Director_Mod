package com.director.export.video;

import com.director.timeline.model.camera.CameraViewportProfile.PreviewScalePolicy;
import net.minecraft.client.texture.NativeImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapturedVideoFrameTest {
    @Test void matchingFrameTransfersOriginalAndWrapperNoLongerOwnsIt() {
        var source = new NativeImage(8, 8, true);
        try (var capture = new CapturedVideoFrame(source, 8, 8, 1, PreviewScalePolicy.FIT)) {
            try (var result = capture.takeImage()) {
                assertSame(source, result);
                capture.close();
                assertNotEquals(0L, result.imageId());
                assertThrows(IllegalStateException.class, capture::takeImage);
            }
        }
        assertEquals(0L, source.imageId());
    }

    @Test void filteringReleasesOriginalAndTransfersIndependentOutput() {
        var source = new NativeImage(8, 8, false);
        source.fillRect(0, 0, 8, 8, 0xFF112233);
        try (var capture = new CapturedVideoFrame(source, 4, 4, 1, PreviewScalePolicy.FIT);
             var result = capture.takeImage()) {
            assertNotSame(source, result);
            assertEquals(0L, source.imageId());
            capture.close();
            assertEquals(0xFF112233, result.getColorArgb(2, 2));
        }
    }

    @Test void failedScalingStillReleasesOriginalScreenshot() {
        var source = new NativeImage(8, 8, true);
        try (var capture = new CapturedVideoFrame(source, 100000, 4, 1, PreviewScalePolicy.FIT)) {
            assertThrows(IllegalArgumentException.class, capture::takeImage);
            assertEquals(0L, source.imageId());
        }
    }

    @Test void abandonedOrLateScreenshotCanBeClosedIdempotentlyWithoutFiltering() {
        var source = new NativeImage(8, 8, true);
        var capture = new CapturedVideoFrame(source, 4, 4, 1, PreviewScalePolicy.FIT);
        capture.close(); capture.close();
        assertEquals(0L, source.imageId());
        assertThrows(IllegalStateException.class, capture::takeImage);
    }
}
