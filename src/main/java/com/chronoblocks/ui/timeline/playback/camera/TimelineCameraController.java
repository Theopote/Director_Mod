package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.ChronoBlocksScreen;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * TimelineCameraController v2.1 - 统一时间线摄像机控制器
 *
 * ⭐ 设计目标：
 * - 仅负责：从时间线采样 CameraSample，喂给 CameraRuntime
 * - 不直接操作 Camera / GameRenderer（全部交给 mixin）
 *
 * ⭐ 模式逻辑（重点）：
 * - 使用时间线摄像机（CINEMATIC）当且仅当：
 *   1) timelineModel.isPlaying() == true
 *   2) 或 scrubbing == true（正在拖动播放头）
 *   3) 或 previewMode == true（点击关键帧预览）
 *
 * - 使用游戏摄像机（GAME）：
 *   1) 不播放 && 不拖动 && !previewMode
 *   2) 或 UI 通知"时间线关闭 / 离开时间线面板"
 *   3) 或 玩家在非时间线区域主动操作（键盘/鼠标），我们认为玩家要接管视角
 */
public final class TimelineCameraController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCameraController.class);
    private static final TimelineCameraController INSTANCE = new TimelineCameraController();

    private TimelineModel timelineModel;
    private final CameraTrackSampler sampler = new CameraTrackSampler();

    /** 是否正在拖动播放头（ruler dragging） */
    private boolean scrubbing = false;

    /** 鼠标是否悬停在时间线 UI 上（需要由 UI 通知） */
    private boolean mouseOverTimeline = false;

    /** 预览模式：例如点击摄像机关键帧进行预览时启用 */
    private boolean previewMode = false;
    
    /** 预览模式超时计时器（秒），超过此时间自动退出预览 */
    private static final float PREVIEW_TIMEOUT_SECONDS = 10.0f;
    private float previewModeStartTime = -1f;

    private TimelineCameraController() {}

    public static TimelineCameraController getInstance() {
        return INSTANCE;
    }

    // ========================= TimelineModel 绑定 =========================

    /**
     * 绑定 TimelineModel。
     * 每帧在 update() 中重新收集关键帧，无需监听器。
     */
    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
        if (model != null) {
            // 初始化一次
            sampler.setKeyframes(collectCameraKeyframes());
        } else {
            sampler.setKeyframes(new ArrayList<>());
        }
    }

    /**
     * 当关键帧发生变化时可以显式调用（例如某些命令直接操作 keyframe 列表）
     */
    public void onKeyframeChanged() {
        if (timelineModel != null) {
            sampler.setKeyframes(collectCameraKeyframes());
            LOGGER.debug("[TimelineCameraController] onKeyframeChanged()，刷新采样器");
        }
    }

    // ========================= 状态由 UI / TimelinePanel 控制 =========================

    /** 设置是否正在拖动播放头（由 TimelinePanel / InteractionHandler 在鼠标按下/抬起时调用） */
    public void setScrubbing(boolean scrubbing) {
        boolean wasScrubbing = this.scrubbing;
        this.scrubbing = scrubbing;
        
        if (scrubbing && !wasScrubbing) {
            LOGGER.debug("[TimelineCameraController] scrubbing 开始");
        } else if (!scrubbing && wasScrubbing) {
            LOGGER.debug("[TimelineCameraController] scrubbing 结束");
            // ⭐ 关键修复：不再在这里直接退出 CINEMATIC，让 update() 统一处理
            // 如果需要"无缝衔接"，在 InteractionHandler.endInteraction() 中调用 bakeCurrentSampleIntoGameAndExit()
        }
    }

    /** 通知鼠标是否在时间线面板上（由 TimelinePanel 在进入/离开时调用） */
    public void setMouseOverTimeline(boolean mouseOverTimeline) {
        this.mouseOverTimeline = mouseOverTimeline;
    }

    /** 播放开始（由 TimelineModel.setPlaying(true) 通知） */
    public void onPlaybackStarted() {
        LOGGER.debug("[TimelineCameraController] 播放开始");
        // 播放开始时可以退出预览模式，由播放接管
        this.previewMode = false;
    }

    /** 播放停止（由 TimelineModel.setPlaying(false) 通知） */
    public void onPlaybackStopped() {
        LOGGER.debug("[TimelineCameraController] 播放停止");
        // 不强制退出预览模式，方便用户暂停后继续查看当前帧
    }

    /**
     * 时间线 UI 被关闭 / 隐藏时调用。
     * 建议在 Panel 被关闭时调用：
     *   TimelineCameraController.getInstance().onTimelineUiClosed();
     */
    public void onTimelineUiClosed() {
        LOGGER.info("[TimelineCameraController] 时间线 UI 关闭，开始清理电影模式和输入锁定");
        
        // ⭐ 关键修复：重置所有状态
        this.scrubbing = false;
        this.mouseOverTimeline = false;
        this.previewMode = false;
        this.previewModeStartTime = -1f;
        
        // ⭐ 关键修复：退出电影模式并恢复玩家视角
        CameraRuntime.getInstance().exitCinematicAndRestorePlayer();
        
        // ⭐ 关键修复：解锁玩家输入
        unlockPlayerInput();
        
        LOGGER.info("[TimelineCameraController] 时间线 UI 关闭，电影模式和输入锁定已清理");
    }

    // ========================= 主更新函数 =========================

    /**
     * 主更新函数（TimelinePanel 每帧调用）
     * 
     * ⭐ v3.0 重构：统一状态机逻辑，确保采样时间正确，修复"突然放大"和"总是最后一帧"问题
     */
    public void update() {
        if (timelineModel == null) {
            CameraRuntime.getInstance().exitCinematicAndRestorePlayer();
            return;
        }

        CameraRuntime runtime = CameraRuntime.getInstance();

        // 如果时间线中没有任何可用摄像机关键帧，直接回到游戏模式
        List<CameraKeyframe> frames = collectCameraKeyframes();
        sampler.setKeyframes(frames);
        if (frames.isEmpty()) {
            runtime.exitCinematicAndRestorePlayer();
            previewMode = false;
            unlockPlayerInput();
            return;
        }

        // ⭐ 预览模式超时检查
        if (previewMode && previewModeStartTime > 0) {
            float currentTimeSeconds = (float) (System.currentTimeMillis() / 1000.0);
            float elapsed = currentTimeSeconds - previewModeStartTime;
            if (elapsed > PREVIEW_TIMEOUT_SECONDS) {
                LOGGER.info("[TimelineCameraController] 预览模式超时 ({}s)，自动退出", elapsed);
                exitPreviewMode();
            }
        }
        
        // ⭐ 全局热键：ESC 键强制退出 CINEMATIC 模式
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null && client.options.inventoryKey != null) {
            if (client.options.inventoryKey.wasPressed()) {
                if (runtime.isCinematicEnabled()) {
                    LOGGER.info("[TimelineCameraController] ESC 键按下，强制退出 CINEMATIC 模式");
                    exitPreviewMode();
                    scrubbing = false;
                }
            }
        }

        // ⭐ 关键修复：统一判断是否应该使用时间线摄像机
        boolean useCinematic = shouldUseCinematic();

        if (!useCinematic) {
            // 不再需要用时间线摄像机：退出并恢复玩家视角
            runtime.exitCinematicAndRestorePlayer();
            unlockPlayerInput();
            previewMode = false;
            previewModeStartTime = -1f;
            return;
        }

        // ⭐ 关键修复：使用当前时间采样，确保拖动时显示的是当前时间点的插值镜头
        float currentTime = timelineModel.getCurrentTime();
        CameraSample sample = sampler.sample(currentTime).orElse(null);
        
        if (sample == null) {
            // 采样失败，退出电影模式
            runtime.exitCinematicAndRestorePlayer();
            unlockPlayerInput();
            return;
        }

        // 进入或更新电影模式
        if (!runtime.isCinematicEnabled()) {
            runtime.enterCinematic(sample);
        } else {
            runtime.updateSample(sample);
        }

        // 播放时锁定输入，scrubbing 只锁移动但保留鼠标视角也可以
        lockPlayerInput();
    }

    /**
     * ⭐ 统一判断：何时使用时间线摄像机
     * 
     * 使用时间线摄像机当且仅当：
     * 1. 时间线窗口可见（ChronoBlocks UI 打开）
     * 2. 且（正在播放 || 正在拖动播放头 || 手动预览模式）
     * 3. 且有摄像机关键帧
     */
    private boolean shouldUseCinematic() {
        if (timelineModel == null) {
            return false;
        }
        if (sampler.isEmpty()) {
            return false;
        }

        // ⭐ 检查时间线窗口是否可见
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hasTimelineFocus = client != null && client.currentScreen instanceof ChronoBlocksScreen;

        // 只要在播 / 拖 / 手动预览，且 ChronoBlocks UI 还开着，就用时间线摄像机
        return hasTimelineFocus && (timelineModel.isPlaying() || scrubbing || previewMode);
    }

    /**
     * 当用户点击某个摄像机关键帧，希望预览画面时，可调用这个方法。
     * 建议：点击 keyframe 时，TimelinePanel 调用：
     *   TimelineCameraController.getInstance().previewAt(keyframe.getTime());
     */
    public void previewAt(float timeSeconds) {
        if (timelineModel != null) {
            timelineModel.setCurrentTime(timeSeconds);
        }

        sampler.setKeyframes(collectCameraKeyframes());
        if (sampler.isEmpty()) {
            previewMode = false;
            CameraRuntime.getInstance().exitCinematicAndRestorePlayer();
            return;
        }

        CameraSample sample = sampler.sample(timeSeconds).orElse(null);
        if (sample == null) {
            previewMode = false;
            CameraRuntime.getInstance().exitCinematicAndRestorePlayer();
            return;
        }

        // 进入预览模式：使用时间线摄像机，但不修改播放状态
        previewMode = true;
        previewModeStartTime = (float) (System.currentTimeMillis() / 1000.0); // 记录开始时间
        CameraRuntime.getInstance().enterCinematic(sample);
        LOGGER.info("[TimelineCameraController] 预览摄像机 @ {}s", timeSeconds);
    }
    
    /**
     * 显式退出预览模式（供 UI 按钮调用）
     */
    public void exitPreviewMode() {
        previewMode = false;
        previewModeStartTime = -1f;
        CameraRuntime.getInstance().exitCinematicAndRestorePlayer();
        unlockPlayerInput();
        LOGGER.info("[TimelineCameraController] 用户显式退出预览模式");
    }

    /**
     * ⭐ 关键修复：将当前预览的 sample 烤进玩家视角，然后退出电影模式（不恢复旧视角）
     * 
     * 用于"无缝衔接"场景：拖动播放头松手后，玩家视角跳到当前预览位置。
     */
    public void bakeCurrentSampleIntoGameAndExit() {
        CameraRuntime runtime = CameraRuntime.getInstance();
        if (!runtime.isCinematicEnabled()) {
            return;
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            CameraAccessor.applySampleToGameCamera(client, sample);
        }

        // ⭐ 关键修复：不恢复旧视角，保持当前位置/FOV
        runtime.exitCinematicWithoutRestore();
        unlockPlayerInput();
        
        LOGGER.debug("[TimelineCameraController] 将当前 sample 烤进玩家视角并退出电影模式");
    }

    /**
     * 供 TimelinePanel.onAddKeyframeToTrack 使用：
     * 从当前游戏摄像机填充一个 CameraKeyframe。
     * 
     * <p>⭐ 关键修复：在 CINEMATIC 模式下，临时退出电影模式以捕获真实的玩家视角
     * 而不是捕获被时间线覆盖的摄像机数据
     */
    public void captureFromGameCamera(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        CameraRuntime runtime = CameraRuntime.getInstance();
        
        // ⭐ 关键修复：如果当前在 CINEMATIC 模式，临时退出以捕获真实玩家视角
        boolean wasCinematic = runtime.isCinematicEnabled();
        if (wasCinematic) {
            runtime.exitCinematicAndRestorePlayer();
            // 等待一帧让 Camera 更新到玩家视角（如果可能）
            // 注意：由于这是同步调用，我们直接采样，Mixin 会在下一帧应用
            LOGGER.debug("[TimelineCameraController] 临时退出 CINEMATIC 模式以捕获玩家视角");
        }

        CameraSample sample = CameraAccessor.sampleGameCamera(client);

        keyframe.setPosition(new Vector3f(
                (float) sample.position().x,
                (float) sample.position().y,
                (float) sample.position().z
        ));
        // rotation.x = pitch, rotation.y = yaw, rotation.z = roll
        keyframe.setRotation(new Vector3f(
                sample.pitch(),  // x = pitch
                sample.yaw(),    // y = yaw
                sample.roll()    // z = roll
        ));
        keyframe.setFov(sample.fov());
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());

        // ⭐ 恢复之前的模式（如果需要）
        // 注意：由于用户可能想要继续预览，我们不在捕获后自动恢复
        // 让 update() 方法根据当前状态决定是否恢复 CINEMATIC
        
        LOGGER.info("[TimelineCameraController] 捕捉玩家视角到 CameraKeyframe@{}s (wasCinematic: {})", 
            keyframe.getTime(), wasCinematic);
    }

    // ========================= 内部工具 =========================

    /**
     * 锁定玩家输入（每帧调用，确保完全锁定）
     * 
     * <p>⭐ 关键修复：
     * <ul>
     *   <li>每帧清零移动输入，防止玩家按键干扰</li>
     *   <li>清空所有键绑定状态</li>
     *   <li>注意：鼠标旋转由 CameraMixin 覆盖，这里不需要额外处理</li>
     * </ul>
     */
    private void lockPlayerInput() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.input != null) {
            var input = client.player.input;
            input.movementForward = 0.0f;
            input.movementSideways = 0.0f;
            // 注意：jumping 和 sneaking 通过 key bindings 处理
        }

        if (client.options != null) {
            clearKey(client.options.forwardKey);
            clearKey(client.options.backKey);
            clearKey(client.options.leftKey);
            clearKey(client.options.rightKey);
            clearKey(client.options.jumpKey);
            clearKey(client.options.sneakKey);
            clearKey(client.options.sprintKey);
        }
    }

    /**
     * 解锁玩家输入（退出电影模式时调用）
     * ⭐ 关键修复：确保所有键绑定状态被正确释放
     */
    private void unlockPlayerInput() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        // ⭐ 关键修复：显式释放所有键绑定，确保输入不被锁定
        if (client.options != null) {
            clearKey(client.options.forwardKey);
            clearKey(client.options.backKey);
            clearKey(client.options.leftKey);
            clearKey(client.options.rightKey);
            clearKey(client.options.jumpKey);
            clearKey(client.options.sneakKey);
            clearKey(client.options.sprintKey);
        }

        // ⭐ 关键修复：恢复玩家输入状态（如果存在）
        // 注意：不直接设置 movementForward/movementSideways，让玩家自然控制
        // 这些值会在下一帧由玩家的按键输入更新
        // 只需要确保键绑定被释放即可
    }

    private void clearKey(KeyBinding key) {
        if (key != null) {
            key.setPressed(false);
        }
    }

    /**
     * 粗略判断"玩家是否正在试图控制角色/视角"
     * - 检测 WASD、跳跃、潜行、冲刺等按键是否被按下
     * - 不依赖鼠标 delta（ImGui 可能会吞掉鼠标事件）
     */
    private boolean isPlayerManipulatingCameraOrMoving() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return false;
        }

        return isKeyDown(client.options.forwardKey)
                || isKeyDown(client.options.backKey)
                || isKeyDown(client.options.leftKey)
                || isKeyDown(client.options.rightKey)
                || isKeyDown(client.options.jumpKey)
                || isKeyDown(client.options.sneakKey)
                || isKeyDown(client.options.sprintKey);
    }

    private boolean isKeyDown(KeyBinding key) {
        return key != null && key.isPressed();
    }

    /**
     * 从单个关键帧创建 CameraSample（用于无关键帧区域的回退）
     */
    private CameraSample createSampleFromKeyframe(CameraKeyframe kf) {
        if (kf == null) {
            return null;
        }
        
        org.joml.Vector3f pos = kf.getPosition();
        org.joml.Vector3f rot = kf.getRotation();
        
        return new CameraSample(
            new net.minecraft.util.math.Vec3d(pos.x, pos.y, pos.z),
            rot.y,   // yaw
            rot.x,   // pitch
            rot.z,   // roll
            kf.getFov(),
            kf.getNearPlane(),
            kf.getFarPlane(),
            kf.isDepthOfFieldEnabled(),
            kf.getFocusDistance(),
            kf.getAperture()
        );
    }

    private List<CameraKeyframe> collectCameraKeyframes() {
        List<CameraKeyframe> list = new ArrayList<>();
        if (timelineModel == null) return list;

        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type == null || !type.supportsCameraPath() || track.isLocked() || !track.isVisible()) {
                continue;
            }
            for (TimelineSelectable s : track.getAutomationKeyframes()) {
                if (s instanceof CameraKeyframe ck) {
                    list.add(ck);
                }
            }
        }
        return list;
    }
}
