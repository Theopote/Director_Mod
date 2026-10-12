package com.director.core.animation;

import com.director.core.animation.effect.BlockEffectRegistry;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.IBlockEffect;
import com.director.core.animation.easing.EasingFunction;
import com.director.core.animation.sequence.BlockSequenceEntry;
import com.director.core.animation.transform.AnimationTransformerFactory;
import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.container.AnimationMode;
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
 * - 时间线时间 (now)
 * - 片段配置 ClipAnimationConfig（mode/duration/easing/params/effects）
 * - 序列行为 BlockSequenceEntry（seqNormalizedTime）
 * <p>
 * 映射成单个方块在当前帧的动画采样结果 BlockAnimationSnapshot。
 * <p>
 * 约定：
 * - 所有时间单位统一用秒（与 Timeline 一致）
 * - sequenceEntry.normalizedTime 为归一化 [0..1]，决定该方块在片段中的"出场顺序"
 * - Snapshot.transform 只保存"局部动画矩阵"（围绕方块中心），不包含世界坐标
 * 世界坐标由 Renderer 统一负责：先 translate(pos)，再乘 transform
 *
 * @author Director Team
 * @version 2.2
 */
public final class BlockAnimationEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnimationEngine.class);

    private BlockAnimationEngine() {
    }

    /**
     * 运行时特效触发状态（用于 ONE_SHOT 等跨帧门控）。
     *
     * <p>
     * 键：pos + effectId
     * 值：上一帧该方块在单块动画窗口内的 t_raw（0..1），以及最近一次 seen 时间用于清理。
     */
    private static final class EffectRuntimeGate {
        private static final class State {
            float lastTRaw;
            float lastSeenNow;
            boolean initialized;
        }

        // ✅ 修复：使用 ConcurrentHashMap 保证线程安全
        private static final java.util.concurrent.ConcurrentHashMap<Key, State> STATES = new java.util.concurrent.ConcurrentHashMap<>();
        private static volatile float lastCleanupNow = Float.NEGATIVE_INFINITY;

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
            var timing = PerBlockAnimationTiming.layout(config, clipStart, clipEnd);
            float seqNormalizedTime = seqEntry != null ? MathHelper.clamp(seqEntry.getNormalizedTime(), 0, 1) : 0;
            float blockStartTime = timing.window(seqNormalizedTime).start();

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

        var timing = PerBlockAnimationTiming.layout(config, clipStart, clipEnd);
        float seqNormalizedTime = seqEntry != null ? MathHelper.clamp(seqEntry.getNormalizedTime(), 0, 1) : 0;
        var window = timing.window(seqNormalizedTime);
        float blockStartTime = window.start();
        float blockEndTime = window.end();

        if (blockEndTime <= blockStartTime) {
            boolean visible = phase == ClipAnimationConfig.AnimationPhase.EXIT ? now < blockStartTime : now >= blockStartTime;
            if (visible) snapshot.markFullyVisible(); else snapshot.markHidden();
            snapshot.setRawProgress(now >= blockStartTime ? 1 : 0);
            snapshot.setEasedProgress(snapshot.getRawProgress());
            snapshot.setAnimationFinished(now >= blockStartTime);
            snapshot.setSequenceNormalizedTime(seqNormalizedTime);
            snapshot.setTransform(null);
            if (now >= blockStartTime) applyEffectsSafe(snapshot, config, now, randomSeed);
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
                // 延迟阶段不发射粒子。
                snapshot.setRawProgress(0.0f);
                snapshot.setEasedProgress(0.0f);
                return snapshot;
            }
        } else {
            // ENTER：动画开始前隐藏
            if (now < blockStartTime) {
                snapshot.markHidden();
                // 延迟阶段不发射粒子。
                snapshot.setRawProgress(0.0f);
                snapshot.setEasedProgress(0.0f);
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
                snapshot.setRawProgress(1); snapshot.setEasedProgress(1);
                snapshot.setAnimationFinished(true);
            } else {
                snapshot.markFullyVisible();
                snapshot.setSequenceNormalizedTime(seqNormalizedTime);
                snapshot.setTransform(null); // null = identity
                snapshot.setAlpha(1.0f);
                // ✅ 修复：使用独立的标记表示"动画已结束"，而不是 rawProgress = 1.001f
                snapshot.setRawProgress(1.0f);
                snapshot.setEasedProgress(1.0f);
                snapshot.setAnimationFinished(true);
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
                && mode != AnimationMode.IMPLODE
                && mode != AnimationMode.FLY_OUT
                && mode != AnimationMode.SHRINK_OUT) {
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

        IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(mode);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationEngine] transformer: mode={} transformer={}",
                    mode, transformer != null ? transformer.getClass().getSimpleName() : "null");
        }

        if (transformer != null) {
            // ⚠️ 防御：config.getParams() 可能为 null
            Map<String, Object> baseParams = config.getParams();
            Map<String, Object> tempParams = new java.util.HashMap<>(
                    baseParams != null ? baseParams : java.util.Collections.emptyMap());

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
                Object enabledObj = null;
                if (p != null) {
                    enabledObj = p.get("opacityFade");
                }
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
        } catch (Exception e) {
            LOGGER.debug("[BlockAnimationEngine] 应用透明度渐变失败: {}", e.getMessage());
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

        // 缩放：优先使用非均匀缩放 scaleX/scaleY/scaleZ，否则回退到统一 scale
        float scaleX = getFloat(params, "scaleX", Float.NaN);
        float scaleY = getFloat(params, "scaleY", Float.NaN);
        float scaleZ = getFloat(params, "scaleZ", Float.NaN);
        if (!Float.isNaN(scaleX) || !Float.isNaN(scaleY) || !Float.isNaN(scaleZ)) {
            // 非均匀缩放：任何轴有指定值就使用非均匀路径
            float sx = Float.isNaN(scaleX) ? 1.0f : scaleX;
            float sy = Float.isNaN(scaleY) ? 1.0f : scaleY;
            float sz = Float.isNaN(scaleZ) ? 1.0f : scaleZ;
            if (sx != 1.0f || sy != 1.0f || sz != 1.0f) {
                matrices.scale(sx, sy, sz);
            }
        } else {
            // 统一缩放
            float scale = getFloat(params, "scale", 1.0f);
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
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

        // ✅ 使用 snapshot.rawProgress 和 snapshot.animationFinished 来驱动特效
        // rawProgress 在 [0..1] 范围内表示动画进度
        // animationFinished = true 表示动画已经完全结束（超过 endTime）
        float tRaw = snapshot != null ? MathHelper.clamp(snapshot.getRawProgress(), 0.0f, 1.0f) : 0.0f;
        boolean isFinished = snapshot != null && snapshot.isAnimationFinished();

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
                            && effectPhase != com.director.core.animation.effect.EffectInstance.EffectPhase.ENTER) {
                        continue;
                    }
                    if (clipPhase == ClipAnimationConfig.AnimationPhase.EXIT
                            && effectPhase != com.director.core.animation.effect.EffectInstance.EffectPhase.EXIT) {
                        continue;
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[BlockAnimationEngine] 检查特效阶段失败: {}", e.getMessage());
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

            // ✅ 优化：如果动画已结束，跳过 CONTINUOUS 特效（持续型特效应该在动画结束时停止）
            // 但允许在动画刚结束时（isFinished && tRaw >= endNorm）触发后段 ONE_SHOT 特效
            if (isFinished && inst.getTriggerMode() == com.director.core.animation.effect.EffectInstance.TriggerMode.CONTINUOUS) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[BlockAnimationEngine] 特效 {} 跳过：动画已结束，CONTINUOUS 特效停止",
                            inst.getType());
                }
                continue;
            }

            boolean oneShot = inst.getTriggerMode() == EffectInstance.TriggerMode.ONE_SHOT;
            if (oneShot) {
                EffectRuntimeGate.State gate = null;
                if (snapshot != null) {
                    gate = EffectRuntimeGate.get(snapshot.getPos().asLong(), inst.getId(), now);
                }
                boolean fire = false;
                if (gate != null) {
                    fire = PerBlockEffectTrigger.crossed(gate.initialized ? gate.lastTRaw : Float.NaN, tRaw, startNorm);
                }
                if (gate != null) {
                    gate.initialized = true;
                }
                if (gate != null) {
                    gate.lastTRaw = tRaw;
                }
                if (!fire) continue;
            }
            if (!oneShot && (tRaw < startNorm || tRaw > endNorm)) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[BlockAnimationEngine] 特效 {} 时间窗口不匹配：tRaw={} 窗口=[{}, {}]",
                            inst.getType(), tRaw, startNorm, endNorm);
                }
                continue;
            }

            float effectT = oneShot ? 0 : MathHelper.clamp((tRaw - startNorm) / durNorm, 0.0f, 1.0f);

            IBlockEffect effectImpl = BlockEffectRegistry.get(inst.getType());
            if (effectImpl == null) {
                continue;
            }

            try {
                net.minecraft.client.world.ClientWorld world = MinecraftClient.getInstance().world;
                if (world != null && snapshot != null) {


                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                                "[BlockAnimationEngine] 触发特效: type={} pos={} tRaw={} effectT={} startNorm={} endNorm={} triggerMode={}",
                                inst.getType(), snapshot.getPos(), tRaw, effectT, startNorm, endNorm,
                                inst.getTriggerMode());
                    }

                    effectImpl.render(
                            world,
                            snapshot,
                            effectT,
                            inst,
                            new java.util.Random(
                                    randomSeed
                                            ^ inst.getId().getLeastSignificantBits()));
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
