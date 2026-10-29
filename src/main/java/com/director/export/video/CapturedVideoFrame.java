package com.director.export.video;

import com.director.timeline.model.camera.CameraViewportProfile.PreviewScalePolicy;
import net.minecraft.client.texture.NativeImage;

/** Raw screenshot plus immutable framing; filtering happens on the export worker after handoff. */
final class CapturedVideoFrame implements AutoCloseable {
    private NativeImage image;
    private final int width, height;
    private final float aspect;
    private final PreviewScalePolicy policy;

    CapturedVideoFrame(NativeImage image, int width, int height, float aspect, PreviewScalePolicy policy) {
        this.image = java.util.Objects.requireNonNull(image);
        this.width = width;
        this.height = height;
        this.aspect = aspect;
        this.policy = policy;
    }

    NativeImage takeImage() {
        NativeImage source = image;
        if (source == null) throw new IllegalStateException("抓帧图像已被转移或关闭");
        image = null;
        if (source.getWidth() == width && source.getHeight() == height
                && (!Float.isFinite(aspect) || aspect <= 0 || Math.abs(aspect - width / (float) height) < 0.0001f))
            return source;
        try (source) {
            return VideoFrameScaler.scale(source, width, height, aspect, policy);
        }
    }

    @Override public void close() {
        if (image != null) {
            image.close();
            image = null;
        }
    }
}
