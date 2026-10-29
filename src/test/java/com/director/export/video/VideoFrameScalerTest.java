package com.director.export.video;

import com.director.timeline.model.camera.CameraViewportProfile.PreviewScalePolicy;
import net.minecraft.client.texture.NativeImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VideoFrameScalerTest {
    @Test void sameSizeCopyPreservesEveryRgbaChannelAndDoesNotCloseSource() {
        try (var source = new NativeImage(4, 4, false)) {
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
                source.setColorArgb(x, y, 0x40000000 | x * 0x330000 | y * 0x4400 | 0x77);
            try (var output = VideoFrameScaler.scale(source, 4, 4, 1, PreviewScalePolicy.FIT)) {
                for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
                    assertEquals(source.getColorArgb(x, y), output.getColorArgb(x, y));
            }
            assertNotEquals(0L, source.imageId());
        }
    }

    @Test void fitKeepsCameraFramingAndWritesOpaqueBlackBarsEvenAtEqualDimensions() {
        try (var source = new NativeImage(8, 8, false)) {
            source.fillRect(0, 0, 8, 8, 0xFF00FF00);
            source.fillRect(0, 0, 8, 2, 0xFFFF0000);
            source.fillRect(0, 6, 8, 2, 0xFFFF0000);
            try (var output = VideoFrameScaler.scale(source, 8, 8, 2, PreviewScalePolicy.FIT)) {
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                    assertEquals(y < 2 || y >= 6 ? 0xFF000000 : 0xFF00FF00, output.getColorArgb(x, y));
            }
        }
    }

    @Test void fillCropsBothCameraFrameAndOutputWithoutBlackBarsOrOutsideColorBleeding() {
        try (var source = new NativeImage(8, 8, false)) {
            source.fillRect(0, 0, 8, 8, 0xFFFF0000);
            source.fillRect(0, 2, 8, 4, 0xFF0000FF);
            try (var output = VideoFrameScaler.scale(source, 8, 8, 2, PreviewScalePolicy.FILL)) {
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                    assertEquals(0xFF0000FF, output.getColorArgb(x, y));
            }
        }
    }

    @Test void downscalingCheckerboardAveragesDetailInsteadOfAliasingToOneColor() {
        try (var source = new NativeImage(32, 32, false)) {
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
                source.setColorArgb(x, y, ((x + y) & 1) == 0 ? 0xFF000000 : 0xFFFFFFFF);
            try (var output = VideoFrameScaler.scale(source, 4, 4, 1, PreviewScalePolicy.FIT)) {
                int value = output.getColorArgb(2, 2);
                int gray = value & 255;
                assertTrue(gray > 160 && gray < 210, "sRGB filtered value=" + gray);
                assertEquals(gray, (value >>> 8) & 255);
                assertEquals(gray, (value >>> 16) & 255);
                assertEquals(255, value >>> 24);
            }
        }
    }

    @Test void upscalingSmoothsColorTransitionsWithoutSwappingRedAndBlue() {
        try (var source = new NativeImage(2, 2, false)) {
            source.fillRect(0, 0, 1, 2, 0xFFFF0000);
            source.fillRect(1, 0, 1, 2, 0xFF0000FF);
            try (var output = VideoFrameScaler.scale(source, 8, 8, 1, PreviewScalePolicy.FIT)) {
                int middle = output.getColorArgb(3, 4);
                assertTrue(((middle >>> 16) & 255) > 0 && (middle & 255) > 0);
                assertTrue(((output.getColorArgb(0, 4) >>> 16) & 255) > 240);
                assertTrue((output.getColorArgb(7, 4) & 255) > 240);
            }
        }
    }

    @Test void layoutClipsPortraitFillToVisibleOutputAndRejectsUnsafeDimensions() {
        var plan = VideoFrameScaler.layout(1920, 1080, 1920, 1080, 9f / 16, PreviewScalePolicy.FILL);
        assertEquals(1920, plan.width()); assertEquals(1080, plan.height());
        assertEquals(0, plan.x()); assertEquals(0, plan.y());
        assertTrue(plan.t0() > 0 && plan.t1() < 1);
        assertThrows(IllegalArgumentException.class,
                () -> VideoFrameScaler.layout(1920, 1080, 100000, 1080, 1, PreviewScalePolicy.FILL));
    }

    @Test void layoutHandlesInvalidAspectAndOnePixelCropWithoutOutOfBoundsCoordinates() {
        for (float aspect : new float[] {0, Float.NaN, Float.POSITIVE_INFINITY, 0.001f, 1000}) {
            var plan = VideoFrameScaler.layout(8, 8, 4, 4, aspect, PreviewScalePolicy.FIT);
            assertTrue(plan.cropWidth() >= 1 && plan.cropHeight() >= 1);
            assertTrue(plan.cropX() + plan.cropWidth() <= 8 && plan.cropY() + plan.cropHeight() <= 8);
            assertTrue(plan.x() >= 0 && plan.y() >= 0 && plan.x() + plan.width() <= 4 && plan.y() + plan.height() <= 4);
        }
    }
    @Test void nativeResizeWritesOnlyVisibleRectangleWithOddSizesAndBothPolicies() {
        try (var source = new NativeImage(9, 13, false)) {
            source.fillRect(0, 0, 9, 13, 0xFF4477AA);
            for (var policy : PreviewScalePolicy.values()) {
                for (float aspect : new float[] {9f / 16, 16f / 9, 1, 21f / 9}) {
                    for (int[] size : new int[][] {{5, 7}, {7, 5}, {1, 1}, {12, 16}}) {
                        var layout = VideoFrameScaler.layout(9, 13, size[0], size[1], aspect, policy);
                        try (var output = VideoFrameScaler.scale(source, size[0], size[1], aspect, policy)) {
                            for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) {
                                boolean inside = x >= layout.x() && x < layout.x() + layout.width()
                                        && y >= layout.y() && y < layout.y() + layout.height();
                                assertEquals(inside ? 0xFF4477AA : 0xFF000000, output.getColorArgb(x, y),
                                        policy + " " + aspect + " " + size[0] + "x" + size[1] + " @" + x + "," + y);
                            }
                        }
                    }
                }
            }
        }
    }

}
