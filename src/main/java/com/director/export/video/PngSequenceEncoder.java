package com.director.export.video;

import net.minecraft.client.texture.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    private Path outputDir;
    private int padWidth = 5;

    @Override
    public void init(VideoExportSettings settings, int totalFrames) throws IOException {
        if (settings == null || settings.outputPath == null || settings.outputPath.isBlank()) {
            throw new IOException("outputPath 为空");
        }

        Path outPath = Paths.get(settings.outputPath.trim());
        Path parent = outPath.getParent();
        if (parent == null) {
            parent = Paths.get(".");
        }

        String baseName = stripExtension(outPath.getFileName() != null ? outPath.getFileName().toString() : "export");
        outputDir = parent.resolve(baseName);

        Files.createDirectories(outputDir);

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

        String fileName = String.format("frame_%0" + padWidth + "d.png", frameIndex + 1);
        Path outFile = outputDir.resolve(fileName);
        image.writeTo(outFile.toFile());
    }

    @Override
    public void finish() {
        // PNG 序列无需额外收尾
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "export";
        }
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(0, idx) : name;
    }
}


