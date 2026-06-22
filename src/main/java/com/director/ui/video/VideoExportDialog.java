package com.director.ui.video;

import com.director.core.DirectorServices;
import net.fabricmc.loader.api.FabricLoader;
import com.director.export.video.VideoExportSettings;
import com.director.ui.DirectorUIManager;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.camera.CameraViewportProfile;

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
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * 🎥 VideoExportDialog - 视频导出对话框（遵循 Director 现有 Popup Dialog 风格）
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
    
    // 用于跟踪摄像机轨道选择变化，自动填充分辨率
    private static int lastCameraTrackIndex = -1;

    // 覆盖确认弹窗状态
    private static final String OVERWRITE_POPUP_ID = "##DirectorVideoExportOverwriteConfirm";
    private static VideoExportSettings pendingStartSettings = null;
    private static String pendingOverwriteWarning = null;

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
        // ⭐ 重置摄像机轨道索引跟踪，确保首次打开时也会自动填充分辨率
        lastCameraTrackIndex = -1;
    }

    public static void renderIfNeeded(DirectorUIManager uiManager) {
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

    private static void renderBody(DirectorUIManager uiManager) {
        // 每帧将 settings 同步到 Im*，确保 UI 切换时值正确
        // 注意：分辨率会在摄像机选择变化时自动更新，这里先同步当前值
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

        // 同步分辨率到 ImInt（在摄像机选择变化后，这里的值会被自动更新）
        widthInt.set(settings.width);
        heightInt.set(settings.height);
        
        ImGui.inputInt("宽度", widthInt);
        ImGui.inputInt("高度", heightInt);
        
        // 分辨率预设快捷按钮（根据当前画幅比例计算）
        UITheme.spacingSmall();
        ImGui.textDisabled("分辨率预设：");
        ImGui.sameLine();
        
        // 计算当前画幅比例（用于预设按钮）
        float currentAspect = (settings.height > 0) ? (float) settings.width / (float) settings.height : 16f / 9f;
        
        // 1080p 预设
        if (ImGui.button("1080p", 80, 0)) {
            applyResolutionPreset(currentAspect, 1080);
        }
        ImGui.sameLine();
        
        // 2K 预设 (1440p)
        if (ImGui.button("2K (1440p)", 100, 0)) {
            applyResolutionPreset(currentAspect, 1440);
        }
        ImGui.sameLine();
        
        // 4K 预设 (2160p)
        if (ImGui.button("4K (2160p)", 100, 0)) {
            applyResolutionPreset(currentAspect, 2160);
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("根据当前画幅比例自动计算对应分辨率");
        }
        
        UITheme.spacingSmall();
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
        ImGui.textDisabled("提示：MP4 依赖 ffmpeg（优先 -Ddirector.ffmpeg，其次放在游戏根目录，最后系统 PATH）。PNG 序列为零依赖。");

        if (settings.format == VideoExportSettings.FORMAT_H264_MP4) {
            renderFFmpegStatus();
        }

        UITheme.spacingSmall();

        // =============================
        // 导出范围
        // =============================
        UITheme.title("导出范围");

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
                lastCameraTrackIndex = -1;
            } else {
                String[] trackNames = cameraTracks.stream()
                        .map(t -> t.getName() != null ? t.getName() : "(未命名轨道)")
                        .toArray(String[]::new);
                
                // 确保索引有效
                cameraTrackIndexInt.set(Math.max(0, Math.min(settings.cameraTrackIndex, trackNames.length - 1)));
                
                // 使用下拉框选择摄像机轨道
                if (ImGui.combo("摄像机轨道", cameraTrackIndexInt, trackNames, trackNames.length)) {
                    // 下拉框选择变化时，更新设置并触发自动填充分辨率
                    settings.cameraTrackIndex = cameraTrackIndexInt.get();
                    settings.cameraTrackName = trackNames[settings.cameraTrackIndex];
                    autoFillResolutionFromCamera(cameraTracks, settings.cameraTrackIndex);
                    lastCameraTrackIndex = settings.cameraTrackIndex;
                } else {
                    // 下拉框未变化，但如果是首次选择（lastCameraTrackIndex == -1），也要填充
                    settings.cameraTrackIndex = cameraTrackIndexInt.get();
                    settings.cameraTrackName = trackNames[settings.cameraTrackIndex];
                    if (lastCameraTrackIndex != settings.cameraTrackIndex) {
                        autoFillResolutionFromCamera(cameraTracks, settings.cameraTrackIndex);
                        lastCameraTrackIndex = settings.cameraTrackIndex;
                    }
                }
            }
        } else {
            // 切换到玩家摄像机时，重置跟踪索引
            lastCameraTrackIndex = -1;
        }

        UITheme.spacingSmall();

        // =============================
        // 渲染质量（简化）
        // =============================
        UITheme.title("渲染质量");

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

            settings.enableAudio = audioEnabledBool.get();

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

        // 覆盖确认弹窗（嵌套在导出对话框内部）
        renderOverwriteConfirmPopupIfNeeded(uiManager);

        if (ImGui.button(Icons.EXPORT + " 开始导出", 160, UITheme.Spacing.BUTTON_HEIGHT)) {
            settings.outputPath = outputPathBuffer.get().trim();
            if (!settings.validate()) {
                lastError = "导出设置不合法：请检查输出路径（.mp4）、分辨率、FPS、时间范围。";
            } else if (settings.format == VideoExportSettings.FORMAT_H264_MP4 && !ensureFFmpegReady()) {
                lastError = "FFmpeg 未就绪： " + (ffmpegError != null ? ffmpegError : "未知错误");
            } else if (uiManager == null) {
                lastError = "UIManager 未注入，无法启动导出任务。";
            } else {
                // 目标存在性检查：同名文件/目录应提示用户
                ExistingTarget existing = findExistingOutputTarget();
                if (existing != null) {
                    pendingStartSettings = copySettings();
                    pendingOverwriteWarning = existing.warningMessage;
                    // 打开确认弹窗
                    ImGui.openPopup(OVERWRITE_POPUP_ID);
                } else {
                    lastError = null;
                    ImGui.closeCurrentPopup();
                    uiManager.startVideoExport(settings);
                }
            }
        }

        ImGui.sameLine();

        if (ImGui.button("取消", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            lastError = null;
            ImGui.closeCurrentPopup();
        }
    }

    private static void renderOverwriteConfirmPopupIfNeeded(DirectorUIManager uiManager) {
        if (ImGui.beginPopupModal(Icons.WARNING + " 文件已存在" + OVERWRITE_POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("检测到输出路径下已有同名文件/目录。");
            if (pendingOverwriteWarning != null) {
                UITheme.spacingSmall();
                ImGui.textWrapped(pendingOverwriteWarning);
            }

            UITheme.spacingSmall();
            ImGui.separator();

            boolean canStart = (pendingStartSettings != null && uiManager != null);

            if (!canStart) {
                ImGui.textDisabled("无法继续：导出设置或 UIManager 缺失。");
            }

            if (ImGui.button(Icons.EXPORT + " 覆盖并导出", 160, UITheme.Spacing.BUTTON_HEIGHT) && canStart) {
                try {
                    // PNG 序列：尝试清空目标目录，避免旧帧混入
                    maybeClearPngSequenceDir(pendingStartSettings);
                } catch (Exception e) {
                    lastError = "清理输出目录失败：" + e.getMessage();
                    // 保持弹窗打开，让用户看到错误
                    ImGui.endPopup();
                    return;
                }

                lastError = null;
                ImGui.closeCurrentPopup(); // 关闭覆盖确认弹窗
                ImGui.closeCurrentPopup(); // 关闭导出设置弹窗
                uiManager.startVideoExport(pendingStartSettings);
                pendingStartSettings = null;
                pendingOverwriteWarning = null;
            }

            ImGui.sameLine();

            if (ImGui.button("取消", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                pendingStartSettings = null;
                pendingOverwriteWarning = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private record ExistingTarget(String warningMessage) {
    }

    private static ExistingTarget findExistingOutputTarget() {
        if (VideoExportDialog.settings.outputPath == null || VideoExportDialog.settings.outputPath.isBlank()) {
            return null;
        }

        try {
            if (VideoExportDialog.settings.format == VideoExportSettings.FORMAT_H264_MP4) {
                Path out = VideoExportDialog.settings.getOutputPathAsPath();
                if (out != null && Files.exists(out)) {
                    return new ExistingTarget("输出文件已存在：\n" + out.toAbsolutePath());
                }
                return null;
            }

            // PNG 序列：输出目录为 parent/<basename>/
            Path outPath = Paths.get(VideoExportDialog.settings.outputPath.trim());
            Path parent = outPath.getParent();
            if (parent == null) {
                parent = Paths.get(".");
            }
            String baseName = stripExtension(outPath.getFileName() != null ? outPath.getFileName().toString() : "export");
            Path dir = parent.resolve(baseName);
            if (Files.exists(dir)) {
                String msg = "PNG 序列输出目录已存在：\n" + dir.toAbsolutePath()
                        + "\n\n覆盖导出建议清空目录，避免旧帧混入。";
                return new ExistingTarget(msg);
            }
        } catch (Exception ignore) {
            // ignore
        }

        return null;
    }

    private static void maybeClearPngSequenceDir(VideoExportSettings s) throws Exception {
        if (s == null || s.format != VideoExportSettings.FORMAT_PNG_SEQUENCE) {
            return;
        }
        if (s.outputPath == null || s.outputPath.isBlank()) {
            return;
        }

        Path outPath = Paths.get(s.outputPath.trim());
        Path parent = outPath.getParent();
        if (parent == null) {
            parent = Paths.get(".");
        }
        String baseName = stripExtension(outPath.getFileName() != null ? outPath.getFileName().toString() : "export");
        Path dir = parent.resolve(baseName);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        // 只删除目录下的文件（不递归删除子目录），避免误伤太多；如果需要可再扩展递归清理
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignore) {
                    // ignore
                }
            });
        }
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "export";
        }
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(0, idx) : name;
    }

    private static VideoExportSettings copySettings() {
        VideoExportSettings dst = new VideoExportSettings();
        dst.outputPath = VideoExportDialog.settings.outputPath;
        dst.width = VideoExportDialog.settings.width;
        dst.height = VideoExportDialog.settings.height;
        dst.fps = VideoExportDialog.settings.fps;
        dst.rangeMode = VideoExportDialog.settings.rangeMode;
        dst.customStartTime = VideoExportDialog.settings.customStartTime;
        dst.customEndTime = VideoExportDialog.settings.customEndTime;
        dst.cameraMode = VideoExportDialog.settings.cameraMode;
        dst.cameraTrackIndex = VideoExportDialog.settings.cameraTrackIndex;
        dst.cameraTrackName = VideoExportDialog.settings.cameraTrackName;
        dst.format = VideoExportDialog.settings.format;
        dst.renderDistance = VideoExportDialog.settings.renderDistance;
        dst.useAntiAliasing = VideoExportDialog.settings.useAntiAliasing;
        dst.enableAudio = VideoExportDialog.settings.enableAudio;
        dst.audioSampleRate = VideoExportDialog.settings.audioSampleRate;
        dst.audioChannels = VideoExportDialog.settings.audioChannels;
        return dst;
    }

    private static List<TimelineTrack> getCameraTracks() {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
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
    
    /**
     * ⭐ 从摄像机属性自动填充导出分辨率
     * 
     * <p>当用户选择摄像机轨道时，自动读取摄像机的 CameraViewportProfile，
     * 如果有预览分辨率则直接使用，否则根据画幅比例计算推荐分辨率。
     * 
     * @param cameraTracks 摄像机轨道列表
     * @param trackIndex 选中的摄像机轨道索引
     */
    private static void autoFillResolutionFromCamera(List<TimelineTrack> cameraTracks, int trackIndex) {
        if (cameraTracks == null || trackIndex < 0 || trackIndex >= cameraTracks.size()) {
            return;
        }
        
        TimelineTrack selectedTrack = cameraTracks.get(trackIndex);
        if (selectedTrack == null) {
            return;
        }
        
        CameraViewportProfile profile = selectedTrack.getCameraViewportProfile();
        if (profile == null) {
            return;
        }
        
        // 根据画幅比例计算推荐分辨率（画幅比例是摄像机属性的核心）
        // 即使有预览分辨率，也优先使用画幅比例来推荐分辨率，因为画幅比例才是"摄像机实际导出的画幅"
        float aspectRatio = profile.getAspectRatio();
        int recommendedWidth, recommendedHeight;
        
        // 根据常见的画幅比例，提供标准分辨率推荐
        if (Math.abs(aspectRatio - 16f / 9f) < 0.01f) {
            // 16:9
            recommendedWidth = 1920;
            recommendedHeight = 1080;
        } else if (Math.abs(aspectRatio - 9f / 16f) < 0.01f) {
            // 9:16 (竖屏)
            recommendedWidth = 1080;
            recommendedHeight = 1920;
        } else if (Math.abs(aspectRatio - 1f) < 0.01f) {
            // 1:1 (方形)
            recommendedWidth = 1080;
            recommendedHeight = 1080;
        } else if (Math.abs(aspectRatio - 21f / 9f) < 0.01f) {
            // 21:9 (超宽屏)
            recommendedWidth = 2560;
            recommendedHeight = 1080;
        } else if (Math.abs(aspectRatio - 4f / 3f) < 0.01f) {
            // 4:3
            recommendedWidth = 1920;
            recommendedHeight = 1440;
        } else {
            // 自定义画幅比例：以宽度 1920 为基准，按比例计算高度
            recommendedWidth = 1920;
            recommendedHeight = Math.round(1920f / aspectRatio);
            // 确保高度是偶数（视频编码通常要求）
            if (recommendedHeight % 2 != 0) {
                recommendedHeight++;
            }
        }
        
        settings.width = recommendedWidth;
        settings.height = recommendedHeight;
        widthInt.set(settings.width);
        heightInt.set(settings.height);
    }
    
    /**
     * 应用分辨率预设（根据画幅比例计算）
     * 
     * @param aspectRatio 当前画幅比例（宽/高）
     * @param targetHeight 目标高度（1080、1440、2160等）
     */
    private static void applyResolutionPreset(float aspectRatio, int targetHeight) {
        int calculatedWidth = Math.round(targetHeight * aspectRatio);
        // 确保宽度和高度都是偶数（视频编码通常要求）
        if (calculatedWidth % 2 != 0) {
            calculatedWidth++;
        }
        if (targetHeight % 2 != 0) {
            targetHeight++;
        }
        
        settings.width = calculatedWidth;
        settings.height = targetHeight;
        widthInt.set(settings.width);
        heightInt.set(settings.height);
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
        String override = System.getProperty("director.ffmpeg");
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
        String defaultPath = outputPathBuffer.get() != null ? outputPathBuffer.get().trim() : "";

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
                    defaultPath = videosDir.resolve("director_export.mp4").toAbsolutePath().toString();
                } else {
                    defaultPath = videosDir.resolve("director_export.png").toAbsolutePath().toString();
                }
            } catch (Exception ignore) {
                defaultPath = (settings.format == VideoExportSettings.FORMAT_H264_MP4) ? "director_export.mp4" : "director_export.png";
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


