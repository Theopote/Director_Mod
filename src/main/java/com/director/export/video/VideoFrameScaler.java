package com.director.export.video;

import com.director.timeline.model.camera.CameraViewportProfile.PreviewScalePolicy;
import net.minecraft.client.texture.NativeImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryUtil;

/** Native filtered resizing directly into the visible output rectangle, without a full-size intermediate. */
final class VideoFrameScaler {
    private VideoFrameScaler() {}

    record Layout(int cropX, int cropY, int cropWidth, int cropHeight,
            int x, int y, int width, int height, float s0, float t0, float s1, float t1) {}

    static Layout layout(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight,
            float aspect, PreviewScalePolicy policy) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0
                || sourceWidth > 32768 || sourceHeight > 32768 || targetWidth > 8192 || targetHeight > 8192)
            throw new IllegalArgumentException("缩放尺寸无效");
        double cameraAspect = Float.isFinite(aspect) && aspect > 0 ? aspect : targetWidth / (double) targetHeight;
        int cropW = sourceWidth, cropH = sourceHeight;
        if (sourceWidth / (double) sourceHeight > cameraAspect)
            cropW = (int) Math.max(1, Math.min(sourceWidth, Math.round(sourceHeight * cameraAspect)));
        else cropH = (int) Math.max(1, Math.min(sourceHeight, Math.round(sourceWidth / cameraAspect)));
        double virtualW, virtualH;
        boolean fit = policy != PreviewScalePolicy.FILL;
        if ((cameraAspect > targetWidth / (double) targetHeight) == fit) {
            virtualW = targetWidth;
            virtualH = targetWidth / cameraAspect;
        } else {
            virtualH = targetHeight;
            virtualW = targetHeight * cameraAspect;
        }
        double virtualX = Math.floor((targetWidth - virtualW) / 2 + 0.5);
        double virtualY = Math.floor((targetHeight - virtualH) / 2 + 0.5);
        virtualW = Math.max(1, Math.floor(virtualW + 0.5));
        virtualH = Math.max(1, Math.floor(virtualH + 0.5));
        int x = (int) Math.max(0, virtualX), y = (int) Math.max(0, virtualY);
        int right = (int) Math.min(targetWidth, virtualX + virtualW);
        int bottom = (int) Math.min(targetHeight, virtualY + virtualH);
        float s0 = (float) ((x - virtualX) / virtualW), t0 = (float) ((y - virtualY) / virtualH);
        float s1 = (float) ((right - virtualX) / virtualW), t1 = (float) ((bottom - virtualY) / virtualH);
        if (right <= x || bottom <= y || s1 <= s0 || t1 <= t0)
            throw new IllegalArgumentException("相机画幅比例过于极端，无法缩放");
        return new Layout((sourceWidth - cropW) / 2, (sourceHeight - cropH) / 2, cropW, cropH,
                x, y, right - x, bottom - y, s0, t0, s1, t1);
    }

    static NativeImage scale(NativeImage source, int targetWidth, int targetHeight,
            float cameraAspect, PreviewScalePolicy policy) {
        if (source.getFormat() != NativeImage.Format.RGBA || source.imageId() == 0)
            throw new IllegalArgumentException("缩放源必须是未关闭的 RGBA 图像");
        Layout layout = layout(source.getWidth(), source.getHeight(), targetWidth, targetHeight, cameraAspect, policy);
        NativeImage target = new NativeImage(targetWidth, targetHeight, false);
        try {
            int inputStride = source.getWidth() * 4, outputStride = targetWidth * 4;
            long input = source.imageId() + (long) layout.cropY() * inputStride + (long) layout.cropX() * 4;
            long output = target.imageId() + (long) layout.y() * outputStride + (long) layout.x() * 4;
            if (layout.width() != targetWidth || layout.height() != targetHeight)
                fillOpaqueBlack(target, outputStride);
            if (layout.cropWidth() == layout.width() && layout.cropHeight() == layout.height()
                    && layout.s0() == 0 && layout.t0() == 0 && layout.s1() == 1 && layout.t1() == 1) {
                for (int row = 0; row < layout.height(); row++)
                    MemoryUtil.memCopy(input + (long) row * inputStride, output + (long) row * outputStride,
                            (long) layout.width() * 4);
            } else {
                int success = STBImageResize.nstbir_resize_region(input, layout.cropWidth(), layout.cropHeight(), inputStride,
                        output, layout.width(), layout.height(), outputStride, STBImageResize.STBIR_TYPE_UINT8,
                        4, 3, 0, STBImageResize.STBIR_EDGE_CLAMP, STBImageResize.STBIR_EDGE_CLAMP,
                        STBImageResize.STBIR_FILTER_DEFAULT, STBImageResize.STBIR_FILTER_DEFAULT,
                        STBImageResize.STBIR_COLORSPACE_SRGB, 0L, layout.s0(), layout.t0(), layout.s1(), layout.t1());
                if (success == 0) throw new IllegalStateException("原生图像缩放失败");
            }
            return target;
        } catch (RuntimeException | Error failure) {
            target.close();
            throw failure;
        }
    }

    private static void fillOpaqueBlack(NativeImage target, int stride) {
        long first = target.imageId();
        for (int column = 0; column < target.getWidth(); column++) MemoryUtil.memPutInt(first + column * 4L, 0xFF000000);
        for (int row = 1; row < target.getHeight(); row++) MemoryUtil.memCopy(first, first + (long) row * stride, stride);
    }
}
