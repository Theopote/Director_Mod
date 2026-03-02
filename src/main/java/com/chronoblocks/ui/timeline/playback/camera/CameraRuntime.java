package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一相机运行时（单一相机状态 + 控制权）
 * <p>
 * 核心设计：
 * - 只有一份 currentSample，表示“当前摄像机状态”（位置 / 旋转 / FOV / DOF...）
 * - 只有一个 owner 表示当前谁在控制摄像机：PLAYER 或 TIMELINE
 * - Mixin 每帧只与这一份状态交互：
 *    - 当 owner=PLAYER 时，从原版 Camera 写入 currentSample
 *    - 当 owner=TIMELINE 时，从 currentSample 写回原版 Camera
 * <p>
 * 兼容性：
 * - 保留部分旧 API（enterGameMode / enterCinematic / getActiveSample 等），内部用新设计实现
 */
public final class CameraRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CameraRuntime.class);
    private static final CameraRuntime INSTANCE = new CameraRuntime();
    private static final float EPSILON = 1e-4f;

    /** 当前控制权归属 */
    public enum Owner {
        PLAYER,    // 玩家控制
        TIMELINE   // 时间线（播放 / 拖动 / 预览）控制
    }

    /** 当前控制权（volatile：渲染线程 / UI线程 / 导出线程均可读写） */
    private volatile Owner owner = Owner.PLAYER;

    /** 当前相机状态（volatile：CameraMixin 读 + Controller/Export 写） */
    private volatile CameraSample currentSample = CameraSample.EMPTY;

    // 退出时间线时的轻微过渡（0.1s lerp）
    private volatile CameraSample lerpStart;
    private volatile CameraSample lerpTarget;
    private volatile float lerpElapsed = 0f;
    private volatile float lerpDuration = 0f;
    private volatile boolean lerpActive = false;

    private CameraRuntime() {}

    public static CameraRuntime getInstance() {
        return INSTANCE;
    }

    public boolean isPlayerOwner() {
        return owner == Owner.PLAYER;
    }

    public boolean isTimelineOwner() {
        return owner == Owner.TIMELINE;
    }

    public CameraSample getCurrentSample() {
        return currentSample;
    }

    /**
     * 由 CameraMixin 在 Camera.update() 之后调用。
     * 只在 owner=PLAYER 时更新当前状态（时间线接管时不覆盖）。
     */
    public void updateFromGameCamera(Vec3d pos, float yaw, float pitch, float fov) {
        if (owner != Owner.PLAYER) {
            // 时间线接管时，不要用玩家视角覆盖掉采样的镜头
            return;
        }

        this.currentSample = new CameraSample(
                pos,
                yaw,
                pitch,
                0.0f,      // roll 对玩家视角无意义
                fov,
                0.05f,
                1024.0f,
                false,
                10.0f,
                2.8f
        );
    }

    /**
     * 由 TimelineCameraController 每帧写入时间线采样结果。
     * 语义：这是时间线控制器的采样写入，owner 应为 TIMELINE。
     */
    public void applyTimelineSample(CameraSample sample) {
        if (sample == null) return;
        this.currentSample = sample;
    }

    /**
     * 仅更新 currentSample，不改变 owner。
     * 用于捕获关键帧时同步状态，而不影响当前的 owner 状态。
     * 
     * <p>与 applyTimelineSample 的区别：
     * <ul>
     *   <li>applyTimelineSample：时间线控制器写入，调用方应确保 owner=TIMELINE</li>
     *   <li>updateCurrentSample：玩家模式下同步状态，不触发 owner 切换</li>
     * </ul>
     * 
     * @param sample 要同步的摄像机样本
     */
    public void updateCurrentSample(CameraSample sample) {
        if (sample == null) return;
        assert owner == Owner.PLAYER : "updateCurrentSample should only be called in PLAYER mode";
        this.currentSample = sample;
    }

    /**
     * 请求一次玩家相机的平滑过渡，用于退出时间线时避免“瞬移”。
     */
    public void applyLerpToPlayer(CameraSample target, float durationSeconds) {
        if (target == null || durationSeconds <= EPSILON) {
            lerpActive = false;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        CameraSample start = CameraAccessor.sampleFromGameCamera(mc);
        if (start == null) {
            start = currentSample;
        }
        lerpStart = start;
        lerpTarget = target;
        lerpDuration = durationSeconds;
        lerpElapsed = 0f;
        lerpActive = true;
    }

    /**
     * 在 PLAYER 模式下逐帧推进 lerp，将玩家实体轻微拉向时间线最后的样本。
     */
    public void tickPlayerLerp(float deltaSeconds) {
        if (!lerpActive) {
            return;
        }
        lerpElapsed += Math.max(0f, deltaSeconds);
        float t = lerpDuration <= EPSILON ? 1f : Math.min(1f, lerpElapsed / lerpDuration);
        CameraSample blended = lerpSamples(lerpStart, lerpTarget, t);

        MinecraftClient mc = MinecraftClient.getInstance();
        CameraAccessor.applySampleToPlayerEntity(mc, blended);
        this.currentSample = blended;

        if (t >= 1f - EPSILON) {
            lerpActive = false;
        }
    }

    /**
     * 切换控制权，并负责锁 / 解锁玩家输入。
     */
    public void setOwner(Owner newOwner) {
        if (this.owner == newOwner) {
            return;
        }

        this.owner = newOwner;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (newOwner == Owner.TIMELINE) {
            lockPlayerInput(mc);
            LOGGER.debug("[CameraRuntime] Owner 切换为 TIMELINE，相机由时间线控制");
        } else {
            unlockPlayerInput(mc);
            LOGGER.debug("[CameraRuntime] Owner 切换为 PLAYER，相机恢复玩家控制");
        }
    }

    /**
     * 重置为“玩家控制，相机状态由 Minecraft 自己刷新”的状态。
     * 一般在：
     * - 关闭 ChronoBlocks UI
     * - 世界退出
     * 等场景调用。
     */
    public void reset() {
        setOwner(Owner.PLAYER);
        // 不强制清空 currentSample，后续 Camera.update 会刷新
    }

    // ================== 玩家输入锁定 / 解锁 ==================

    private void lockPlayerInput(MinecraftClient client) {
        if (client.player != null && client.player.input != null) {
            var input = client.player.input;
            input.playerInput = new net.minecraft.util.PlayerInput(false, false, false, false, false, false, false);
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

    private void unlockPlayerInput(MinecraftClient client) {
        // 基本上不需要做额外事情，只要不再每帧清零就行
        // KeyBinding 在玩家下一次按键时会自动更新状态
    }

    private void clearKey(net.minecraft.client.option.KeyBinding key) {
        if (key != null) {
            key.setPressed(false);
        }
    }

    private CameraSample lerpSamples(CameraSample a, CameraSample b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        Vec3d pa = a.position();
        Vec3d pb = b.position();
        Vec3d pos = new Vec3d(
                lerp(pa.x, pb.x, t),
                lerp(pa.y, pb.y, t),
                lerp(pa.z, pb.z, t)
        );

        float yaw = lerpAngle(a.yaw(), b.yaw(), t);
        float pitch = lerpAngle(a.pitch(), b.pitch(), t);
        float roll = lerpAngle(a.roll(), b.roll(), t);
        float fov = lerp(a.fov(), b.fov(), t);
        float near = lerp(a.nearPlane(), b.nearPlane(), t);
        float far = lerp(a.farPlane(), b.farPlane(), t);
        boolean dof = t < 0.5f ? a.depthOfFieldEnabled() : b.depthOfFieldEnabled();
        float focus = lerp(a.focusDistance(), b.focusDistance(), t);
        float aperture = lerp(a.aperture(), b.aperture(), t);

        return new CameraSample(pos, yaw, pitch, roll, fov, near, far, dof, focus, aperture);
    }

    private float lerp(double a, double b, float t) {
        return (float) (a + (b - a) * Math.max(0f, Math.min(1f, t)));
    }

    private float lerpAngle(float a, float b, float t) {
        float delta = wrapDegrees(b - a);
        return a + delta * Math.max(0f, Math.min(1f, t));
    }

    private float wrapDegrees(float degrees) {
        degrees = degrees % 360f;
        if (degrees >= 180f) {
            degrees -= 360f;
        }
        if (degrees < -180f) {
            degrees += 360f;
        }
        return degrees;
    }
}