package com.chronoblocks.ui.video;

import net.fabricmc.loader.api.FabricLoader;
import com.chronoblocks.export.video.VideoExportSettings;
import com.chronoblocks.ui.ChronoBlocksUIManager;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * 🎥 VideoExportDialog - 视频导出对话框（遵循 ChronoBlocks 现有 Popup Dialog 风格）
 *
 * <p>使用 open() 设置 shouldOpen 标记；在 UIManager 每帧调用 renderIfNeeded() 即可。</p>
 */
public final class VideoExportDialog {

    private static boolean shouldOpen = false;
    private static final VideoExportSettings settings = new VideoExportSettings();

    // UI buffers
    private static final ImString outputPathBuffer = new ImString(512);
    private static final ImInt widthInt = new ImInt(settings.width);
    private static final ImInt heightInt = new ImInt(settings.height);
    private static final ImInt fpsInt = new ImInt(settings.fps);
    private static final ImInt formatInt = new ImInt(settings.format);
    private static final ImInt rangeModeInt = new ImInt(settings.rangeMode);
    private static final ImFloat rangeStartFloat = new ImFloat(settings.customStartTime);
    private static final ImFloat rangeEndFloat = new ImFloat(settings.customEndTime);
    private static final ImInt cameraModeInt = new ImInt(settings.cameraMode);
    private static final ImInt cameraTrackIndexInt = new ImInt(settings.cameraTrackIndex);
    private static final ImInt renderDistanceInt = new ImInt(settings.renderDistance);
    private static final ImBoolean audioEnabledBool = new ImBoolean(settings.enableAudio);
    private static String lastError = null;

    // FFmpeg 状态缓存（仅用于 UI 展示/校验）
    private static boolean ffmpegChecked = false;
    private static boolean ffmpegOk = false;
    private static String ffmpegPath = null;
    private static String ffmpegError = null;

    private VideoExportDialog() {}

    public static void open() {
        shouldOpen = true;
        lastError = null;
        ffmpegChecked = false;
        ffmpegOk = false;
        ffmpegPath = null;
        ffmpegError = null;
        // 默认填充：如果之前设置过 outputPath，则带回
        if (settings.outputPath != null && !settings.outputPath.isBlank()) {
            outputPathBuffer.set(settings.outputPath);
        } else {
            outputPathBuffer.set("");
        }
    }

    public static void renderIfNeeded(ChronoBlocksUIManager uiManager) {
        final String popupId = Icons.VIDEO + " 视频导出设置##VideoExportDialog";

        if (shouldOpen) {
            ImGui.openPopup(popupId);
            shouldOpen = false;
        }

        // 居中（与 ContainerDialogs 类似）
        var center = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(center.x, center.y, ImGuiCond.Appearing, 0.5f, 0.5f);

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            renderBody(uiManager);
            ImGui.endPopup();
        } else {
            // 如果 popup 已关闭，清掉错误（避免下次打开残留）
            lastError = null;
        }
    }

    private static void renderBody(ChronoBlocksUIManager uiManager) {
        // 每帧将 settings 同步到 Im*，确保 UI 切换时值正确
        widthInt.set(settings.width);
        heightInt.set(settings.height);
        fpsInt.set(settings.fps);
        formatInt.set(settings.format);
        rangeModeInt.set(settings.rangeMode);
        rangeStartFloat.set(settings.customStartTime);
        rangeEndFloat.set(settings.customEndTime);
        cameraModeInt.set(settings.cameraMode);
        cameraTrackIndexInt.set(settings.cameraTrackIndex);
        renderDistanceInt.set(settings.renderDistance);
        audioEnabledBool.set(settings.enableAudio);

        // =============================
        // 输出文件
        // =============================
        UITheme.title("输出");
        ImGui.separator();

        ImGui.pushItemWidth(UITheme.Spacing.INPUT_WIDTH_LARGE);
        String outputHint = (settings.format == VideoExportSettings.FORMAT_H264_MP4) ? "输出文件 (.mp4)" : "输出基名（用于 PNG 序列目录命名）";
        ImGui.inputText(outputHint, outputPathBuffer, ImGuiInputTextFlags.ReadOnly);
        ImGui.popItemWidth();

        ImGui.sameLine();
        if (ImGui.button("选择...")) {
            openOutputPathDialog();
        }

        UITheme.spacingSmall();

        // =============================
        // 分辨率 / FPS
        // =============================
        UITheme.title("视频参数");
        ImGui.separator();

        ImGui.inputInt("宽度", widthInt);
        ImGui.inputInt("高度", heightInt);
        ImGui.sliderInt("FPS", fpsInt.getData(), 1, 240);

        settings.width = Math.max(16, widthInt.get());
        settings.height = Math.max(16, heightInt.get());
        settings.fps = Math.max(1, Math.min(240, fpsInt.get()));

        UITheme.spacingSmall();

        // =============================
        // 格式
        // =============================
        String[] formatOptions = { "H.264 (MP4)", "PNG 序列（预留）" };
        if (ImGui.combo("导出格式", formatInt, formatOptions, formatOptions.length)) {
            settings.format = formatInt.get();
        }
        ImGui.textDisabled("提示：MP4 依赖 ffmpeg（优先 -Dchronoblocks.ffmpeg，其次放在游戏根目录，最后系统 PATH）。PNG 序列为零依赖。");

        if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
            renderFFmpegStatus();
        }

        UITheme.spacingSmall();

        // =============================
        // 导出范围
        // =============================
        UITheme.title("导出范围");
        ImGui.separator();

        String[] rangeOptions = { "使用时间线范围（预留）", "自定义区间", "整个工程（预留）" };
        if (ImGui.combo("范围模式", rangeModeInt, rangeOptions, rangeOptions.length)) {
            settings.rangeMode = rangeModeInt.get();
        }

        if (settings.rangeMode == VideoExportSettings.RANGE_CUSTOM) {
            ImGui.inputFloat("开始时间 (s)", rangeStartFloat, 0.1f, 1.0f, "%.2f");
            ImGui.inputFloat("结束时间 (s)", rangeEndFloat, 0.1f, 1.0f, "%.2f");
            settings.customStartTime = Math.max(0f, rangeStartFloat.get());
            settings.customEndTime = Math.max(settings.customStartTime, rangeEndFloat.get());
        } else {
            ImGui.textDisabled("该范围模式将使用当前工程的时长（来自 PlaybackController）。");
        }

        UITheme.spacingSmall();

        // =============================
        // 摄像机
        // =============================
        UITheme.title("摄像机");
        ImGui.separator();

        String[] cameraOptions = { "玩家摄像机", "时间线摄像机轨道" };
        if (ImGui.combo("来源", cameraModeInt, cameraOptions, cameraOptions.length)) {
            settings.cameraMode = cameraModeInt.get();
        }

        List<TimelineTrack> cameraTracks = getCameraTracks();
        if (settings.cameraMode == VideoExportSettings.CAMERA_TRACK) {
            if (cameraTracks.isEmpty()) {
                ImGui.textColored(1.0f, 0.6f, 0.2f, 1.0f,
                        Icons.WARNING + " 当前工程没有摄像机轨道（TrackType.CAMERA）");
                settings.cameraTrackIndex = 0;
                settings.cameraTrackName = "";
            } else {
                String[] trackNames = cameraTracks.stream()
                        .map(t -> t.getName() != null ? t.getName() : "(未命名轨道)")
                        .toArray(String[]::new);
                cameraTrackIndexInt.set(Math.max(0, Math.min(settings.cameraTrackIndex, trackNames.length - 1)));
                if (ImGui.combo("轨道", cameraTrackIndexInt, trackNames, trackNames.length)) {
                    settings.cameraTrackIndex = cameraTrackIndexInt.get();
                } else {
                    settings.cameraTrackIndex = cameraTrackIndexInt.get();
                }
                settings.cameraTrackName = trackNames[settings.cameraTrackIndex];
            }
        }

        UITheme.spacingSmall();

        // =============================
        // 渲染质量（简化）
        // =============================
        UITheme.title("渲染质量");
        ImGui.separator();

        ImGui.sliderInt("渲染距离", renderDistanceInt.getData(), 2, 32);
        settings.renderDistance = renderDistanceInt.get();

        boolean aa = settings.useAntiAliasing;
        if (ImGui.checkbox("开启抗锯齿（预留）", aa)) {
            settings.useAntiAliasing = !aa;
        }

        UITheme.spacingSmall();

        // =============================
        // 音频（MP4）
        // =============================
        if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
            UITheme.title("音频");
            ImGui.separator();

            if (ImGui.checkbox("导出音频并混入 MP4（当前为事件提示音占位）", audioEnabledBool)) {
                settings.enableAudio = audioEnabledBool.get();
            } else {
                settings.enableAudio = audioEnabledBool.get();
            }

            ImGui.textDisabled("采样率: " + settings.audioSampleRate + " Hz | 声道: " + settings.audioChannels);
            UITheme.spacingSmall();
        }

        // =============================
        // 错误提示
        // =============================
        if (lastError != null) {
            ImGui.separator();
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, Icons.ERROR + " " + lastError);
            UITheme.spacingSmall();
        }

        // =============================
        // 操作按钮
        // =============================
        ImGui.separator();

        if (ImGui.button(Icons.EXPORT + " 开始导出", 160, UITheme.Spacing.BUTTON_HEIGHT)) {
            settings.outputPath = outputPathBuffer.get().trim();
            if (!settings.validate()) {
                lastError = "导出设置不合法：请检查输出路径（.mp4）、分辨率、FPS、时间范围。";
            } else if (settings.format == VideoExportSettings.FORMAT_H264_MP4 && !ensureFFmpegReady()) {
                lastError = "FFmpeg 未就绪： " + (ffmpegError != null ? ffmpegError : "未知错误");
            } else if (uiManager == null) {
                lastError = "UIManager 未注入，无法启动导出任务。";
            } else {
                lastError = null;
                ImGui.closeCurrentPopup();
                uiManager.startVideoExport(settings);
            }
        }

        ImGui.sameLine();

        if (ImGui.button("取消", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            lastError = null;
            ImGui.closeCurrentPopup();
        }
    }

    private static List<TimelineTrack> getCameraTracks() {
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null || model.getAllTracks() == null) {
            return List.of();
        }
        List<TimelineTrack> result = new ArrayList<>();
        for (TimelineTrack t : model.getAllTracks()) {
            if (t != null && t.getType() == TrackType.CAMERA) {
                result.add(t);
            }
        }
        return result;
    }

    private static void renderFFmpegStatus() {
        if (!ffmpegChecked) {
            // 懒检测：第一次显示 MP4 相关 UI 时执行一次
            ensureFFmpegReady();
        }

        ImGui.text("FFmpeg 状态：");
        ImGui.sameLine();
        if (ffmpegOk) {
            ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f, Icons.CHECK + " 已就绪");
        } else {
            ImGui.textColored(1.0f, 0.4f, 0.4f, 1.0f, Icons.ERROR + " 不可用");
        }

        if (ffmpegPath != null) {
            ImGui.textDisabled(ffmpegPath);
        }
        if (!ffmpegOk && ffmpegError != null) {
            ImGui.textDisabled(ffmpegError);
        }

        if (ImGui.button(Icons.REFRESH + " 重新检测")) {
            ffmpegChecked = false;
            ensureFFmpegReady();
        }
    }

    private static boolean ensureFFmpegReady() {
        ffmpegChecked = true;
        ffmpegOk = false;
        ffmpegPath = null;
        ffmpegError = null;

        // 1) JVM 属性优先（与 encoder 逻辑一致）
        String override = System.getProperty("chronoblocks.ffmpeg");
        if (override != null && !override.isBlank()) {
            ffmpegPath = override.trim();
        }

        // 2) 游戏根目录（.minecraft）下的 ffmpeg 可执行文件
        if (ffmpegPath == null) {
            try {
                Path gameDir = FabricLoader.getInstance().getGameDir();
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                String name = os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
                Path local = gameDir.resolve(name);
                if (Files.exists(local)) {
                    ffmpegPath = local.toAbsolutePath().toString();
                }
            } catch (Exception ignore) {
                // ignore
            }
        }

        // 3) 系统 PATH 回退
        if (ffmpegPath == null) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ffmpegPath = os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
        }

        // 4) 轻量运行检测：ffmpeg -version，避免玩家点了导出才发现不可执行
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean exited = p.waitFor(2, TimeUnit.SECONDS);
            if (!exited) {
                p.destroy();
                p.destroyForcibly();
                ffmpegError = "ffmpeg 启动超时（2s）";
                return false;
            }
            if (p.exitValue() != 0) {
                ffmpegError = "ffmpeg 返回非 0 退出码: " + p.exitValue();
                return false;
            }
            ffmpegOk = true;
            return true;
        } catch (Exception e) {
            ffmpegError = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "(no message)");
            // 如果是内置路径但不可执行，补充提示
            if (ffmpegPath != null && Files.exists(Path.of(ffmpegPath))) {
                ffmpegError = ffmpegError + "（路径存在但不可执行？）";
            }
            return false;
        }
    }

    private static void openOutputPathDialog() {
        String current = outputPathBuffer.get() != null ? outputPathBuffer.get().trim() : "";
        String defaultPath = current;

        if (defaultPath.isBlank()) {
            try {
                Path gameDir = FabricLoader.getInstance().getGameDir();
                Path videosDir = gameDir.resolve("videos");
                try {
                    Files.createDirectories(videosDir);
                } catch (Exception ignore) {
                    // ignore
                }
                if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
                    defaultPath = videosDir.resolve("chronoblocks_export.mp4").toAbsolutePath().toString();
                } else {
                    defaultPath = videosDir.resolve("chronoblocks_export.png").toAbsolutePath().toString();
                }
            } catch (Exception ignore) {
                defaultPath = (settings.format == VideoExportSettings.FORMAT_H264_MP4) ? "chronoblocks_export.mp4" : "chronoblocks_export.png";
            }
        }

        String title = (settings.format == VideoExportSettings.FORMAT_H264_MP4)
                ? "选择导出 MP4 输出文件"
                : "选择导出 PNG 序列输出基名";

        String filterDesc;
        String pattern;
        if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
            filterDesc = "MP4 文件 (*.mp4)";
            pattern = "*.mp4";
        } else {
            // PNG 序列当前以 outputPath 的“文件名”作为目录基名，给用户一个直观的 *.png 选择
            filterDesc = "PNG 文件 (*.png)";
            pattern = "*.png";
        }

        String chosen;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8(pattern));
            filters.flip();
            chosen = TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, filters, filterDesc);
        } catch (Throwable t) {
            lastError = "打开系统文件选择器失败：" + t.getClass().getSimpleName() + (t.getMessage() != null ? (" - " + t.getMessage()) : "");
            return;
        }

        if (chosen == null || chosen.isBlank()) {
            return; // 用户取消
        }

        String out = chosen.trim();
        if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
            String lower = out.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".mp4")) {
                out = out + ".mp4";
            }
        }

        outputPathBuffer.set(out);
        settings.outputPath = out;
        lastError = null;
    }
}


