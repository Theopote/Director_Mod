package com.director.export.video;

import net.minecraft.client.texture.NativeImage;

import java.io.IOException;

/**
 * 视频编码器接口
 *
 * <p>编码器负责把逐帧渲染得到的 {@link NativeImage} 写出到磁盘。</p>
 *
 * <p>当前实现：{@link PngSequenceEncoder}（PNG 序列）与 {@link FFmpegVideoEncoder}（H.264 MP4）。</p>
 */
public interface VideoEncoder {

    /**
     * 初始化编码器。
     *
     * @param settings 导出设置
     * @param totalFrames 总帧数（用于生成文件名宽度/预分配等）
     */
    void init(VideoExportSettings settings, int totalFrames) throws IOException;

    /**
     * 写入一帧。
     *
     * @param image RGBA 图像（调用方仍负责 close）
     * @param frameIndex 当前帧索引（0-based）
     */
    void encodeFrame(NativeImage image, int frameIndex) throws IOException;

    /**
     * 收尾并释放资源。
     */
    void finish() throws IOException;

    /** Release partial output after cancellation or failure, on the export worker. */
    void abort();
}


