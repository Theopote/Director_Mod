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
            // 不再需要用时间线摄像机：无缝切换到游戏摄像机
            // ⭐ 关键修复：用户感觉不到有两套摄像机，切换时无缝衔接（位置、FOV等，不包括旋转）
            if (runtime.isCinematicEnabled()) {
                CameraSample currentSample = runtime.getActiveSample();
                if (currentSample != null && client != null) {
                    // 将当前电影摄像机的位置和FOV应用到游戏摄像机（不包括旋转）
                    if (client.player != null) {
                        client.player.setPos(
                            currentSample.position().x,
                            currentSample.position().y,
                            currentSample.position().z
                        );
                        // 注意：不设置 yaw/pitch，保持玩家当前视角（旋转是后期给关键帧设置的）
                    }
                    // ⭐ 关键修复：无缝切换FOV，让用户感觉不到有两套摄像机
                    // 将电影摄像机的FOV应用到玩家设置，这样退出电影模式后FOV保持一致
                    if (client.options != null && client.options.getFov() != null) {
                        client.options.getFov().setValue((int) currentSample.fov());
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[TimelineCameraController] 无缝切换：将电影摄像机位置和FOV应用到游戏摄像机");
                    }
                }
            }
            // 使用 exitCinematicWithoutRestore 因为我们已经手动应用了位置和FOV
            runtime.exitCinematicWithoutRestore();
            unlockPlayerInput();
            previewMode = false;
            return;
        }

        // ⭐ 关键修复：使用当前时间采样，确保拖动时显示的是当前时间点的插值镜头
        float currentTime = timelineModel.getCurrentTime();
        
        // ⭐ 关键修复：检查当前时间是否在关键帧范围内
        // 如果时间超出关键帧范围，应该退出电影模式而不是使用最后一个关键帧
        List<CameraKeyframe> keyframes = collectCameraKeyframes();
        if (!keyframes.isEmpty()) {
            float firstKeyframeTime = keyframes.stream()
                .map(CameraKeyframe::getTime)
                .min(Float::compareTo)
                .orElse(0f);
            float lastKeyframeTime = keyframes.stream()
                .map(CameraKeyframe::getTime)
                .max(Float::compareTo)
                .orElse(0f);
            
            // ⭐ 关键修复：如果时间超出关键帧范围，退出电影模式
            // 使用严格比较，避免在边界处频繁切换
            // 只有当时间严格在 [firstKeyframeTime, lastKeyframeTime] 范围内时才使用电影模式
            if (currentTime < firstKeyframeTime || currentTime > lastKeyframeTime) {
                // ⭐ 关键修复：只有在当前处于电影模式时才退出，避免频繁调用
                if (runtime.isCinematicEnabled()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[TimelineCameraController] 时间 @ {}s 超出关键帧范围 [{}, {}]，退出电影模式", 
                            currentTime, firstKeyframeTime, lastKeyframeTime);
                    }
                    runtime.exitCinematicAndRestorePlayer();
                    unlockPlayerInput();
                }
                return;
            }
        }
        
        CameraSample sample = sampler.sample(currentTime).orElse(null);
        
        // ⭐ 调试日志：帮助定位 FOV 问题
        if (LOGGER.isDebugEnabled() && sample != null) {
            LOGGER.debug("[TimelineCameraController] 采样 @ {}s: FOV={}, pos=({}, {}, {})", 
                currentTime, sample.fov(), 
                sample.position().x, sample.position().y, sample.position().z);
        }
        
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
     * 显式退出预览模式（供 UI 按钮调用）
     * 
     * <p>⭐ 移除自动超时：预览模式将一直保持，直到：
     * <ul>
     *   <li>用户显式按下 ESC 键或点击"退出预览"按钮</li>
     *   <li>时间线 UI 关闭（onTimelineUiClosed()）</li>
     *   <li>开始播放（播放会接管摄像机控制）</li>
     * </ul>
     */
    public void exitPreviewMode() {
        previewMode = false;
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

        // ⭐ 关键修复：确保使用玩家真实的 FOV
        // 直接从 options 获取玩家的 FOV，而不是从 sample 中获取
        // 因为 sample 中的 FOV 可能已经被电影模式修改过
        float capturedFov = 70.0f; // 默认值
        if (client.options != null && client.options.getFov() != null) {
            capturedFov = client.options.getFov().getValue().floatValue();
        }
        
        // ⭐ 调试日志：记录玩家的实际 FOV
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineCameraController] 捕捉关键帧: 玩家 FOV={}, 采样 FOV={}", 
                capturedFov, sample.fov());
        }

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
        keyframe.setFov(capturedFov);  // ⭐ 使用修正后的 FOV
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());

        // ⭐ 恢复之前的模式（如果需要）
        // 注意：由于用户可能想要继续预览，我们不在捕获后自动恢复
        // 让 update() 方法根据当前状态决定是否恢复 CINEMATIC
        
        LOGGER.info("[TimelineCameraController] 捕捉玩家视角到 CameraKeyframe@{}s: FOV={}, pos=({}, {}, {}), wasCinematic={}", 
            keyframe.getTime(), capturedFov,
            sample.position().x, sample.position().y, sample.position().z,
            wasCinematic);
    }

    /**
     * 从当前摄像机捕获位置和其他参数（但不捕获旋转）。
     * ⭐ 用于创建新关键帧时，保持旋转为默认值0。
     * 
     * <p>⭐ 关键改进：画面不能动
     * - 如果当前在电影模式下，从当前电影摄像机捕获（不退出电影模式，画面不动）
     * - 如果当前不在电影模式下，从游戏摄像机捕获
     * - 这样添加关键帧时画面不会变化，用户看到的就是保存的关键帧位置
     * 
     * <p>与 captureFromGameCamera 的区别：
     * <ul>
     *   <li>不设置旋转（保持为默认的0）</li>
     *   <li>只捕获位置、FOV、裁剪面等参数</li>
     * </ul>
     * 
     * <p>设计理由：
     * - 默认情况下电影摄像机不应该有旋转，只有选中关键帧设置时玩家可以设置旋转
     * - 符合专业动画软件的行为：创建关键帧时通常只设置位置，旋转需要手动调整
     * - 用户调整好摄像机位置后，点击添加关键帧时画面不应该变化
     * 
     * @param keyframe 要更新的关键帧
     */
    public void capturePositionFromGameCamera(CameraKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        CameraRuntime runtime = CameraRuntime.getInstance();
        
        CameraSample sample;
        
        // ⭐ 关键修复：如果当前在电影模式下，从当前电影摄像机捕获（不退出，画面不动）
        if (runtime.isCinematicEnabled()) {
            sample = runtime.getActiveSample();
            if (sample == null) {
                // 如果电影模式下没有sample，回退到游戏摄像机
                sample = CameraAccessor.sampleGameCamera(client);
            } else {
                LOGGER.debug("[TimelineCameraController] 从当前电影摄像机捕获位置（画面不动）");
            }
        } else {
            // 不在电影模式下，从游戏摄像机捕获
            sample = CameraAccessor.sampleGameCamera(client);
        }

        // ⭐ 关键修复：确保使用正确的 FOV
        // 如果从电影摄像机捕获，使用电影摄像机的FOV
        // 如果从游戏摄像机捕获，使用玩家设置的FOV
        float capturedFov = sample.fov();
        if (!runtime.isCinematicEnabled() && client.options != null && client.options.getFov() != null) {
            // 从游戏摄像机捕获时，使用玩家设置的FOV
            capturedFov = client.options.getFov().getValue().floatValue();
        }

        // ⭐ 只捕获位置和其他参数，不捕获旋转（保持旋转为0）
        keyframe.setPosition(new Vector3f(
                (float) sample.position().x,
                (float) sample.position().y,
                (float) sample.position().z
        ));
        // 注意：不设置旋转，保持默认值 (0, 0, 0)
        keyframe.setFov(capturedFov);
        keyframe.setNearPlane(sample.nearPlane());
        keyframe.setFarPlane(sample.farPlane());
        keyframe.setDepthOfFieldEnabled(sample.depthOfFieldEnabled());
        keyframe.setFocusDistance(sample.focusDistance());
        keyframe.setAperture(sample.aperture());

        LOGGER.info("[TimelineCameraController] 捕捉位置到 CameraKeyframe@{}s: FOV={}, pos=({}, {}, {}), 旋转保持为(0,0,0), 从{}捕获", 
            keyframe.getTime(), capturedFov,
            sample.position().x, sample.position().y, sample.position().z,
            runtime.isCinematicEnabled() ? "电影摄像机" : "游戏摄像机");
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