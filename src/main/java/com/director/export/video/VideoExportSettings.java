package com.director.export.video;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 🎥 VideoExportSettings - 视频导出设置（UI 可直接绑定）
 *
 * <p>承载导出对话框字段与基础校验。编码由 {@link FFmpegVideoEncoder}（H.264 MP4）
 * 或 {@link PngSequenceEncoder}（PNG 序列）消费。</p>
 */
public class VideoExportSettings implements Cloneable {

    // ==================== 输出 ====================

    /** 输出文件路径（建议 .mp4）。由对话框写入。 */
    public String outputPath = "";

    // ==================== 分辨率 / FPS ====================

    public int width = 1920;
    public int height = 1080;
    public int fps = 30;

    /** Opt-in until native-resolution rendering has been validated on the client GPU. */
    public boolean nativeResolution = false;

    // ==================== 导出范围 ====================

    public static final int RANGE_TIMELINE = 0;
    public static final int RANGE_CUSTOM = 1;

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

    // ==================== 编码参数（MP4 / x264）====================

    /**
     * x264 preset：影响编码速度 vs 压缩效率（不直接等同“画质”）。
     * 常用：ultrafast / fast / medium / slow
     */
    public String x264Preset = "slow";

    /**
     * x264 CRF：影响质量 vs 码率/体积。越小越清晰、文件越大、编码更慢。
     * 常用范围：16 - 28；默认 18 ~ 23。
     */
    public int x264Crf = 18;

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

    public VideoExportSettings copy() {
        try {
            return (VideoExportSettings) clone();
        } catch (CloneNotSupportedException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static int frameCount(float start, float end, int fps) {
        double exactFrames = ((double) end - start) * fps;
        // Float timeline endpoints can lie just above an exact frame boundary.
        double nearest = Math.rint(exactFrames);
        double frames = Math.abs(exactFrames - nearest) <= 0.00001 ? nearest : Math.ceil(exactFrames);
        if (!Float.isFinite(start) || !Float.isFinite(end) || start < 0 || end <= start
                || fps < 1 || fps > 240 || frames > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("导出时间范围或总帧数无效");
        }
        return Math.max(1, (int) frames);
    }

    public boolean validate() {
        return validationError() == null;
    }

    /** A user-facing reason, or null when the settings can be submitted. */
    public String validationError() {
        if (width < 16 || height < 16 || width > 8192 || height > 8192)
            return "分辨率宽高须在 16–8192 像素之间";
        if (fps < 1 || fps > 240) return "FPS 须在 1–240 之间";
        if (format != FORMAT_H264_MP4 && format != FORMAT_PNG_SEQUENCE) return "不支持的导出格式";
        if (rangeMode != RANGE_TIMELINE && rangeMode != RANGE_CUSTOM) return "不支持的时间范围模式";
        if (cameraMode != CAMERA_PLAYER && cameraMode != CAMERA_TRACK) return "不支持的摄像机模式";
        if (rangeMode == RANGE_CUSTOM) {
            try {
                frameCount(customStartTime, customEndTime, fps);
            } catch (IllegalArgumentException invalid) {
                return invalid.getMessage();
            }
        }
        if (format == FORMAT_H264_MP4) {
            if ((width & 1) != 0 || (height & 1) != 0) return "MP4 的宽度和高度须为偶数";
            if (x264Crf < 0 || x264Crf > 51) return "CRF 须在 0–51 之间";
            if (x264Preset == null || !java.util.Set.of("ultrafast", "superfast", "veryfast", "faster", "fast",
                    "medium", "slow", "slower", "veryslow", "placebo")
                    .contains(x264Preset.trim().toLowerCase(java.util.Locale.ROOT))) return "不支持的 x264 preset";
            if (enableAudio) {
                if (audioSampleRate < 8000 || audioSampleRate > 192000) return "音频采样率须在 8000–192000 Hz 之间";
                if (audioChannels < 1 || audioChannels > 2) return "音频仅支持单声道或双声道";
            }
        }
        Path path = getOutputPathAsPath();
        if (path == null || path.getFileName() == null) return "请指定有效的输出路径";
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (format == FORMAT_H264_MP4 && !name.endsWith(".mp4")) return "MP4 输出路径须以 .mp4 结尾";
        int dot = name.lastIndexOf('.');
        if (format == FORMAT_PNG_SEQUENCE && (name.isBlank() || dot == 0 || name.equals("..")))
            return "请指定 PNG 序列的有效目录名称";
        return null;
    }
}
