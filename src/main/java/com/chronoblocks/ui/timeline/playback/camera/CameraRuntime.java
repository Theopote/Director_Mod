package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;

/**
 * CameraRuntime v3.0 - 统一摄像机运行时状态管理器
 *
 * ⭐ 职责：维护"当前到底用哪台摄像机"和"当前 sample 是什么"，以及缓存/恢复玩家视角。
 *
 * - GAME 模式：完全不干预原版摄像机（CameraMixin / GameRendererMixin 都会直接 return）
 * - CINEMATIC 模式：每帧由 mixin 从 activeSample 读取位置/旋转/FOV 覆盖原版摄像机
 */
public final class CameraRuntime {
    private static final CameraRuntime INSTANCE = new CameraRuntime();

    /** 内部模式枚举 */
    public enum Mode {
        GAME,           // 默认玩家视角
        CINEMATIC       // 时间线摄像机（播放 / 拖动 / 预览）
    }

    /** 当前模式，默认 GAME */
    private Mode mode = Mode.GAME;

    /** 当前正在应用的镜头（位置、旋转、FOV 等） */
    private CameraSample activeSample = null;

    /** 缓存的玩家视角（进入 CINEMATIC 模式前保存） */
    private CameraSample cachedPlayerView = null;

    /** ⭐ 关键修复：保存玩家原始的 FOV（在第一次进入电影模式前） */
    private Float originalPlayerFov = null;

    /** 单例访问 */
    public static CameraRuntime getInstance() {
        return INSTANCE;
    }

    private CameraRuntime() {}

    // ========================= 查询接口 =========================

    /**
     * 是否处于"电影模式"且有有效 sample。
     * 
     * ⭐ 统一命名：与 Mixin 中的判断条件保持一致。
     * 🚩 注意：只有在返回 true 时，CameraMixin / GameRendererMixin 才会覆盖原版摄像机。
     */
    public boolean isCinematicEnabled() {
        return mode == Mode.CINEMATIC && activeSample != null;
    }

    /**
     * @deprecated 使用 isCinematicEnabled() 替代，保持向后兼容
     */
    @Deprecated
    public boolean isCinematicActive() {
        return isCinematicEnabled();
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * 获取当前 sample（供 mixin 使用）
     */
    public CameraSample getActiveSample() {
        return activeSample;
    }

    // ========================= 状态切换 =========================

    /**
     * 进入电影模式，并应用指定 sample。
     * ⭐ 关键：只在从 GAME 切换到 CINEMATIC 时缓存玩家视角。
     *
     * @param sample 要使用的镜头快照，如果为 null 则退回 GAME 模式
     */
    public void enterCinematic(CameraSample sample) {
        if (sample == null) {
            exitCinematicAndRestorePlayer();
            return;
        }
        
        // ⭐ 关键修复：只在切换模式时缓存玩家视角，避免重复缓存
        if (mode != Mode.CINEMATIC) {
            cachePlayerView();
        }
        
        this.mode = Mode.CINEMATIC;
        this.activeSample = sample;
    }

    /**
     * 在已经处于 CINEMATIC 模式时，更新当前 sample。
     * 一般由 TimelineCameraController.update() 每帧调用。
     */
    public void updateSample(CameraSample sample) {
        if (sample == null) {
            // 没有可用 sample 了，自动退回游戏模式并恢复玩家视角
            exitCinematicAndRestorePlayer();
            return;
        }
        if (mode != Mode.CINEMATIC) {
            // 如果当前不是电影模式，直接切换过去（会触发缓存）
            enterCinematic(sample);
        } else {
            this.activeSample = sample;
        }
    }

    /**
     * 完全退出电影模式，恢复玩家视角。
     * 用于：停止播放、关闭 UI、用户主动退出预览等场景。
     * 
     * ⭐ 关键修复：确保 FOV 正确恢复
     */
    public void exitCinematicAndRestorePlayer() {
        if (mode == Mode.GAME) {
            // ⭐ 关键修复：即使已经是游戏模式，也要确保 FOV 是正确的
            // 因为可能之前进入过电影模式，FOV 可能被修改了
            if (originalPlayerFov != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.options != null && client.options.getFov() != null) {
                    client.options.getFov().setValue(originalPlayerFov.intValue());
                }
            }
            return;
        }
        
        restorePlayerView();
        mode = Mode.GAME;
        activeSample = null;
    }

    /**
     * 只退出电影模式，不恢复玩家视角。
     * 用于："无缝衔接"场景，将当前 sample 烤进玩家视角后退出。
     */
    public void exitCinematicWithoutRestore() {
        mode = Mode.GAME;
        activeSample = null;
        // 注意：不调用 restorePlayerView()，保持当前摄像机状态
    }

    /**
     * 回到原版相机模式（玩家视角）。
     * mixin 不再覆盖 Camera / FOV。
     * 
     * @deprecated 使用 exitCinematicAndRestorePlayer() 替代，更明确语义
     */
    @Deprecated
    public void enterGameMode() {
        exitCinematicAndRestorePlayer();
    }

    /**
     * 强制释放电影模式。
     * 用于：
     * - 时间线 UI 被关闭时
     * - 世界卸载 / 游戏退出时
     * - 任何"保险起见"的重置
     */
    public void forceReleaseCinematic() {
        mode = Mode.GAME;
        activeSample = null;
        cachedPlayerView = null; // 清理缓存
        originalPlayerFov = null; // 清理原始 FOV
    }

    /**
     * 在世界/游戏结束时清理（可选调用）
     */
    public void reset() {
        forceReleaseCinematic();
    }

    // ========================= 内部工具 =========================

    /**
     * 缓存当前玩家视角（在进入 CINEMATIC 模式前调用）
     * ⭐ 关键修复：确保保存玩家原始的 FOV，而不是被修改过的 FOV
     * 
     * 问题分析：
     * - GameRendererMixin 在电影模式下直接覆盖 FOV，不会修改 client.options.getFov()
     * - 所以 client.options.getFov() 应该始终是玩家的原始 FOV
     * - 但为了保险起见，我们只在第一次进入电影模式时保存原始 FOV
     * - 这样即使多次进入/退出电影模式，我们也能恢复玩家真正的原始 FOV
     */
    private void cachePlayerView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        
        // ⭐ 关键修复：只在第一次缓存时保存原始 FOV（当前是 GAME 模式，说明是第一次进入电影模式）
        // 如果 originalPlayerFov 已经存在，说明之前已经保存过，不需要再次保存
        // 注意：GameRendererMixin 不会修改 client.options.getFov()，所以这个值应该是正确的
        if (originalPlayerFov == null && mode == Mode.GAME && client.options != null && client.options.getFov() != null) {
            originalPlayerFov = client.options.getFov().getValue().floatValue();
        }
        
        cachedPlayerView = CameraAccessor.sampleGameCamera(client);
        
        // ⭐ 关键修复：如果已经保存了原始 FOV，用原始 FOV 替换缓存 sample 中的 FOV
        // 这样可以确保恢复时使用的是玩家真正的原始 FOV
        if (originalPlayerFov != null && cachedPlayerView != null) {
            // 创建一个新的 sample，使用原始 FOV
            cachedPlayerView = new CameraSample(
                cachedPlayerView.position(),
                cachedPlayerView.yaw(),
                cachedPlayerView.pitch(),
                cachedPlayerView.roll(),
                originalPlayerFov,  // ⭐ 使用原始 FOV
                cachedPlayerView.nearPlane(),
                cachedPlayerView.farPlane(),
                cachedPlayerView.depthOfFieldEnabled(),
                cachedPlayerView.focusDistance(),
                cachedPlayerView.aperture()
            );
        }
    }

    /**
     * 恢复缓存的玩家视角到游戏摄像机
     * ⭐ 关键修复：确保恢复玩家原始的 FOV
     * 
     * 注意：GameRendererMixin 在非电影模式下不会覆盖 FOV，所以只需要确保 client.options.getFov() 是正确的值即可
     */
    private void restorePlayerView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        
        // ⭐ 关键修复：优先恢复原始 FOV（如果存在）
        // 因为 GameRendererMixin 不会修改 client.options.getFov()，所以理论上不需要恢复
        // 但为了保险起见，我们确保 FOV 是正确的
        if (originalPlayerFov != null && client.options != null && client.options.getFov() != null) {
            // 确保 FOV 恢复到原始值
            client.options.getFov().setValue(originalPlayerFov.intValue());
        }
        
        // 恢复位置和角度（如果有缓存的视角）
        if (cachedPlayerView != null) {
            // ⭐ 关键修复：恢复时使用保存的原始 FOV（如果存在）
            CameraSample sampleToRestore = getCameraSample();

            // 只恢复位置和角度，FOV 已经在上面恢复了
            if (client.player != null) {
                client.player.setPos(sampleToRestore.position().x, sampleToRestore.position().y, sampleToRestore.position().z);
                client.player.setYaw(sampleToRestore.yaw());
                client.player.setPitch(sampleToRestore.pitch());
            }
            
            cachedPlayerView = null; // 恢复后清理缓存
        }
        
        // ⭐ 关键修复：不要清理 originalPlayerFov，因为可能还会再次进入电影模式
        // 只有在 forceReleaseCinematic() 时才清理
    }

    private CameraSample getCameraSample() {
        CameraSample sampleToRestore = cachedPlayerView;
        if (originalPlayerFov != null) {
            // 创建一个新的 sample，使用原始 FOV
            sampleToRestore = new CameraSample(
                cachedPlayerView.position(),
                cachedPlayerView.yaw(),
                cachedPlayerView.pitch(),
                cachedPlayerView.roll(),
                originalPlayerFov,  // ⭐ 使用原始 FOV
                cachedPlayerView.nearPlane(),
                cachedPlayerView.farPlane(),
                cachedPlayerView.depthOfFieldEnabled(),
                cachedPlayerView.focusDistance(),
                cachedPlayerView.aperture()
            );
        }
        return sampleToRestore;
    }
}
