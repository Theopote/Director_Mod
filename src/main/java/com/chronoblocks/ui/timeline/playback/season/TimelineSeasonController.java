package com.chronoblocks.ui.timeline.playback.season;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimelineSeasonController v1.1
 * 
 * <p>负责驱动"季节轨道"（Season Track）的运行时逻辑：
 * <ul>
 *   <li>从激活的 SEASON 轨道收集 SeasonKeyframe</li>
 *   <li>使用 SeasonTrackSampler 在当前时间线时间进行插值采样</li>
 *   <li>将 SeasonSample 作为"当前季节状态"缓存，供渲染层 / Mixin 查询</li>
 *   <li>不直接修改世界时间，也不冻结时间系统，只提供气候/光照等长期趋势基线</li>
 * </ul>
 * 
 * <p>设计要点：
 * <ul>
 *   <li>支持多个 SEASON 轨道，但任意时刻只有一条为 active（与摄像机轨道类似）</li>
 *   <li>季节只作为"环境基线"，短期天气仍由 Weather Track 控制</li>
 *   <li>渲染层可通过 {@link #getCurrentSample()} 读取当前季节参数</li>
 *   <li>自动激活单一季节轨道（如果只有一条）</li>
 * </ul>
 * 
 * <p>与 Weather Track 的关系：
 * <ul>
 *   <li>季节轨道：控制长期趋势（渐变）</li>
 *   <li>天气轨道：控制短期事件（下雨、雷暴等）</li>
 *   <li>优先级：Weather Track > Season Track > Minecraft 原版</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.1 - 改进代码结构、添加 update(float) 重载、改进文档
 */
public final class TimelineSeasonController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineSeasonController.class);
    private static final TimelineSeasonController INSTANCE = new TimelineSeasonController();
    
    /** 所属的时间线模型 */
    private TimelineModel timelineModel;
    
    /** 当前激活的 SEASON 轨道（仅一条） */
    private TimelineTrack activeSeasonTrack;
    
    /** 负责插值的采样器 */
    private final SeasonTrackSampler sampler = new SeasonTrackSampler();
    
    /** 是否启用季节控制 */
    private boolean enabled = true;
    
    /** 模型变更监听器 */
    private TimelineModel.ModelChangeListener modelChangeListener;
    
    /** 最近一次采样到的季节状态（供渲染层 / Mixin 使用） */
    private SeasonSample currentSample = SeasonSample.disabled();
    
    private TimelineSeasonController() {
    }
    
    // ================== 单例访问 ==================
    
    public static TimelineSeasonController getInstance() {
        return INSTANCE;
    }
    
    /** 便捷别名，供 Mixin 使用 */
    public static TimelineSeasonController get() {
        return INSTANCE;
    }
    
    // ================== 初始化 / 绑定模型 ==================
    
    /**
     * 绑定 / 切换 TimelineModel。
     * 
     * <p>通常在时间线 UI 初始化或加载工程时调用。
     * 会自动注册 ModelChangeListener，在模型变更时刷新季节关键帧。
     * ⭐ v1.0：会自动初始化 activeSeasonTrack（选择第一条 SEASON 轨道）。
     * 
     * @deprecated 使用 {@link #init(TimelineModel)} 代替，但为了向后兼容保留
     */
    @Deprecated
    public synchronized void setTimelineModel(TimelineModel model) {
        init(model);
    }
    
    /**
     * 绑定 / 切换 TimelineModel。
     * 
     * <p>通常在时间线 UI 初始化或加载工程时调用。
     */
    public synchronized void init(TimelineModel model) {
        if (this.timelineModel == model) {
            return;
        }
        
        // 解除旧模型监听
        if (this.timelineModel != null && this.modelChangeListener != null) {
            this.timelineModel.removeModelChangeListener(modelChangeListener);
        }
        
        this.timelineModel = model;
        
        if (model != null) {
            if (modelChangeListener == null) {
                modelChangeListener = changeType -> {
                    // ⭐ 记录所有模型变更（使用 INFO 级别，确保能看到）
                    LOGGER.info("[TimelineSeason] 收到模型变更通知: {}", changeType);
                    // 立即处理模型变更，确保删除关键帧/轨道时立即恢复
                    onModelStructureChanged();
                };
            }
            model.addModelChangeListener(modelChangeListener);
            
            // 选择第一条 SEASON 轨道作为激活轨道
            if (!isValidSeasonTrack(activeSeasonTrack)) {
                activeSeasonTrack = findFirstSeasonTrack();
                
                // ⭐ v1.0：如果只有一个季节轨道，自动激活它
                int seasonTrackCount = countSeasonTracks();
                if (seasonTrackCount == 1 && activeSeasonTrack != null) {
                    LOGGER.info("[TimelineSeason] 检测到只有一个季节轨道，自动激活: {}", activeSeasonTrack.getName());
                }
            }
            
            rebuildFromActiveTrack();
            LOGGER.info("[TimelineSeason] init 完成，activeSeasonTrack = {}",
                    activeSeasonTrack != null ? activeSeasonTrack.getName() : "<none>");
        } else {
            sampler.clear();
            activeSeasonTrack = null;
            currentSample = SeasonSample.disabled();
            LOGGER.info("[TimelineSeason] init(null) → 关闭季节控制");
        }
    }
    
    // ================== 轨道选择 / 管理 ==================
    
    /**
     * 获取当前激活的季节轨道。
     */
    public synchronized TimelineTrack getActiveSeasonTrack() {
        return activeSeasonTrack;
    }
    
    /**
     * 设置当前激活的季节轨道。
     * 
     * <p>通常由轨道头部的"选择季节轨道"按钮触发。
     */
    public synchronized void setActiveSeasonTrack(TimelineTrack track) {
        if (track != null && track.getType() != TrackType.SEASON) {
            LOGGER.warn("[TimelineSeason] setActiveSeasonTrack: 非 SEASON 轨道被选中，忽略: {}",
                    track.getName());
            return;
        }
        if (track == activeSeasonTrack) {
            return;
        }
        
        this.activeSeasonTrack = track;
        LOGGER.info("[TimelineSeason] 激活季节轨道: {}",
                track != null ? track.getName() : "<none>");
        
        rebuildFromActiveTrack();
    }
    
    /**
     * 启用/禁用季节控制。
     * 
     * <p>禁用时不会再从 SeasonTrack 采样，也不会更新 currentSample。
     */
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            currentSample = SeasonSample.disabled();
        }
        LOGGER.info("[TimelineSeason] 季节控制 {}", enabled ? "已启用" : "已禁用");
    }
    
    public synchronized boolean isEnabled() {
        return enabled;
    }
    
    // ================== 播放 / 时间推进 ==================
    
    /**
     * 每 tick 更新季节状态（保留向后兼容）。
     * 
     * @deprecated 使用 {@link #onTimelineTick(float)} 代替
     */
    @Deprecated
    public synchronized void update() {
        if (!enabled || timelineModel == null) {
            return;
        }
        
        if (!hasValidActiveTrack()) {
            return;
        }
        
        // 只有在播放或 scrubbing 时才自动更新季节状态
        boolean playing = timelineModel.isPlaying();
        boolean scrubbing = timelineModel.isScrubbing();
        
        if (!playing && !scrubbing) {
            return;
        }
        
        float currentTime = timelineModel.getCurrentTime();
        onTimelineTick(currentTime);
    }
    
    /**
     * 时间线在播放或拖动播放头时调用（v3.0 新 API）。
     * 
     * <p>这是季节系统的统一入口，所有季节相关的更新都通过这里。
     * 内部会调用 {@link #propagateToWorld()} 推送到雪系统、天空、草色等。
     * 
     * @param timelineTime 当前时间线时间（秒）
     */
    public synchronized void onTimelineTick(float timelineTime) {
        if (!enabled || timelineModel == null) {
            // 没有启用或没有模型，清除状态
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            return;
        }
        
        if (!hasValidActiveTrack() || sampler.isEmpty()) {
            // 没有激活轨道或没有关键帧，清除状态
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            return;
        }
        
        // 从采样器获取当前时间的季节采样（使用 v3.0 API）
        SeasonSample sample = sampler.sampleAt(timelineTime);
        setCurrentSample(sample);
        
        // 推送到世界系统
        propagateToWorld();
    }
    
    /**
     * 显式更新（带参数版本），便于从外部系统直接驱动。
     * 
     * @deprecated 使用 {@link #onTimelineTick(float)} 代替
     */
    @Deprecated
    public synchronized void update(float currentTime) {
        onTimelineTick(currentTime);
    }
    
    /**
     * 在指定时间进行预览（不改变播放状态）。
     * 
     * <p>例如：右键季节关键帧菜单中的"跳转到此季节状态"
     */
    public synchronized void previewAt(float timeSeconds) {
        if (!enabled || !hasValidActiveTrack()) {
            return;
        }
        
        SeasonSample sample = sampler.sampleAt(timeSeconds);
        if (sample.enabled) {
            setCurrentSample(sample);
            propagateToWorld();
            LOGGER.debug("[TimelineSeason] 预览季节 @ {}s: {}", timeSeconds, sample);
        }
    }

    /**
     * 在当前时间线时间进行预览（例如属性面板中点击"预览当前时间季节状态"）。
     */
    public synchronized void previewAtCurrentTime() {
        if (timelineModel == null) {
            return;
        }
        previewAt(timelineModel.getCurrentTime());
    }

    /**
     * 预览指定关键帧时间点的季节状态。
     * 用于属性面板修改后立即预览
     */
    public synchronized void previewAtKeyframeTime(float keyframeTime) {
        if (!enabled || !hasValidActiveTrack()) {
            return;
        }
        
        SeasonSample sample = sampler.sampleAt(keyframeTime);
        if (sample.enabled) {
            setCurrentSample(sample);
            propagateToWorld();
            LOGGER.debug("[TimelineSeason] 预览关键帧季节 @ {}s: {}", keyframeTime, sample);
        }
    }
    
    // ================== 模型变更 / 关键帧刷新 ==================
    
    /**
     * 属性面板修改关键帧时调用，或模型结构变更后重建。
     * 
     * <p>重新生成 SeasonTrackSampler 的内部缓存，并保持当前时间。
     */
    public synchronized void rebuildFromActiveTrack() {
        if (!hasValidActiveTrack()) {
            sampler.clear();
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            // 立即清理积雪和冰块（没有激活轨道时）
            cleanupSeasonEffects();
            return;
        }
        
        // 使用 v3.0 API：直接从轨道设置
        sampler.setFromTrack(activeSeasonTrack);
        
        // 使用当前时间线时间重新采样并推送
        if (timelineModel != null && !sampler.isEmpty()) {
            float currentTime = timelineModel.getCurrentTime();
            SeasonSample sample = sampler.sampleAt(currentTime);
            setCurrentSample(sample);
            propagateToWorld();
            LOGGER.debug("[TimelineSeason] 重新构建轨道，已更新当前时间点的季节状态");
        } else {
            // 没有关键帧，恢复到默认并立即清理积雪和冰块
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            cleanupSeasonEffects();
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[TimelineSeason] Rebuild from track '{}', keyframes = {}",
                    activeSeasonTrack != null ? activeSeasonTrack.getName() : "<none>", 
                    sampler.getKeyframes().size());
        }
    }
    
    /**
     * 处理时间线模型结构变更（轨道增删、关键帧变化等）。
     * 
     * <p>由 {@link TimelineModel.ModelChangeListener} 调用。
     */
    private synchronized void onModelStructureChanged() {
        if (timelineModel == null) {
            LOGGER.warn("[TimelineSeason] onModelStructureChanged() - timelineModel 为 null，跳过处理");
            return;
        }
        
        LOGGER.info("[TimelineSeason] onModelStructureChanged() - 开始处理模型结构变更");
        
        // 统计当前季节轨道数量
        int seasonTrackCount = countSeasonTracks();
        TimelineTrack firstSeasonTrack = findFirstSeasonTrack();
        
        LOGGER.info("[TimelineSeason] 当前季节轨道数量: {}, 激活轨道: {}", 
                seasonTrackCount, activeSeasonTrack != null ? activeSeasonTrack.getName() : "<null>");
        
        // ⭐ v1.0：如果只有一个季节轨道，无论当前状态如何，都确保它是激活的
        if (seasonTrackCount == 1 && firstSeasonTrack != null) {
            if (activeSeasonTrack != firstSeasonTrack) {
                activeSeasonTrack = firstSeasonTrack;
                LOGGER.info("[TimelineSeason] 模型变更：检测到只有一个季节轨道，自动激活: {}", firstSeasonTrack.getName());
            }
        } else if (seasonTrackCount == 0) {
            // 没有季节轨道时，清空激活轨道
            activeSeasonTrack = null;
        } else {
            // 有多个季节轨道时，检查当前激活轨道是否仍然在模型中
            // 如果当前激活轨道不在模型中（被删除），或者无效，切换到第一条轨道
            if (activeSeasonTrack != null && !isSeasonTrackInModel(activeSeasonTrack)) {
                LOGGER.info("[TimelineSeason] 模型变更：当前激活轨道已被删除，切换到: {}", 
                        firstSeasonTrack != null ? firstSeasonTrack.getName() : "<none>");
                activeSeasonTrack = firstSeasonTrack;
            } else if (!isValidSeasonTrack(activeSeasonTrack)) {
                activeSeasonTrack = firstSeasonTrack;
                if (firstSeasonTrack != null) {
                    LOGGER.info("[TimelineSeason] 模型变更：当前激活轨道无效，切换到: {}", firstSeasonTrack.getName());
                }
            }
        }
        
        rebuildFromActiveTrack();
        
        // 记录模型变更后的状态，使用 info 级别以便调试
        LOGGER.info("[TimelineSeason] 模型变更完成，激活轨道: {}, 关键帧数: {}",
                activeSeasonTrack != null ? activeSeasonTrack.getName() : "<none>",
                sampler.hasKeyframes() ? "有" : "无");
    }
    
    /**
     * 绑定 TimelineModel（v4.0 API，别名）。
     */
    public void bindTimelineModel(TimelineModel model) {
        init(model);
    }

    // ================== 采样 / 应用 ==================
    
    /**
     * 将当前季节采样推送到世界系统（v3.0 新 API）。
     * 
     * <p>统一的数据流出口，所有依赖季节的系统都在这里接收更新：
     * <ul>
     *   <li>雪系统（SeasonSnowManager）</li>
     *   <li>天空、雾、草色等通过 Mixin 读取 currentSample，无需显式推送</li>
     * </ul>
     */
    private synchronized void propagateToWorld() {
        boolean hasOverride = hasOverride();
        if (hasOverride && currentSample != null) {
            // 雪系统
            try {
                com.chronoblocks.season.SeasonSnowManager.get().onSeasonSampleUpdated(currentSample);
            } catch (Exception e) {
                LOGGER.warn("[TimelineSeason] Failed to propagate to SeasonSnowManager", e);
            }
            
            // 冰冻水面系统
            try {
                com.chronoblocks.season.WaterFreezeManager.get().onSeasonSampleUpdated(currentSample);
            } catch (Exception e) {
                LOGGER.warn("[TimelineSeason] Failed to propagate to WaterFreezeManager", e);
            }
            
            // 天空、雾、草叶色已经在 mixin 通过 currentSample 读取，无需这里显式应用
        } else {
            // 没有覆盖时，通知所有系统清除
            try {
                com.chronoblocks.season.SeasonSnowManager.get().onSeasonSampleUpdated(null);
            } catch (Exception e) {
                LOGGER.warn("[TimelineSeason] Failed to clear SeasonSnowManager", e);
            }
            
            try {
                com.chronoblocks.season.WaterFreezeManager.get().onSeasonSampleUpdated(null);
            } catch (Exception e) {
                LOGGER.warn("[TimelineSeason] Failed to clear WaterFreezeManager", e);
            }
        }
    }

    /**
     * 设置当前季节状态，并在必要时触发渲染更新。
     */
    private void setCurrentSample(SeasonSample sample) {
        // ⭐ 关键：先设置 currentSample，确保后续的 hasOverride() 检查能看到新值
        this.currentSample = sample;
        
        // 强制刷新渲染，让颜色变化立即生效
        // 由于生物群系颜色可能被缓存，我们需要触发一次区域重渲染
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null && client.worldRenderer != null) {
            try {
                // 获取玩家当前位置和视野范围
                net.minecraft.util.math.BlockPos playerPos = client.player != null 
                        ? client.player.getBlockPos() 
                        : new net.minecraft.util.math.BlockPos(0, 64, 0); // 如果没有玩家，使用默认位置
                
                int renderDistance = client.player != null 
                        ? Math.max(client.options.getViewDistance().getValue(), 8) 
                        : 8; // 至少 8 区块
                int chunkRadius = renderDistance * 16; // 转换为方块单位
                
                // 计算可见区域的边界（使用 Minecraft 1.21 的标准高度范围：-64 到 320）
                int minX = playerPos.getX() - chunkRadius;
                int minY = Math.max(playerPos.getY() - 64, -64);
                int minZ = playerPos.getZ() - chunkRadius;
                int maxX = playerPos.getX() + chunkRadius;
                int maxY = Math.min(playerPos.getY() + 256, 320);
                int maxZ = playerPos.getZ() + chunkRadius;
                
                // ⭐ 强制刷新：无论样本是否变化，都调度重渲染（特别是恢复默认时）
                // 调度可见区域的区块重渲染，强制重新计算生物群系颜色
                client.worldRenderer.scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
                
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[TimelineSeason] 已调度区域重渲染: ({}, {}, {}) 到 ({}, {}, {}) - hasOverride() = {}", 
                            minX, minY, minZ, maxX, maxY, maxZ, hasOverride());
                }
            } catch (Exception e) {
                LOGGER.warn("[TimelineSeason] 调度区域重渲染失败: {}", e.getMessage(), e);
            }
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[TimelineSeason] 无法刷新渲染：客户端、世界或渲染器为 null");
            }
        }
    }
    
    // ================== 提供给渲染层 / Mixin 的接口 ==================
    
    /**
     * 检查是否有季节覆盖。
     * 
     * <p>供 Mixin 使用，判断是否应该应用季节颜色。
     * 
     * <p>判断标准：
     * <ul>
     *   <li>季节控制已启用</li>
     *   <li>有有效的激活轨道</li>
     *   <li>采样器中有关键帧（关键修复：没有关键帧时不应用覆盖）</li>
     *   <li>当前样本不为 null</li>
     *   <li>当前样本不是默认样本（所有 tint 为 0）</li>
     * </ul>
     */
    public synchronized boolean hasOverride() {
        if (!enabled) {
            return false;
        }
        
        // ⭐ 关键修复：如果没有激活轨道，直接返回 false（即使 currentSample 可能不为 null）
        if (!hasValidActiveTrack()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineSeason] hasOverride() = false (没有激活轨道)");
            }
            return false;
        }
        
        // ⭐ 关键修复：如果采样器中没有关键帧，也返回 false（没有关键帧意味着没有季节覆盖）
        if (!sampler.hasKeyframes()) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[TimelineSeason] hasOverride() = false (采样器中没有关键帧)");
            }
            return false;
        }
        
        if (currentSample == null) {
            return false;
        }
        
        // 检查是否有任何非零的 tint（使用偏移值语义）
        Vector3f zero = new Vector3f(0f, 0f, 0f);
        boolean result = !currentSample.grassTint.equals(zero) ||
               !currentSample.foliageTint.equals(zero) ||
               !currentSample.skyTint.equals(zero) ||
               !currentSample.fogTint.equals(zero) ||
               !currentSample.lightTint.equals(zero);
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[TimelineSeason] hasOverride() = {} (有激活轨道，有关键帧，tint检查: {})", 
                    result, result ? "有非零tint" : "所有tint为零");
        }
        
        return result;
    }
    
    /**
     * 提供给渲染层 / Mixin 的当前季节采样结果。
     * 
     * <p>如果暂时没有任何季节轨道，则返回最后一次的 sample 或默认 sample。
     * 
     * <p>这是 Mixin 读取季节状态的主要接口。
     */
    public synchronized SeasonSample getCurrentSample() {
        if (currentSample == null) {
            currentSample = SeasonSample.disabled();
        }
        return currentSample;
    }
    
    /**
     * 直接获取当前采样（如果不存在则返回默认值）。
     * 
     * <p>便捷方法，供 Mixin 使用（兼容现有代码）。
     * 
     * @deprecated 使用 {@link #getCurrentSample()} 代替
     */
    @Deprecated
    public synchronized SeasonSample current() {
        return getCurrentSample();
    }
    
    /**
     * 清除季节覆盖状态，恢复游戏默认值。
     * 
     * <p>通常在关闭 ChronoBlocks 时调用，确保季节修改被清除，游戏恢复默认渲染。
     * 
     * <p>此方法会：
     * <ul>
     *   <li>将 currentSample 重置为默认值（所有 tint = 0，无季节偏移）</li>
     *   <li>清空采样器的关键帧</li>
     *   <li>强制刷新渲染，确保颜色立即恢复到默认</li>
     *   <li>确保 Mixin 不再应用季节颜色（hasOverride() 将返回 false）</li>
     * </ul>
     * 
     * <p>注意：此方法不会禁用季节控制，只是清除当前状态。如果需要禁用，请调用 {@link #setEnabled(boolean)}。
     */
    public synchronized void clear() {
        // 清空采样器关键帧
        sampler.clear();
        
        // 使用 setCurrentSample() 确保触发渲染刷新，立即恢复到默认颜色
        setCurrentSample(SeasonSample.disabled());
        propagateToWorld();
        
        // 立即清理所有积雪和冰块
        cleanupSeasonEffects();
        
        LOGGER.info("[TimelineSeason] Cleared season override state - 恢复游戏默认渲染");
    }
    
    /**
     * 清理所有季节效果（积雪和冰块）。
     * 在删除关键帧、删除轨道或关闭 ChronoBlocks 时调用。
     */
    private void cleanupSeasonEffects() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                com.chronoblocks.season.SeasonSnowManager.get().restoreAll(client.world);
                com.chronoblocks.season.WaterFreezeManager.get().restoreAll(client.world);
                LOGGER.info("[TimelineSeason] 已清理所有季节效果（积雪 + 冰冻水面）");
            }
        } catch (Exception e) {
            LOGGER.warn("[TimelineSeason] 清理季节效果时出错", e);
        }
    }
    
    // ================== 辅助方法 ==================
    
    /**
     * 检查是否有有效的激活季节轨道。
     */
    private boolean hasValidActiveTrack() {
        return isValidSeasonTrack(activeSeasonTrack);
    }
    
    /**
     * 验证轨道是否是有效的 SEASON 轨道。
     */
    private boolean isValidSeasonTrack(TimelineTrack track) {
        if (track == null) return false;
        return track.getType() == TrackType.SEASON;
    }
    
    /**
     * 找到第一条 SEASON 轨道（用于默认激活）。
     */
    private TimelineTrack findFirstSeasonTrack() {
        if (timelineModel == null) return null;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == TrackType.SEASON) {
                return track;
            }
        }
        return null;
    }
    
    /**
     * 检查轨道是否仍在模型中（未被删除）。
     */
    private boolean isSeasonTrackInModel(TimelineTrack track) {
        if (track == null || timelineModel == null) {
            return false;
        }
        return timelineModel.getTracks().contains(track);
    }
    
    /**
     * 统计 SEASON 轨道数量。
     */
    private int countSeasonTracks() {
        if (timelineModel == null) return 0;
        int count = 0;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == TrackType.SEASON) {
                count++;
            }
        }
        return count;
    }
}
