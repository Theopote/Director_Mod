package com.director.export.video;

import net.minecraft.client.texture.NativeImage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * PNG 序列编码器（零依赖，可直接使用）
 *
 * <p>会根据 {@link VideoExportSettings#outputPath} 生成一个同名目录，然后输出：</p>
 * <pre>
 * frame_00001.png
 * frame_00002.png
 * ...
 * </pre>
 *
 * <p>例如 outputPath = "./video/output.mp4"，则输出目录为 "./video/output/"。</p>
 */
public final class PngSequenceEncoder implements VideoEncoder {

    private StagedVideoOutput output;
    private Path outputDir;
    private int expectedFrames;
    private int encodedFrames;
    private int padWidth = 5;

    @Override
    public void init(VideoExportSettings settings, int totalFrames) throws IOException {
        if (settings == null || settings.outputPath == null || settings.outputPath.isBlank()) {
            throw new IOException("outputPath 为空");
        }

        if (output != null) throw new IOException("编码器不能重复初始化");
        if (settings.getOutputPathAsPath() == null || totalFrames <= 0) throw new IOException("输出路径或总帧数无效");
        output = StagedVideoOutput.sequence(StagedVideoOutput.sequenceTarget(settings.getOutputPathAsPath()));
        outputDir = output.path();
        expectedFrames = totalFrames;

        // 根据总帧数决定补零宽度（至少 5）
        int digits = Integer.toString(Math.max(1, totalFrames)).length();
        padWidth = Math.max(5, digits);
    }

    @Override
    public void encodeFrame(NativeImage image, int frameIndex) throws IOException {
        if (outputDir == null) {
            throw new IOException("PngSequenceEncoder 未初始化");
        }
        if (image == null) {
            throw new IOException("image == null");
        }

        if (frameIndex != encodedFrames || encodedFrames >= expectedFrames) throw new IOException("PNG 帧序号不连续");
        String fileName = String.format(java.util.Locale.ROOT, "frame_%0" + padWidth + "d.png", frameIndex + 1);
        Path outFile = outputDir.resolve(fileName);
        image.writeTo(outFile.toFile());
        encodedFrames++;
    }

    @Override
    public void finish() throws IOException {
        if (output == null || encodedFrames != expectedFrames) throw new IOException("PNG 序列帧数不完整");
        output.commit();
    }

    @Override public void abort() {
        if (output != null) {
            try { output.close(); }
            catch (IOException error) {
                org.slf4j.LoggerFactory.getLogger(PngSequenceEncoder.class)
                        .warn("[VideoExport] 临时 PNG 序列清理失败", error);
            }
        }
    }
}
