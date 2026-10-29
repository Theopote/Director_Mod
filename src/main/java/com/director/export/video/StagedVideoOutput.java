package com.director.export.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes beside the destination so cancellation never truncates the last completed export. */
final class StagedVideoOutput implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StagedVideoOutput.class);
    private final Path target;
    private final Path staging;
    private final boolean sequence;
    private boolean committed;

    private StagedVideoOutput(Path target, boolean sequence) throws IOException {
        this.target = target.toAbsolutePath().normalize();
        this.sequence = sequence;
        if (this.target.getParent() == null) throw new IOException("输出路径不能是根目录");
        Files.createDirectories(this.target.getParent());
        validateTarget();
        staging = sequence
                ? Files.createTempDirectory(this.target.getParent(), ".director-frames-")
                : Files.createTempFile(this.target.getParent(), ".director-video-", ".mp4");
    }

    static StagedVideoOutput video(Path target) throws IOException {
        return new StagedVideoOutput(target, false);
    }

    static StagedVideoOutput sequence(Path target) throws IOException {
        return new StagedVideoOutput(target, true);
    }

    static Path sequenceTarget(Path requested) throws IOException {
        Path name = requested.getFileName();
        if (name == null) throw new IOException("PNG 序列路径无效");
        String base = name.toString();
        int dot = base.lastIndexOf('.');
        if (dot >= 0) base = base.substring(0, dot);
        if (base.isBlank() || base.equals(".") || base.equals("..")) throw new IOException("PNG 序列目录名称无效");
        return requested.resolveSibling(base);
    }

    Path path() { return staging; }

    private void validateTarget() throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        if (sequence) validateFramesDirectory(target);
        else if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("MP4 输出目标不是普通文件：" + target);
    }

    private static void validateFramesDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("PNG 序列输出目标不是普通目录：" + directory);
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (!file.getFileName().toString().matches("frame_[0-9]+\\.png")
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("PNG 输出目录含非导出帧，请选择其他目录：" + file);
            }
        }
    }

    void commit() throws IOException {
        if (committed) throw new IOException("导出结果已经提交");
        validateTarget();
        if (!sequence) {
            if (Files.size(staging) == 0) throw new IOException("编码器未生成视频文件");
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                committed = true;
                return;
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Same-directory fallback with rollback also supports nonempty sequence directories.
            }
        }
        Path backup = null;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            backup = Files.createTempFile(target.getParent(), ".director-previous-", ".backup");
            Files.delete(backup);
            Files.move(target, backup);
        }
        try {
            Files.move(staging, target);
            committed = true;
        } catch (IOException failure) {
            if (backup != null) {
                try {
                    Files.move(backup, target);
                } catch (IOException rollback) {
                    failure.addSuppressed(new IOException("原导出结果保留在：" + backup, rollback));
                }
            }
            throw failure;
        }
        if (backup != null) {
            try {
                deleteOwnedOutput(backup);
            } catch (IOException cleanup) {
                LOGGER.warn("[VideoExport] 新结果已保存，旧结果备份未能清理：{}", backup, cleanup);
            }
        }
    }

    private void deleteOwnedOutput(Path path) throws IOException {
        if (sequence && Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            validateFramesDirectory(path);
            try (var files = Files.list(path)) {
                for (Path file : files.toList()) Files.delete(file);
            }
        }
        Files.deleteIfExists(path);
    }

    @Override public void close() throws IOException {
        if (!committed) deleteOwnedOutput(staging);
    }
}
