package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.easing.EasingFunction;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.transform.AnimationTransformerFactory;
import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.container.AnimationMode;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * BlockAnimationEngine
 * --------------------
 * 负责把：
 *   - 时间线时间 (now)
 *   - 片段配置 ClipAnimationConfig（mode/duration/easing/params/effects）
 *   - 序列行为 BlockSequenceEntry（seqNormalizedTime）
 * <p>
 * 映射成单个方块在当前帧的动画采样结果 BlockAnimationSnapshot。
 * <p>
 * 约定：
 *   - 所有时间单位统一用秒（与 Timeline 一致）
 *   - sequenceEntry.normalizedTime 为归一化 [0..1]，决定该方块在片段中的"出场顺序"
 *   - Snapshot.transform 只保存"局部动画矩阵"（围绕方块中心），不包含世界坐标
 *     世界坐标由 Renderer 统一负责：先 translate(pos)，再乘 transform
 *
 * @author ChronoBlocks Team
 * @version 2.2
 */
public final class BlockAnimationEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnimationEngine.class);

    private BlockAnimationEngine() {
    }

    /**
     * 运行时特效触发状态（用于 ONE_SHOT 等跨帧门控）。
     *
     * <p>键：pos + effectId
     * 值：上一帧该方块在单块动画窗口内的 t_raw（0..1），以及最近一次 seen 时间用于清理。
     */
    private static final class EffectRuntimeGate {
        private static final class State {
            float lastTRaw;
            float lastSeenNow;
            boolean initialized;
        }

        private static final java.util.Map<Key, State> STATES = new java.util.HashMap<>();
        private static float lastCleanupNow = Float.NEGATIVE_INFINITY;

        private record Key(long posLong, java.util.UUID effectId) {
        }

        static State get(long posLong, java.util.UUID effectId, float now) {
            Key k = new Key(posLong, effectId);
            State s = STATES.get(k);
            if (s == null) {
                s = new State();
                s.initialized = false;
                s.lastTRaw = 0.0f;
                s.lastSeenNow = now;
                STATES.put(k, s);
            } else {
                s.lastSeenNow = now;
            }
            // 简单清理：每 2s 扫一次，移除 10s 没见到的 key
            if (now - lastCleanupNow > 2.0f) {
                lastCleanupNow = now;
                float expire = now - 10.0f;
                STATES.entrySet().removeIf(e -> e.getValue() == null || e.getValue().lastSeenNow < expire);
            }
            return s;
        }
    }

    /**
     * 对单个方块进行采样，生成一帧的动画快照。
     *
     * @param pos        方块世界坐标
     * @param state      方块状态
     * @param config     片段动画配置（ClipAnimationConfig）
     * @param seqEntry   序列条目（可为 null，此时 seqNormalizedTime=0）
     * @param clipStart  片段开始时间（世界时间轴）
     * @param clipEnd    片段结束时间（世界时间轴）
     * @param now        当前时间（世界时间轴）
     * @param randomSeed 本方块随机种子（建议 containerSeed ^ pos.asLong()）
     */
    public static BlockAnimationSnapshot sample(BlockPos pos,
                                                BlockState state,
                                                ClipAnimationConfig config,
                                                BlockSequenceEntry seqEntry,
                                                float clipStart,
                                                float clipEnd,
                                                float now,
                                                long randomSeed) {
        BlockAnimationSnapshot snapshot = new BlockAnimationSnapshot(pos, state);

        // ===== 0. 无配置：纯瞬时渲染 =====
        if (config == null) {
            if (now >= clipStart) {
                snapshot.markFullyVisible();
                snapshot.setTransform(null); // null = identity
            } else {
                snapshot.markHidden();
            }
            // 没有 config 就没有 effect，直接返回
            return snapshot;
        }

        AnimationMode mode = config.getMode();
        if (mode == null) {
            mode = AnimationMode.INSTANT;
        }
        ClipAnimationConfig.AnimationPhase phase = config.getPhase();

        // ===== INSTANT 模式特殊处理：直接按序列出现，不需要动画窗口 =====
        if (mode == AnimationMode.INSTANT) {
            float delay = Math.max(0.0f, config.getDelay());
            try {
                var seq = config.getSequenceParameters();
                if (seq != null) {
                    delay += Math.max(0.0f, seq.getGlobalDelay());
                }
            } catch (Exception ignored) {
            }
            
            float seqNormalizedTime = (seqEntry != null)
                    ? MathHelper.clamp(seqEntry.getNormalizedTime(), 0.0f, 1.0f)
                    : 0.0f;
            
            float clipDuration = Math.max(0.0001f, clipEnd - clipStart);
            // INSTANT 模式：方块按照序列顺序直接出现，不需要动画窗口
            float sequenceSpan = Math.max(0.0001f, clipDuration - delay);
            float blockStartTime = clipStart + delay + seqNormalizedTime * sequenceSpan;
            
            // 钳制到片段范围
            if (blockStartTime < clipStart) blockStartTime = clipStart;
            if (blockStartTime > clipEnd) blockStartTime = clipEnd;
            
            if (phase == ClipAnimationConfig.AnimationPhase.EXIT) {
                // EXIT：在 blockStartTime 之前完全可见，之后隐藏（由上层移除）
                if (now >= blockStartTime) {
                    snapshot.markHidden();
                } else {
                    snapshot.markFullyVisible();
                    snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                    snapshot.setTransform(null); // null = identity
                    snapshot.setAlpha(1.0f);
                    // EXIT 阶段不应用特效（因为方块即将被移除）
                }
            } else {
                // ENTER：在 blockStartTime 之前隐藏，之后完全可见
                if (now >= blockStartTime) {
                    snapshot.markFullyVisible();
                    snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                    snapshot.setTransform(null); // null = identity
                    snapshot.setAlpha(1.0f);
                    // 允许触发特效（虽然 INSTANT 模式通常不需要特效，但保留兼容性）
                    applyEffectsSafe(snapshot, config, now, randomSeed);
                } else {
                    snapshot.markHidden();
                }
            }
            return snapshot;
        }

        float clipDuration = Math.max(0.0001f, clipEnd - clipStart);
        float animDuration = Math.max(0.0001f, Math.min(config.getDuration(), clipDuration));
        float delay = Math.max(0.0f, config.getDelay());
        try {
            var seq = config.getSequenceParameters();
            if (seq != null) {
                delay += Math.max(0.0f, seq.getGlobalDelay());
            }
        } catch (Exception ignored) {
        }
        
        // 性能敏感：sample() 会被每帧对大量方块调用，禁止使用 info 级日志刷屏。
        // 如需调试请改用 trace 并自行开启日志级别。
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationEngine] sample: pos={} mode={} duration={} clipStart={} clipEnd={} now={}",
                    pos, mode, animDuration, clipStart, clipEnd, now);
        }

        // ===== 1. 序列归一化时间 =====
        float seqNormalizedTime = (seqEntry != null)
                ? MathHelper.clamp(seqEntry.getNormalizedTime(), 0.0f, 1.0f)
                : 0.0f;

        // ===== 2. 计算单个方块的动画时间窗口 =====
        // ENTER：方块从自己的 startTime 起进入世界并播放，endTime 后静态就位
        // EXIT ：方块在自己的 startTime 起开始退出动画，endTime 后应当被移除（由上层负责从世界中移除）
        float blockStartTime;
        float blockEndTime;
        float baseSpan = clipDuration - animDuration - delay;
        boolean fullWindow = baseSpan > 0.0001f;
        float sequenceSpan = fullWindow ? baseSpan : Math.max(0.0001f, clipDuration - delay);
        if (phase == ClipAnimationConfig.AnimationPhase.EXIT) {
            // 结束时刻分布在 [clipStart+delay+animDuration, clipEnd]
            blockEndTime = clipStart + delay + (fullWindow ? animDuration : 0.0f) + seqNormalizedTime * sequenceSpan;
            blockStartTime = blockEndTime - animDuration;
        } else {
            // ENTER：开始时刻分布在 [clipStart+delay, clipEnd-animDuration]
            float blockBaseOffset = seqNormalizedTime * sequenceSpan;
            blockStartTime = clipStart + delay + blockBaseOffset;
            blockEndTime = blockStartTime + animDuration;
        }

        // 钳制到片段范围
        if (blockStartTime < clipStart) blockStartTime = clipStart;
        if (blockEndTime > clipEnd) blockEndTime = clipEnd;

        if (blockEndTime <= blockStartTime) {
            // 动画时间窗口无效：按瞬时处理，但仍然允许触发 effect
            if (now >= blockStartTime) {
                snapshot.markFullyVisible();
                snapshot.setTransform(null); // null = identity
                snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                snapshot.setAlpha(1.0f);
                applyEffectsSafe(snapshot, config, now, randomSeed);
            } else {
                snapshot.markHidden();
            }
            return snapshot;
        }

        // ===== 3. 根据 phase 与 [blockStartTime, blockEndTime] 判断可见性 =====
        if (phase == ClipAnimationConfig.AnimationPhase.EXIT) {
            // EXIT：动画开始前应当完全可见（由上层控制“仍在 visibleBlocks”）
            if (now < blockStartTime) {
                snapshot.markFullyVisible();
                snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                snapshot.setTransform(null); // null = identity
                snapshot.setAlpha(1.0f);
                // ✅ 修复：即使动画还没开始，如果设置了特效，也应该检查并触发（特别是前段特效）
                snapshot.setRawProgress(0.0f);
                snapshot.setEasedProgress(0.0f);
                applyEffectsSafe(snapshot, config, now, randomSeed);
                return snapshot;
            }
        } else {
            // ENTER：动画开始前隐藏
            if (now < blockStartTime) {
                snapshot.markHidden();
                // ✅ 修复：即使动画还没开始，如果设置了特效，也应该检查并触发（特别是前段特效）
                snapshot.setRawProgress(0.0f);
                snapshot.setEasedProgress(0.0f);
                applyEffectsSafe(snapshot, config, now, randomSeed);
                return snapshot;
            }
        }

        // 动画已结束：端点状态
        if (now >= blockEndTime) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[BlockAnimationEngine] finished: pos={} mode={} now={} blockEndTime={}",
                        pos, mode, now, blockEndTime);
            }
            if (phase == ClipAnimationConfig.AnimationPhase.EXIT) {
                // EXIT：动画结束后应当隐藏（上层会在同一时刻把方块从世界中移除）
                snapshot.markHidden();
            } else {
                snapshot.markFullyVisible();
                snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                snapshot.setTransform(null); // null = identity
                snapshot.setAlpha(1.0f);
                // ✅ 修复：动画结束后，设置 rawProgress = 1.0，允许后段特效触发（如 DUST_BURST）
                snapshot.setRawProgress(1.0f);
                snapshot.setEasedProgress(1.0f);
            }
            // ✅ 修复：动画结束后仍然检查并触发特效（特别是后段特效，如 DUST_BURST）
            applyEffectsSafe(snapshot, config, now, randomSeed);
            return snapshot;
        }

        // ===== 4. 计算归一化时间 t_raw, t_eased =====
        float tRaw = (now - blockStartTime) / (blockEndTime - blockStartTime);
        tRaw = MathHelper.clamp(tRaw, 0.0f, 1.0f);

        EasingFunction easing = EasingFunction.LINEAR;
        try {
            easing = EasingFunction.fromString(config.getEasing());
        } catch (Exception e) {
            // 保持 LINEAR 即可，不需要打日志避免刷屏
        }
        float tEased = easing.apply(tRaw);

        // EXIT 阶段默认反向播放（让“入场变换器”自动变成“退场变换器”）
        // 但某些 mode 本身就是退场语义（如 FADE_OUT），则不反转。
        float tForTransformer = tEased;
        if (phase == ClipAnimationConfig.AnimationPhase.EXIT
                && mode != AnimationMode.FADE_OUT
                && mode != AnimationMode.EXPLODE
                && mode != AnimationMode.IMPLODE) {
            tForTransformer = 1.0f - tEased;
        }

        snapshot.setVisible(true);
        snapshot.setRawProgress(tRaw);
        snapshot.setEasedProgress(tEased);
        snapshot.setSequenceNormalizedTime(seqNormalizedTime);
        snapshot.setAlpha(1.0f); // 初始 alpha=1，后续由动画/特效调整
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationEngine] animating: pos={} mode={} tRaw={} tEased={}",
                    pos, mode, tRaw, tEased);
        }

        // ===== 5. 生成局部变换矩阵（调用 IAnimationTransformer） =====
        MatrixStack matrices = new MatrixStack();
        // 围绕方块中心做局部变换
        matrices.translate(0.5f, 0.5f, 0.5f);

        IAnimationTransformer transformer =
                AnimationTransformerFactory.getInstance().getTransformer(mode);
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationEngine] transformer: mode={} transformer={}",
                    mode, transformer != null ? transformer.getClass().getSimpleName() : "null");
        }
        
        if (transformer != null) {
            // ⚠️ 防御：config.getParams() 可能为 null
            Map<String, Object> baseParams =
                    config.getParams();
            Map<String, Object> tempParams = new java.util.HashMap<>(baseParams);

            // 合并默认参数
            Map<String, Object> defaultParams = transformer.getDefaultParams();
            if (defaultParams != null) {
                for (Map.Entry<String, Object> entry : defaultParams.entrySet()) {
                    tempParams.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }

            Random rand = Random.create(randomSeed ^ pos.asLong());

            try {
                // Minecraft Random → java.util.Random
                java.util.Random javaRandom = new java.util.Random(rand.nextLong());
                transformer.apply(tForTransformer, tempParams, javaRandom);

                // 从 params 中提取变换参数并应用到 MatrixStack
                applyTransformFromParams(matrices, tempParams);

                // 更新 snapshot 的 alpha（如果 transformer 写入了 alpha）
                Object alphaObj = tempParams.get("alpha");
                if (alphaObj instanceof Number) {
                    snapshot.setAlpha(((Number) alphaObj).floatValue());
                }
            } catch (Exception e) {
                LOGGER.error("动画 transformer 应用失败: mode={} pos={} err={}",
                        mode, pos, e.getMessage(), e);
            }
        }

        // ===== 5.1 动画修饰（属于“动画层”，不是特效层）=====
        // 旧 UI 中的“透明度淡入(opacityFade)”此前只是写入 params，但运行时不生效；
        // 这里把它落地：在 ENTER 阶段按 t_raw 做一个额外的 alpha 渐入乘法。
        try {
            if (phase == ClipAnimationConfig.AnimationPhase.ENTER) {
                Map<String, Object> p = config.getParams();
                Object enabledObj = p.get("opacityFade");
                boolean enabled = (enabledObj instanceof Boolean b) ? b
                        : (enabledObj instanceof String s && Boolean.parseBoolean(s));
                if (enabled) {
                    float intensity = getFloat(p, "opacityFadeIntensity", 1.0f);
                    intensity = Math.max(0.0001f, intensity);
                    // 约定：>1 更慢，<1 更快
                    float fade = MathHelper.clamp(tRaw / intensity, 0.0f, 1.0f);
                    snapshot.setAlpha(snapshot.getAlpha() * fade);
                }
            }
        } catch (Exception ignored) {
        }

        // 回到方块左下角
        matrices.translate(-0.5f, -0.5f, -0.5f);
        snapshot.setTransform(matrices);

        // ===== 6. 应用特效（粒子 / 其他可视效果） =====
        applyEffectsSafe(snapshot, config, now, randomSeed);

        return snapshot;
    }

    /**
     * 包一层 try-catch，避免特效抛异常把整个动画系统搞崩。
     */
    private static void applyEffectsSafe(BlockAnimationSnapshot snapshot,
                                         ClipAnimationConfig config,
                                         float now,
                                         long randomSeed) {
        try {
            applyEffects(snapshot, config, now, randomSeed);
        } catch (Exception e) {
            LOGGER.error("应用特效时出现未捕获异常: pos={} err={}",
                    snapshot != null ? snapshot.getPos() : "null", e.getMessage(), e);
        }
    }

    /**
     * 从 params Map 中提取变换参数并应用到 MatrixStack
     */
    private static void applyTransformFromParams(MatrixStack matrices, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }

        // 位移
        float dx = getFloat(params, "dx", 0.0f);
        float dy = getFloat(params, "dy", 0.0f);
        float dz = getFloat(params, "dz", 0.0f);
        if (dx != 0.0f || dy != 0.0f || dz != 0.0f) {
            matrices.translate(dx, dy, dz);
        }

        // 旋转
        float yaw = getFloat(params, "yaw", 0.0f);
        float pitch = getFloat(params, "pitch", 0.0f);
        float roll = getFloat(params, "roll", 0.0f);

        if (yaw != 0.0f) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        }
        if (pitch != 0.0f) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
        if (roll != 0.0f) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        }

        // 缩放
        float scale = getFloat(params, "scale", 1.0f);
        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }
    }

    private static float getFloat(Map<String, Object> map, String key, float defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).floatValue();
        }
        return defaultValue;
    }

    /**
     * 真正应用所有挂在 Clip 上的 EffectInstance。
     * <p>
     * 注意：这里仅负责「触发」粒子等效果，不改变方块几何。
     */
    private static void applyEffects(BlockAnimationSnapshot snapshot,
                                     ClipAnimationConfig config,
                                     float now,
                                     long randomSeed) {

        if (config == null || config.getEffects().isEmpty()) {
            return;
        }

        // ✅ EffectInstance 的时间轴是“相对于单个方块动画起点的偏移（归一化 0..1）”
        // 因此我们用 snapshot.rawProgress（未 easing 的 t_raw）来驱动特效。
        float tRaw = snapshot != null ? MathHelper.clamp(snapshot.getRawProgress(), 0.0f, 1.0f) : 0.0f;

        for (EffectInstance inst : config.getEffects()) {
            if (inst == null || !inst.isEnabled()) {
                if (LOGGER.isTraceEnabled() && inst != null) {
                    LOGGER.trace("[BlockAnimationEngine] 特效 {} 被跳过：已禁用", inst.getType());
                }
                continue;
            }

            // ✅ 作用阶段过滤：让特效也遵守 Enter/Exit 语义
            try {
                var clipPhase = config.getPhase();
                var effectPhase = inst.getPhase();
                if (effectPhase != EffectInstance.EffectPhase.BOTH) {
                    if (clipPhase == ClipAnimationConfig.AnimationPhase.ENTER
                            && effectPhase != com.chronoblocks.core.animation.effect.EffectInstance.EffectPhase.ENTER) {
                        continue;
                    }
                    if (clipPhase == ClipAnimationConfig.AnimationPhase.EXIT
                            && effectPhase != com.chronoblocks.core.animation.effect.EffectInstance.EffectPhase.EXIT) {
                        continue;
                    }
                }
            } catch (Exception ignored) {
            }

            float startNorm = MathHelper.clamp(inst.getStartOffset(), 0.0f, 1.0f);
            float durNorm = inst.getDuration();
            if (durNorm <= 0.0f) {
                // <=0 表示继承“单块动画窗口全长”
                durNorm = 1.0f;
            }
            durNorm = MathHelper.clamp(durNorm, 0.0001f, 1.0f);
            // ✅ 修复：确保 endNorm 不超过 1.0f，避免特效在动画结束后仍然触发
            float endNorm = MathHelper.clamp(startNorm + durNorm, 0.0f, 1.0f);

            if (tRaw < startNorm || tRaw > endNorm) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[BlockAnimationEngine] 特效 {} 时间窗口不匹配：tRaw={} 窗口=[{}, {}]",
                            inst.getType(), tRaw, startNorm, endNorm);
                }
                continue;
            }

            float effectT = MathHelper.clamp((tRaw - startNorm) / durNorm, 0.0f, 1.0f);

            IBlockEffect effectImpl = BlockEffectRegistry.get(inst.getType());
            if (effectImpl == null) {
                continue;
            }

            try {
                net.minecraft.client.world.ClientWorld world =
                        MinecraftClient.getInstance().world;
                if (world != null && snapshot != null) {
                    // ONE_SHOT：只在进入时间窗口时触发一次（支持回放/拖动倒退时再次触发）
                    if (inst.getTriggerMode() == com.chronoblocks.core.animation.effect.EffectInstance.TriggerMode.ONE_SHOT) {
                        var gate = EffectRuntimeGate.get(snapshot.getPos().asLong(), inst.getId(), now);
                        if (!gate.initialized) {
                            gate.initialized = true;
                            gate.lastTRaw = tRaw;
                            // 首次看到该方块：
                            // - 默认不触发，避免在片段中途开始渲染时“突然爆一堆”
                            // - 但如果特效窗口从 0 开始，且当前正处于动画刚起步（tRaw 非常小），允许触发一次
                            //   这样 startOffset=0 的 ONE_SHOT（如闪白光）不会永远不触发。
                            // ✅ 修复：首次初始化时，如果当前已经在特效窗口内，允许触发一次
                            // 这样即使 startOffset 不是 0，也能正常触发
                            if (tRaw + 1e-4f < startNorm) {
                                // 当前时间还没到特效窗口，等待进入窗口
                                gate.lastTRaw = tRaw;
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("[BlockAnimationEngine] ONE_SHOT 特效 {} 等待进入窗口: tRaw={} startNorm={} endNorm={}",
                                            inst.getType(), tRaw, startNorm, endNorm);
                                }
                                continue;
                            }
                            // 已经在窗口内，允许触发一次
                            gate.lastTRaw = tRaw;
                            effectT = 0.0f;
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("[BlockAnimationEngine] ONE_SHOT 特效 {} 首次触发（已在窗口内）: tRaw={} startNorm={} endNorm={}",
                                        inst.getType(), tRaw, startNorm, endNorm);
                            }
                        } else {
                            // 如果时间倒退（拖动播放头/循环），重置状态以允许重新触发
                            if (tRaw + 1e-4f < gate.lastTRaw) {
                                // 时间倒退：重置状态，允许下次前进时重新触发
                                gate.lastTRaw = tRaw;
                                // 如果倒退到特效窗口之前，重置 initialized 以允许重新触发
                                if (tRaw + 1e-4f < startNorm) {
                                    gate.initialized = false;
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debug("[BlockAnimationEngine] ONE_SHOT 特效 {} 时间倒退，重置状态: tRaw={} startNorm={}",
                                                inst.getType(), tRaw, startNorm);
                                    }
                                }
                                continue;
                            }
                            // ✅ 修复：检查是否跨越了特效开始边界（从窗口外进入窗口内）
                            boolean crossed = gate.lastTRaw + 1e-4f < startNorm && tRaw + 1e-4f >= startNorm;
                            gate.lastTRaw = tRaw;
                            if (!crossed) {
                                if (LOGGER.isTraceEnabled()) {
                                    LOGGER.trace("[BlockAnimationEngine] ONE_SHOT 特效 {} 未跨越边界: lastTRaw={} tRaw={} startNorm={}",
                                            inst.getType(), gate.lastTRaw, tRaw, startNorm);
                                }
                                continue;
                            }
                            // 触发一次：用 effectT=0 作为起点
                            effectT = 0.0f;
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("[BlockAnimationEngine] ONE_SHOT 特效 {} 跨越边界触发: lastTRaw={} tRaw={} startNorm={}",
                                        inst.getType(), gate.lastTRaw, tRaw, startNorm);
                            }
                        }
                    }

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[BlockAnimationEngine] 触发特效: type={} pos={} tRaw={} effectT={} startNorm={} endNorm={} triggerMode={}",
                                inst.getType(), snapshot.getPos(), tRaw, effectT, startNorm, endNorm, inst.getTriggerMode());
                    }
                    
                    effectImpl.render(
                        world,
                        snapshot.getPos(),
                        effectT,
                        inst,
                        new java.util.Random(
                            randomSeed
                                ^ snapshot.getPos().asLong()
                                ^ inst.getId().getLeastSignificantBits()
                        )
                    );
                }
            } catch (Exception e) {
                LOGGER.error("应用特效失败: type={} pos={} err={}",
                        inst.getType(),
                        snapshot != null ? snapshot.getPos() : "null",
                        e.getMessage(), e);
            }
        }
    }
}
