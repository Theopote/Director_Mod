package com.chronoblocks.export.video;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 🎥 VideoExportSettings - 视频导出设置（UI 可直接绑定）
 *
 * <p>目前仅提供 UI 所需字段 + 基础校验。后续接入离屏渲染与 FFmpeg 编码时，可在此补充：
 * bitRate、preset、crf、编码器选择、PNG 序列等。</p>
 */
public class VideoExportSettings {

    // ==================== 输出 ====================

    /** 输出文件路径（建议 .mp4）。由对话框写入。 */
    public String outputPath = "";

    // ==================== 分辨率 / FPS ====================

    public int width = 1920;
    public int height = 1080;
    public int fps = 30;

    // ==================== 导出范围 ====================

    public static final int RANGE_TIMELINE = 0;
    public static final int RANGE_CUSTOM = 1;
    public static final int RANGE_PROJECT = 2;

    public int rangeMode = RANGE_TIMELINE;
    public float customStartTime = 0f;
    public float customEndTime = 10f;

    // ==================== 摄像机来源 ====================

    public static final int CAMERA_PLAYER = 0;
    public static final int CAMERA_TRACK = 1;

    public int cameraMode = CAMERA_PLAYER;

    /** UI 下拉选择的“摄像机轨道”索引（相对于仅 CAMERA 轨道的列表）。 */
    public int cameraTrackIndex = 0;

    /** 可选：存储轨道名（更稳定）。 */
    public String cameraTrackName = "";

    // ==================== 编码 / 格式 ====================

    public static final int FORMAT_H264_MP4 = 0;
    public static final int FORMAT_PNG_SEQUENCE = 1;

    public int format = FORMAT_H264_MP4;

    // ==================== 质量（先做 UI 字段）====================

    public int renderDistance = 12;
    public boolean useAntiAliasing = true;

    // ==================== 音频（WAV -> FFmpeg 混流）====================

    /** 是否导出音频并混入 MP4（仅 MP4 有意义，PNG 序列会忽略） */
    public boolean enableAudio = true;

    /** WAV 采样率（Hz） */
    public int audioSampleRate = 48000;

    /** 声道数（1=mono, 2=stereo） */
    public int audioChannels = 2;

    // ==================== 校验与派生 ====================

    public Path getOutputPathAsPath() {
        try {
            if (outputPath == null) {
                return null;
            }
            String trimmed = outputPath.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return Paths.get(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validate() {
        if (width < 16 || height < 16) {
            return false;
        }
        if (fps < 1 || fps > 240) {
            return false;
        }
        if (audioSampleRate < 8000 || audioSampleRate > 192000) {
            return false;
        }
        if (audioChannels < 1 || audioChannels > 2) {
            return false;
        }
        if (rangeMode == RANGE_CUSTOM) {
            if (customStartTime < 0f) {
                return false;
            }
            if (customEndTime < customStartTime) {
                return false;
            }
        }
        Path p = getOutputPathAsPath();
        if (p == null) {
            return false;
        }
        String name = p.getFileName() != null ? p.getFileName().toString().toLowerCase() : "";
        if (format == FORMAT_H264_MP4) {
            // UI 里 MP4 仍要求用户给出 .mp4（后续接入 FFmpeg 时可直接复用）
            if (!name.endsWith(".mp4")) {
                return false;
            }
        } else if (format == FORMAT_PNG_SEQUENCE) {
            // PNG 序列：允许任意输出路径（通常用户仍会填 .mp4 作为“基名”）
            if (name.isBlank()) {
                return false;
            }
        }
        return true;
    }
}


