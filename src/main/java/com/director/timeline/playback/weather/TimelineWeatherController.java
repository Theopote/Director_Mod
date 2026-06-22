package com.director.timeline.playback.weather;

import com.director.core.DirectorServices;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.weather.WeatherKeyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimelineWeatherController v1.0
 * -------------------------------------------------------
 * 负责在"导演模式 + 天气轨道启用"时，用 WeatherTrackSampler
 * 驱动 Minecraft 客户端的天气表现：
 * <p>
 *  - 雨 / 雪 / 雷暴 强度（雨滴、粒子、天空暗度）
 *  - 云量 / 雾密度（可交给渲染 Mixin 使用 WeatherSample）
 *  - 湿度 / 风力（可用于粒子 / 叶子摆动等二期扩展）
 * <p>
 * 设计要点：
 *  - 单例（与 TimelineSeasonController / TimelineWorldController 风格一致）
 *  - 不主动"创造天气 Mixin"，只是提供 hasOverride() + current()
 *    供其他渲染/粒子 Mixin 查询。
 *  - 通过 applyToWorld(...) 尽量利用 Vanilla 提供的雨/雷接口。
 * <p>
 * 使用方式（典型调用链）：
 *  - 时间线播放： updateForPlayback(currentTimeSeconds)
 *  - 拖动时间标尺： previewAt(currentTimeSeconds)
 *  - UI 切换激活轨道： bindActiveTrack(track) / clearActiveTrack()
 *  - 关闭导演模式 / 删除天气轨道： disableAndReset()
 *
 * @author Director
 * @version 1.0
 */
public final class TimelineWeatherController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineWeatherController.class);

    // ========= 单例 =========

    private static final TimelineWeatherController INSTANCE = new TimelineWeatherController();

    public static TimelineWeatherController getInstance() {
        return INSTANCE;
    }

    /** 便捷别名，供 Mixin 使用 */
    public static TimelineWeatherController get() {
        return INSTANCE;
    }

    // ========= 状态字段 =========

    /** 当前是否启用"天气轨道接管" */
    private boolean enabled = false;

    /** 当前绑定的天气轨道（可能为 null） */
    private TimelineTrack activeTrack;

    /** 采样器：负责从 WeatherKeyframe 列表插值出 WeatherSample */
    private WeatherTrackSampler sampler;

    /** 当前采样结果，供渲染 / 粒子 Mixin 查询 */
    private WeatherSample currentSample;

    /** 当前是否有有效 override（enabled + sampler + sample） */
    private boolean hasOverride = false;

    private TimelineWeatherController() {
    }

    // ==========================================================
    // 基本控制 API（给 UI / TimelinePanel 调用）
    // ==========================================================

    /**
     * 是否启用天气轨道接管。
     * 通常由 UI 的"启用天气轨道"按钮控制。
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;

        if (!enabled) {
            // 关闭时：清理 override，尽量恢复原版天气
            clearOverrideAndResetWorld();
        } else {
            LOGGER.info("[WeatherController] 天气轨道接管已启用");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 绑定/切换当前激活的天气轨道。
     * 一般在 TimelinePanel 用户选中某条 Weather 轨道时调用。
     */
    public void bindActiveTrack(TimelineTrack track) {
        if (track != null && track.getType() != TrackType.WEATHER) {
            LOGGER.warn("[WeatherController] 试图绑定非天气轨道: {}", track.getType());
        }
        this.activeTrack = track;
        rebuildFromActiveTrack();
    }

    /**
     * 设置激活的天气轨道（向后兼容方法）。
     * 
     * @deprecated 使用 {@link #bindActiveTrack(TimelineTrack)} 代替
     */
    @Deprecated
    public void setActiveWeatherTrack(TimelineTrack track) {
        bindActiveTrack(track);
    }

    /**
     * 获取当前激活的天气轨道。
     */
    public TimelineTrack getActiveWeatherTrack() {
        return activeTrack;
    }

    /**
     * 是否存在天气轨道（无论是否已激活）。
     */
    public boolean hasWeatherTrack() {
        if (activeTrack != null) {
            return true;
        }
        com.director.ui.timeline.container.ContainerTimelineService service =
            DirectorServices.get().containerTimeline();
        if (service == null) {
            return false;
        }
        com.director.ui.timeline.model.TimelineModel model = service.getTimelineModel();
        if (model == null) {
            return false;
        }
        for (TimelineTrack track : model.getTracks()) {
            if (track.getType() == TrackType.WEATHER) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当轨道上的关键帧发生变化（添加/删除/移动）时调用。
     * 例如：
     *  - WeatherKeyframe 命令执行完成后
     *  - 撤销/重做
     */
    public void rebuildFromActiveTrack() {
        if (activeTrack == null) {
            sampler = null;
            currentSample = null;
            hasOverride = false;
            return;
        }

        sampler = new WeatherTrackSampler(activeTrack);
        sampler.rebuildKeyframeList();
        
        // 统计关键帧数量
        int keyframeCount = 0;
        for (com.director.ui.timeline.model.TimelineSelectable selectable : activeTrack.getAutomationKeyframes()) {
            if (selectable instanceof WeatherKeyframe) {
                keyframeCount++;
            }
        }
        LOGGER.debug("[WeatherController] 重建 WeatherTrackSampler。关键帧数 = {}", keyframeCount);

        // ⭐ v1.2：重建后立即用当前时间采样，保持 currentSample 始终有效
        if (enabled && keyframeCount > 0) {
            float currentTime = 0f;
            com.director.ui.timeline.container.ContainerTimelineService svc =
                DirectorServices.get().containerTimeline();
            if (svc != null) {
                com.director.ui.timeline.model.TimelineModel m = svc.getTimelineModel();
                if (m != null) {
                    currentTime = m.getCurrentTime();
                }
            }
            WeatherSample sample = sampler.sample(currentTime);
            this.currentSample = sample;
            this.hasOverride = true;
            applyToWorld(sample);
        }
    }

    /**
     * ⭐ v1.0：检查并自动激活唯一的天气轨道。
     * 当模型结构变更时调用此方法，如果只有一个天气轨道，自动激活它。
     * 
     * <p>此方法通过 ContainerTimelineService 获取 TimelineModel，不直接绑定模型。
     */
    public void checkAndAutoActivate() {
        com.director.ui.timeline.container.ContainerTimelineService service = 
            DirectorServices.get().containerTimeline();
        if (service == null) {
            return;
        }
        
        com.director.ui.timeline.model.TimelineModel model = service.getTimelineModel();
        if (model == null) {
            return;
        }
        
        // 统计当前天气轨道数量
        int weatherTrackCount = 0;
        TimelineTrack firstWeatherTrack = null;
        for (TimelineTrack track : model.getTracks()) {
            if (track.getType() == TrackType.WEATHER) {
                weatherTrackCount++;
                if (firstWeatherTrack == null) {
                    firstWeatherTrack = track;
                }
            }
        }
        
        // ⭐ v1.0：如果只有一个天气轨道，无论当前状态如何，都确保它是激活的
        // 这包括：从0个变成1个、从多个变成1个、新建唯一轨道等情况
        if (weatherTrackCount == 1) {
            if (activeTrack != firstWeatherTrack) {
                activeTrack = firstWeatherTrack;
                LOGGER.info("[WeatherController] 检测到只有一个天气轨道，自动激活: {}", firstWeatherTrack.getName());
                rebuildFromActiveTrack();
            }
            // ⭐ 自动启用天气控制器（如果有激活轨道）
            if (!enabled) {
                setEnabled(true);
            }
            // ⭐ v1.2：自动激活后立即采样当前时间，确保 currentSample 有效
            //    否则在用户交互前 Mixin/forceDisableVanillaWeather 读到 null
            if (currentSample == null && sampler != null) {
                float currentTime = 0f;
                com.director.ui.timeline.container.ContainerTimelineService svc =
                    DirectorServices.get().containerTimeline();
                if (svc != null) {
                    com.director.ui.timeline.model.TimelineModel m = svc.getTimelineModel();
                    if (m != null) {
                        currentTime = m.getCurrentTime();
                    }
                }
                WeatherSample sample = sampler.sample(currentTime);
                this.currentSample = sample;
                this.hasOverride = true;
                applyToWorld(sample);
                LOGGER.debug("[WeatherController] 自动激活后立即采样 @ t={}s, rain={}", currentTime, sample.rainIntensity);
            }
        } else if (weatherTrackCount == 0) {
            // 没有天气轨道时，清空激活轨道并禁用控制器
            if (activeTrack != null) {
                activeTrack = null;
                sampler = null;
                currentSample = null;
                hasOverride = false;
                LOGGER.info("[WeatherController] 没有天气轨道，清空激活轨道");
            }
            if (enabled) {
                setEnabled(false);
            }
        } else {
            // 有多个天气轨道时，检查当前激活轨道是否仍然在模型中
            if (activeTrack != null && !model.getTracks().contains(activeTrack)) {
                // 当前激活轨道已被删除，切换到第一条轨道（但不强制，因为可能有多个）
                activeTrack = firstWeatherTrack;
                LOGGER.info("[WeatherController] 当前激活轨道已被删除，切换到: {}", firstWeatherTrack.getName());
                rebuildFromActiveTrack();
            }
            // ⭐ 如果有激活轨道，确保控制器启用
            if (activeTrack != null && !enabled) {
                setEnabled(true);
            }
        }
    }

    // ==========================================================
    // 调用链入口（时间线驱动）
    // ==========================================================

    /**
     * ⭐ v1.1 重构：时间线播放 / 拖动时调用，传入当前时间，刷新天气状态。
     * <p>
     * 一般由 TimelinePanel / 播放控制中心 调用：
     *  - 播放时每 tick 调用
     *  - 拖动时间尺 / Scrubbing 时调用
     * 
     * <p>架构：
     * <ul>
     *   <li>采样 WeatherSample</li>
     *   <li>分发到各个子系统（粒子、雾、天空）</li>
     *   <li>事件子系统使用 tickEvents() 方法，需要在每 tick 循环中单独调用</li>
     * </ul>
     */
    public void updateForPlayback(float timelineTimeSeconds) {
        if (!enabled || activeTrack == null || sampler == null) {
            // 当未启用或无轨道时，不 override
            clearOverride();  // NOTE: 不强制恢复原版，只是停止覆盖。
            return;
        }

        // 采样当前时间的天气
        WeatherSample sample = sampler.sample(timelineTimeSeconds);
        this.currentSample = sample;
        this.hasOverride = true;

        // 分发到各个子系统
        applyToWorld(sample);
    }
    
    /**
     * ⭐ v1.1 新增：每 tick 调用事件子系统。
     * 
     * <p>事件子系统（雷暴、闪电、声音）需要在每 tick 调用，而不是仅在采样时调用。
     * 此方法应该由 TimelinePanel 的每 tick 循环调用。
     * 
     * @param timelineTimeSeconds 时间线时间（秒）
     */
    public void tickEvents(float timelineTimeSeconds) {
        if (!enabled || !hasOverride || currentSample == null) {
            return;
        }
        
        try {
            WeatherEventController.getInstance().tick(currentSample, timelineTimeSeconds);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法更新事件子系统", e);
        }
    }

    /**
     * 仅用于"预览"（例如拖动时间标尺时预览，但不一定处于播放态）。
     * 和 updateForPlayback 基本一致，只是语义上给 UI 看得更清楚。
     */
    public void previewAt(float timelineTimeSeconds) {
        updateForPlayback(timelineTimeSeconds);
    }

    /**
     * 时间线播放时调用（向后兼容方法）。
     * 
     * @deprecated 使用 {@link #updateForPlayback(float)} 代替
     */
    @Deprecated
    public void onTimelineTick(float timelineTime) {
        updateForPlayback(timelineTime);
    }

    /**
     * 初始化控制器（向后兼容方法）。
     * 
     * <p>注意：v1.0 版本不再需要绑定 TimelineModel，改为直接使用 bindActiveTrack()。
     * 此方法保留仅为向后兼容，实际不做任何操作。
     * 
     * @deprecated 使用 {@link #bindActiveTrack(TimelineTrack)} 代替
     */
    @Deprecated
    public void init(com.director.ui.timeline.model.TimelineModel model) {
        // v1.0 不再需要绑定模型，改为直接绑定轨道
        LOGGER.debug("[WeatherController] init() 已弃用，请使用 bindActiveTrack()");
    }

    /**
     * 外部系统（例如季节 / 天气混合 Mixin）需要知道：
     * 当前是否有天气 override。
     */
    public boolean hasOverride() {
        return hasOverride && currentSample != null && enabled;
    }

    /**
     * 当前天气采样结果（给 Mixin / 渲染层调用）。
     */
    public WeatherSample getCurrentSample() {
        return currentSample;
    }

    /**
     * 接管原版降水：将底层字段设为天气轨道的值。
     * <p>
     * 旧方案总是清零 rainGradient，再依赖 WorldMixin 在 getRainGradient()
     * 的 @At("RETURN") 覆盖返回值。但 MC 1.21.10 的 FrameGraph 渲染管线中
     * WeatherRendering.buildPrecipitationPieces 可能在某些路径缓存了原始值，
     * 导致 Mixin 覆盖不生效，雨线无法渲染。
     * <p>
     * 新方案：直接将 rainGradient / thunderGradient 设为轨道值，
     * 这样即使 Mixin 拦截没触发，底层字段也是正确的。
     * Mixin 仍然作为双保险保持不变。
     */
    public void forceDisableVanillaWeather() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        ClientWorld world = client.world;

        // 在有天气轨道且有有效 sample 时，将底层字段设为轨道值
        if (hasOverride && currentSample != null && currentSample.enabled) {
            float rain = currentSample.rainIntensity;
            float thunder = currentSample.thunderIntensity;

            world.setRainGradient(rain);
            world.setThunderGradient(thunder);
            // 只在真正有雨降水时才告诉原版"正在下雨"
            // 雷暴不需要设 raining（isThundering 单独处理）
            // 雪不需要设 raining（由 WeatherParticleController 自定义处理）
            world.getLevelProperties().setRaining(rain > 0.01f);
        } else {
            // 无天气轨道或 sample 无效时：清零，阻止原版随机天气
            world.setRainGradient(0.0f);
            world.setThunderGradient(0.0f);
            world.getLevelProperties().setRaining(false);
        }
    }

    // ==========================================================
    // 将 WeatherSample 应用到 Minecraft 世界
    // ==========================================================

    /**
     * ⭐ v1.1 重构：将 WeatherSample 分发到各个天气子系统。
     * 
     * <p>架构设计：
     * <ul>
     *   <li>1️⃣ WeatherParticleController - 粒子层（雨/雪粒子）</li>
     *   <li>2️⃣ WeatherFogController - 雾控制（指数雾、雾密度）⚠️ 已禁用</li>
     *   <li>3️⃣ WeatherSkyController - 天空/光照（天空亮度、云层）</li>
     *   <li>4️⃣ WeatherEventController - 事件（雷暴、闪电、声音）</li>
     * </ul>
     * 
     * <p>注意：雨/雷的梯度（WorldMixin）和雾/天空（Mixin）通过直接读取 getCurrentSample() 实现，
     * 这里只需要确保 currentSample 保持最新。
     */
    private void applyToWorld(WeatherSample sample) {
        if (sample == null || !sample.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }

        // -------------------------------
        // 1️⃣ 粒子子系统：雨/雪粒子生成
        // -------------------------------
        try {
            WeatherParticleController.getInstance().updateSample(sample);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法更新天气粒子", e);
        }

        // -------------------------------
        // 2️⃣ 雾子系统：指数雾、雾密度、雾颜色
        // -------------------------------
        try {
            // ⚠️ 雾功能已禁用
            // WeatherFogController.getInstance().apply(sample);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法应用雾控制", e);
        }

        // -------------------------------
        // 3️⃣ 天空子系统：天空亮度、云层
        // -------------------------------
        try {
            WeatherSkyController.getInstance().apply(sample);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法应用天空控制", e);
        }

        // -------------------------------
        // 4️⃣ 事件子系统：雷暴、闪电、声音
        // -------------------------------
        // 注意：事件子系统使用 tick() 方法，需要在每 tick 调用（而不是这里）
        // 这里只做初始化，实际事件逻辑在 TimelinePanel 的每 tick 循环中调用

        // -------------------------------
        // 5) 向后兼容：通知 WeatherRenderer 更新（未来可能迁移到事件子系统）
        // -------------------------------
        try {
            com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(sample);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法更新 WeatherRenderer", e);
        }

        // -------------------------------
        // 注意：以下效果通过 Mixin 直接读取 getCurrentSample() 实现：
        //   - 雨/雷梯度（WorldMixin.getRainGradient / getThunderGradient）
        //   - 雾颜色/密度（BackgroundRendererMixin.applyFog）
        //   - 天空亮度（ClientWorldMixin.getSkyBrightness / getStarBrightness）
        // 因此，这里必须保证 currentSample 始终保持最新。
    }

    // ==========================================================
    // 清理 / 重置逻辑
    // ==========================================================

    /**
     * 清除 override 状态（但不强制恢复 Vanilla 雨量）。
     * 一般用于：
     *  - 停止播放但仍在导演模式中
     *  - 暂停天气轨道接管
     */
    public void clearOverride() {
        this.hasOverride = false;
        this.currentSample = null;
        
        // 通知 WeatherRenderer 清除
        try {
            com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(null);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherRenderer", e);
        }
    }

    /**
     * ⭐ v1.1 重构：清空 override，并尝试恢复世界天气到"正常状态"。
     * 
     * <p>清理所有子系统状态：
     *  - 雨强度/雷暴强度 重置为 0（晴天）
     *  - 其他视觉效果（云/雾）由 Vanilla 自己随时间驱动
     */
    private void clearOverrideAndResetWorld() {
        this.hasOverride = false;
        this.currentSample = null;

        // 1️⃣ 清除粒子子系统
        try {
            WeatherParticleController.getInstance().updateSample(null);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherParticleController", e);
        }

        // 2️⃣ 清除雾子系统
        try {
            // ⚠️ 雾功能已禁用
            // WeatherFogController.getInstance().clear();
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherFogController", e);
        }

        // 3️⃣ 清除天空子系统
        try {
            WeatherSkyController.getInstance().clear();
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherSkyController", e);
        }

        // 4️⃣ 清除事件子系统
        try {
            WeatherEventController.getInstance().clear();
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherEventController", e);
        }

        // 5️⃣ 向后兼容：清除 WeatherRenderer
        try {
            com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(null);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherRenderer", e);
        }

        // 注意：雨/雷的恢复由 WorldMixin 自动处理（当 hasOverride() 返回 false 时）
        // 雾和云的恢复由 BackgroundRendererMixin 和 ClientWorldMixin 自动处理（当 hasOverride() 返回 false 时）
        LOGGER.info("[WeatherController] 清除天气 override，并尝试恢复 Vanilla 晴天状态");
    }

    /**
     * ⭐ v1.1 重构：清除天气覆盖状态，恢复游戏默认值。
     * 
     * <p>通常在关闭 Director 时调用，确保天气修改被清除，游戏恢复默认渲染。
     * 
     * <p>此方法会：
     * <ul>
     *   <li>禁用天气控制器</li>
     *   <li>清除所有 override 状态</li>
     *   <li>清理所有子系统（粒子、雾、天空、事件）</li>
     *   <li>确保 Mixin 不再应用天气效果（hasOverride() 将返回 false）</li>
     * </ul>
     */
    public void clear() {
        // 禁用控制器
        this.enabled = false;
        
        // 清空所有状态
        this.hasOverride = false;
        this.currentSample = null;
        this.activeTrack = null;
        this.sampler = null;

        // 1️⃣ 清除粒子子系统
        try {
            WeatherParticleController.getInstance().updateSample(null);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherParticleController", e);
        }

        // 2️⃣ 清除雾子系统
        try {
            // ⚠️ 雾功能已禁用
            // WeatherFogController.getInstance().clear();
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherFogController", e);
        }

        // 3️⃣ 清除天空子系统
        try {
            WeatherSkyController.getInstance().clear();
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherSkyController", e);
        }

        // 4️⃣ 清除事件子系统
        try {
            WeatherEventController.getInstance().clear();
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherEventController", e);
        }

        // 5️⃣ 向后兼容：清除 WeatherRenderer
        try {
            com.director.client.renderer.weather.WeatherRenderer.get().onWeatherSampleUpdated(null);
        } catch (Exception e) {
            LOGGER.debug("[WeatherController] 无法清除 WeatherRenderer", e);
        }

        LOGGER.info("[WeatherController] Cleared weather override state - 恢复游戏默认天气状态");
    }

}
