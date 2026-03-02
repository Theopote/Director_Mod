package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.easing.EasingFunction;
import com.chronoblocks.core.animation.transform.AnimationTransformerFactory;
import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.effect.preset.EffectPreset;
import com.chronoblocks.core.animation.effect.preset.EffectPresetRegistry;
import com.chronoblocks.ui.util.EffectClipboardManager;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.sequence.ConcurrencyMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import com.chronoblocks.core.animation.sequence.SequenceMode;
import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.TimeMappingStrategy;
import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.FloodSeedMode;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimelineClip 属性面板适配器（最终清洗版）
 *
 * <p>
 * 基于ImGui的属性编辑面板。
 * 修复了旧的字符串硬编码问题，统一了枚举处理逻辑。
 *
 * @author ChronoBlocks Team
 * @version 3.1 - Enum Refactored
 */

public class TimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipPropertyAdapter.class);

    private final ImInt animationModeIndex = new ImInt();

    private final ImInt easingFunctionIndex = new ImInt();

    private final ImBoolean opacityFade = new ImBoolean(false);

    // ==== Effects UI state ====
    private final ImInt addEffectSelectedIndex = new ImInt(0);

    // 序列行为高级选项折叠
    private final ImBoolean showSequenceAdvanced = new ImBoolean(false);

    // Easing曲线预览缓存（避免每帧重新计算）
    private static final int EASING_CURVE_SAMPLES = 100;
    private float[] easingCurveCache = new float[EASING_CURVE_SAMPLES];
    private EasingFunction cachedEasingFunction = null;

    // 静态缓存标签数组 (减少GC)
    // 旧：曾用于直接显示 AnimationMode.values() 的下拉。
    // 现在改为按 Enter/Exit 阶段过滤可用模式，因此不再需要全量 labels。
    private static String[] EASING_FUNCTION_LABELS = null;

    /**
     * 获取缓动函数标签数组（延迟初始化）
     */
    private static String[] getEasingFunctionLabels() {
        if (EASING_FUNCTION_LABELS == null) {
            try {
                EASING_FUNCTION_LABELS = buildEasingLabels();
            } catch (Exception e) {
                LOGGER.error("初始化缓动函数标签失败: {}", e.getMessage(), e);
                EASING_FUNCTION_LABELS = new String[] { "线性" };
            }
        }
        return EASING_FUNCTION_LABELS;
    }

    /**
     * 构建缓动函数标签数组
     */
    private static String[] buildEasingLabels() {
        EasingFunction[] functions = EasingFunction.values();
        String[] labels = new String[functions.length];
        for (int i = 0; i < functions.length; i++) {
            labels[i] = functions[i].getDisplayName();
        }
        return labels;
    }

    /**
     * 构建枚举标签缓存
     */
    private static <T extends Enum<T>> String[] buildEnumLabels(T[] values) {
        return Arrays.stream(values).map(v -> {
            try {
                return (String) v.getClass().getMethod("getDisplayName").invoke(v);
            } catch (Exception e) {
                return v.toString();
            }
        }).toArray(String[]::new);
    }

    /**
     * 获取枚举的显示名称数组（复用 buildEnumLabels 逻辑）
     */
    private <T extends Enum<T>> String[] getAllEnumDisplayNames(T[] values) {
        return buildEnumLabels(values);
    }

    @Override
    public Class<TimelineClip> getTargetType() {
        return TimelineClip.class;
    }

    @Override
    public String getAdapterName() {
        return "容器片段属性";
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean supports(Object target) {
        if (!(target instanceof TimelineClip clip)) {
            return false;
        }
        try {
            TimelineTrack parent = clip.getParentTrack();
            // ✅ 容器片段适配器：只支持非拆除容器（拆除容器由EraseTimelineClipPropertyAdapter处理）
            UUID containerId = clip.getBoundContainerId();
            if (containerId == null) {
                return false;
            }
            try {
                Container container = ContainerManager.getInstance() != null
                        ? ContainerManager.getInstance().getContainer(containerId)
                        : null;
                if (container == null) {
                    return false;
                }
                return container.getType() != com.chronoblocks.core.container.ContainerType.DEMOLITION;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean renderProperties(TimelineClip clip) {
        if (clip == null) {
            ImGui.textDisabled("未选中片段");
            return false;
        }

        try {
            return renderPropertiesInternal(clip);
        } catch (Exception e) {
            LOGGER.error("渲染片段属性时发生异常: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "渲染错误: " + e.getMessage());
            if (ImGui.button("查看详情")) {
                LOGGER.error("详细堆栈:", e);
            }
            return false;
        }
    }

    /**
     * 渲染属性（内部实现）
     */
    private boolean renderPropertiesInternal(TimelineClip clip) {
        boolean modified = false;

        // ⭐ 用于音频联动：属性面板中修改容器片段时间范围时，同步更新关联的音频片段
        final float oldStartTime = clip.getStartTime();
        final float oldEndTime = clip.getEndTime();

        // 1. 基础设置组 - 默认展开
        if (ImGui.collapsingHeader(Icons.SETTINGS + " 基础设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= renderBasicProperties(clip);
            ImGui.unindent();
        }

        boolean sequenceModified = false;
        boolean animationModified = false;

        // 2. 序列行为组 - 默认展开
        if (ImGui.collapsingHeader(Icons.LAYERS + " 序列行为", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            sequenceModified = renderSequenceBehavior(clip);
            modified |= sequenceModified;
            ImGui.unindent();
        }

        // 3. 动画设置组 - 默认展开
        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            animationModified = renderAnimationSettings(clip);
            modified |= animationModified;
            ImGui.unindent();
        }

        // 4. 特效 && 特效预设组
        List<EffectInstance> effects = clip.getAnimationConfig() != null ? clip.getAnimationConfig().getEffects()
                : java.util.Collections.emptyList();
        String effectsHeader = Icons.PARTICLE + " 特效 (Effects)";
        if (!effects.isEmpty()) {
            effectsHeader += " (" + effects.size() + ")";
        }
        if (ImGui.collapsingHeader(effectsHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= renderEffectsSection(clip);
            ImGui.unindent();
        }

        // ======= 动画配置联动：修改动画/序列设置后，自动刷新音效事件 =======
        // 需求：音效时机应随着“是否有动画/动画时长/序列延迟”等变化而变化
        try {
            TimelineTrack parent = clip.getParentTrack();
            TrackType type = parent != null ? parent.getType() : null;
            boolean isBlockClip = type == TrackType.CONTAINER;
            if (isBlockClip && (sequenceModified || animationModified)) {
                // 避免拖动 slider 时每帧重建：仅在用户停止编辑后触发一次（服务端也有签名缓存兜底）
                if (!ImGui.isAnyItemActive()) {
                    com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                            .refreshAutoSoundEventsIfNeeded(clip);
                }
            }
        } catch (Exception ignored) {
        }

        // ======= 时间范围联动：容器片段 -> 音频片段 =======
        // 要求：无论在时间线拖动/缩放还是在属性面板修改时，音频片段都要跟随容器片段范围变化
        try {
            TimelineTrack parent = clip.getParentTrack();
            TrackType type = parent != null ? parent.getType() : null;
            boolean isBlockClip = type == TrackType.CONTAINER;
            if (isBlockClip) {
                float newStart = clip.getStartTime();
                float newEnd = clip.getEndTime();
                boolean timeChanged = Math.abs(newStart - oldStartTime) > 0.0005f
                        || Math.abs(newEnd - oldEndTime) > 0.0005f;
                if (timeChanged) {
                    com.chronoblocks.ui.timeline.audio.TimelineAudioService.getInstance()
                            .onClipTimeRangeChanged(clip, oldStartTime, oldEndTime, newStart, newEnd);

                    // 标记时间线变更：更新总时长 + 自动保存（与拖动/命令路径一致）
                    var timelineModel = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance()
                            .getTimelineModel();
                    if (timelineModel != null) {
                        timelineModel.markDirty();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return modified;
    }

    // ----- 渲染方法实现 -----

    /**
     * 渲染基础属性
     */

    private boolean renderBasicProperties(TimelineClip clip) {
        boolean modified = false;
        ClipAnimationConfig config = clip.getAnimationConfig();

        float contentWidth = ImGui.getContentRegionAvailX();
        float labelWidth = 80.0f;
        float inputWidth = (contentWidth - labelWidth * 2 - ImGui.getStyle().getItemSpacingX() * 2) / 2;

        // 起始时间
        ImGui.alignTextToFramePadding();
        ImGui.text("起始时间");
        ImGui.sameLine();
        ImGui.pushItemWidth(inputWidth);
        float[] startVal = { clip.getStartTime() };
        if (ImGui.dragFloat("##StartTime", startVal, 0.05f, 0f, 10000f, "%.2fs")) {
            clip.setStartTime(Math.max(0f, startVal[0]));
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段开始时间");
        }

        ImGui.sameLine();

        // 持续时长
        ImGui.alignTextToFramePadding();
        ImGui.text("持续时长");
        ImGui.sameLine();
        ImGui.pushItemWidth(inputWidth);
        float[] lengthVal = { clip.getLength() };
        if (ImGui.dragFloat("##Length", lengthVal, 0.05f, 0.05f, 10000f, "%.2fs")) {
            clip.setLength(lengthVal[0]);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("方块从隐藏到完全出现的持续时长");
        }

        // 片段颜色
        int clipColor = clip.getColor();
        float[] colorRGBA = colorIntToFloat(clipColor);
        if (ImGui.colorEdit4("片段颜色", colorRGBA,
                imgui.flag.ImGuiColorEditFlags.NoAlpha |
                        imgui.flag.ImGuiColorEditFlags.DisplayRGB |
                        imgui.flag.ImGuiColorEditFlags.InputRGB)) {
            int newColor = colorFloatToInt(colorRGBA);
            clip.setColor(newColor);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段在轨道上显示的颜色");
        }

        ImGui.separator();

        // 自动淡化之前片段
        ImBoolean autoFade = new ImBoolean(clip.isAutoFadePreviousClips());
        if (ImGui.checkbox("自动淡化之前片段", autoFade)) {
            clip.setAutoFadePreviousClips(autoFade.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("播放当前片段时，让更早的片段方块暂时半透明/隐藏以避免遮挡。");
        }

        // 淡化透明度控制
        if (clip.isAutoFadePreviousClips()) {
            float[] opacity = { clip.getAutoFadeOpacity() };
            if (ImGui.sliderFloat("淡化透明度", opacity, 0.05f, 0.95f, "%.2f")) {
                clip.setAutoFadeOpacity(opacity[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("设置遮挡方块的透明度：\n- 0.05: 几乎透明\n- 0.35: 半透明（推荐）\n- 0.95: 几乎不透明");
            }

            if (opacity[0] < 0.15f) {
                ImGui.textColored(0.8f, 0.8f, 0.2f, 1.0f, "  -> 遮挡方块将被完全隐藏");
            } else if (opacity[0] < 0.5f) {
                ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "  -> 遮挡方块将变为半透明");
            } else {
                ImGui.textColored(0.8f, 0.8f, 0.8f, 1.0f, "  -> 遮挡方块将轻微淡化");
            }
        }

        // 遮挡时透明显示开关
        ImBoolean occlusionTransparency = new ImBoolean(config.isOcclusionTransparencyEnabled());
        if (ImGui.checkbox("遮挡时透明显示", occlusionTransparency)) {
            config.setOcclusionTransparencyEnabled(occlusionTransparency.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("勾选：遮挡后续片段时由系统接管并以半透明显示（隐藏原网格）。\n关闭：保持原样，不参与透明遮挡。");
        }

        ImGui.separator();

        // Post-Animation Fade 设置
        if (ImGui.collapsingHeader("动画后淡化 (Post-Animation Fade)", ImGuiTreeNodeFlags.None)) {
            ImGui.indent();

            ImGui.textDisabled("片段动画结束后，可以设置一个淡化阶段，让方块逐渐变透明。");
            UITheme.spacingSmall();

            float[] fadeStart = { clip.getPostAnimationFadeStart() };
            if (ImGui.dragFloat("淡化开始时间 (秒)", fadeStart, 0.1f, 0.0f, 10.0f, "%.1fs")) {
                clip.setPostAnimationFadeStart(fadeStart[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("片段结束后，等待多少秒开始淡化。\n0.0 = 片段结束立即开始淡化。");
            }

            float[] fadeEnd = { clip.getPostAnimationFadeEnd() };
            if (ImGui.dragFloat("淡化结束时间 (秒)", fadeEnd, 0.1f, fadeStart[0], 30.0f, "%.1fs")) {
                clip.setPostAnimationFadeEnd(fadeEnd[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("片段结束后，到多少秒时淡化完成。\n必须 >= 淡化开始时间。\n超过此时间后，方块恢复为完全不透明。");
            }

            // 显示淡化时间范围提示
            if (fadeEnd[0] > fadeStart[0]) {
                float fadeDuration = fadeEnd[0] - fadeStart[0];
                ImGui.textDisabled(String.format("淡化持续时间: %.1f 秒 (从 %.1f 到 %.1f 秒)",
                        fadeDuration, fadeStart[0], fadeEnd[0]));
            } else if (fadeEnd[0] == fadeStart[0] && fadeStart[0] > 0.0f) {
                ImGui.textColored(0.8f, 0.6f, 0.2f, 1.0f, "⚠ 淡化开始和结束时间相同，淡化效果将立即完成");
            } else {
                ImGui.textDisabled("淡化功能已禁用（开始和结束时间均为 0）");
            }

            ImGui.unindent();
        }

        return modified;
    }

    private boolean renderAnimationSettings(TimelineClip clip) {
        try {
            return renderAnimationSettingsInternal(clip);
        } catch (Exception e) {
            LOGGER.error("渲染动画设置时发生异常: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 渲染动画设置（内部实现）
     *
     * <p>
     * 结构：
     * <ul>
     * <li>1. 总体节奏（模式 + 时长）</li>
     * <li>2. 缓动曲线（枚举 + 曲线预览）</li>
     * <li>3. 动画模式参数（位移 / 缩放 / 旋转等）</li>
     * <li>4. 高级修饰（透明度 / 闪白光等）</li>
     * </ul>
     */
    private boolean renderAnimationSettingsInternal(TimelineClip clip) {
        boolean modified = false;

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            LOGGER.warn("片段的动画配置为空");
            ImGui.textDisabled("动画配置为空");
            return false;
        }

        // ✅ 进入 UI 就做一次旧模式迁移，避免“旧的 AnimationMode=随机/切片”等仍占用 mode 下拉
        com.chronoblocks.core.animation.LegacyAnimationModeMigrator.migrateIfNeeded(config);
        // ✅ 旧高级参数迁移：例如 flashWhite -> EffectInstance（防止开关“看起来有用但实际不生效”）
        com.chronoblocks.core.animation.LegacyAdvancedParamsMigrator.migrateIfNeeded(config);

        // ===== 0. 防御式初始化 =====
        AnimationMode currentMode = config.getMode();
        if (currentMode == null) {
            LOGGER.warn("动画模式为空，使用默认值 INSTANT");
            currentMode = AnimationMode.INSTANT;
            config.setMode(currentMode);
        }

        String currentEasing = config.getEasing();
        if (currentEasing == null || currentEasing.isEmpty()) {
            currentEasing = EasingFunction.LINEAR.name();
            config.setEasing(currentEasing);
        }

        // ===== 1. 总体节奏：阶段(Enter) + 模式 + 时长 =====
        // ✅ 注意：拆除片段现在使用 EraseTimelineClipPropertyAdapter，此适配器只处理容器片段（ENTER 阶段）
        String sectionTitle = "动画模式与节奏";
        UITheme.label(sectionTitle, UITheme.DarkTheme.TEXT_SELECTED);

        // 片段阶段：容器片段固定为 ENTER
        com.chronoblocks.core.animation.ClipAnimationConfig.AnimationPhase desiredPhase = com.chronoblocks.core.animation.ClipAnimationConfig.AnimationPhase.ENTER;
        if (config.getPhase() != desiredPhase) {
            config.setPhase(desiredPhase);
            modified = true;
        }

        // 模式选择（按阶段过滤）
        AnimationMode[] availableModes = new AnimationMode[] { AnimationMode.INSTANT, AnimationMode.FADE_IN,
                AnimationMode.GROW_UP, AnimationMode.EXPAND, AnimationMode.ASSEMBLE, AnimationMode.FALL_DOWN,
                AnimationMode.RISE_UP, AnimationMode.FLY_IN, AnimationMode.POP_IN, AnimationMode.SHAKER };

        // 同一个 mode 在 Exit/Enter 阶段语义可能相反：显示名做一次用户友好化
        String[] availableLabels = java.util.Arrays.stream(availableModes)
                .map(AnimationMode::getDisplayName)
                .toArray(String[]::new);
        int selectedIdx = 0;
        for (int i = 0; i < availableModes.length; i++) {
            if (availableModes[i] == currentMode) {
                selectedIdx = i;
                break;
            }
        }
        // 当前 mode 不在可选列表：自动纠正到更合理的默认值
        if (selectedIdx == 0 && currentMode != availableModes[0]) {
            AnimationMode fallback = AnimationMode.FADE_IN;
            config.setMode(fallback);
            currentMode = fallback;
            for (int i = 0; i < availableModes.length; i++) {
                if (availableModes[i] == currentMode) {
                    selectedIdx = i;
                    break;
                }
            }
            modified = true;
        }
        animationModeIndex.set(selectedIdx);

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##AnimMode", animationModeIndex, availableLabels)) {
            int newIndex = animationModeIndex.get();
            if (newIndex < 0 || newIndex >= availableModes.length) {
                newIndex = 0;
            }
            AnimationMode newMode = availableModes[newIndex];
            config.setMode(newMode);
            currentMode = newMode;

            // 智能默认缓动函数和参数重置
            try {
                IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(newMode);
                if (transformer != null) {
                    // 设置推荐的缓动函数
                    String recommended = transformer.getRecommendedEasing();
                    if (recommended != null && !recommended.isEmpty()) {
                        config.setEasing(recommended);
                        currentEasing = recommended;
                    }

                    // 重置参数：清除旧参数，填充新模式的默认参数
                    Map<String, Object> params = config.getParams();
                    Map<String, Object> defaultParams = transformer.getDefaultParams();
                    if (defaultParams != null) {
                        // 清除所有旧参数（但保留高级修饰参数）
                        boolean opacityFadeValue = params.containsKey("opacityFade") &&
                                (params.get("opacityFade") instanceof Boolean b ? b : false);
                        float opacityFadeIntensity = 1.0f;
                        if (params.containsKey("opacityFadeIntensity")) {
                            Object intensityObj = params.get("opacityFadeIntensity");
                            if (intensityObj instanceof Number num) {
                                opacityFadeIntensity = num.floatValue();
                            } else if (intensityObj instanceof String str) {
                                try {
                                    opacityFadeIntensity = Float.parseFloat(str);
                                } catch (NumberFormatException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                        params.clear();

                        // 填充新模式的默认参数
                        params.putAll(defaultParams);

                        // 恢复高级修饰参数
                        params.put("opacityFade", opacityFadeValue);
                        params.put("opacityFadeIntensity", opacityFadeIntensity);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("获取推荐缓动函数时出错: {}", e.getMessage());
            }

            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("选择出现片段的入场方式");
        }

        // 模式描述（来自 AnimationMode 描述，个别 Exit 模式做友好化）
        // UITheme.spacingSmall();
        try {
            drawHelpBox(currentMode.getDescription());
        } catch (Exception e) {
            LOGGER.error("显示模式描述时出错: {}", e.getMessage(), e);
        }

        // 动画时长（相对于片段长度的绝对秒数）
        // UITheme.spacingSmall();
        float[] duration = { config.getDuration() };
        if (ImGui.dragFloat("动画时长 (秒)", duration, 0.05f, 0.0f, clip.getLength(), "%.2fs")) {
            if (duration[0] < 0.0f)
                duration[0] = 0.0f;
            config.setDuration(duration[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制单个方块从开始运动到完全就位所用的时间。\n" +
                    "建议不超过片段总时长。");
        }

        // ===== 动画模式特定参数（显示在"动画模式与节奏"部分内）=====
        UITheme.spacingSmall();
        Map<String, Object> params = config.getParams();

        // 填入默认值，避免 NPE
        try {
            IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(currentMode);
            if (transformer != null) {
                Map<String, Object> defaultParams = transformer.getDefaultParams();
                if (defaultParams != null) {
                    defaultParams.forEach(params::putIfAbsent);
                }

                // 直接使用 ImGui 渲染参数（不依赖 UIHelper 的反射）
                try {
                    boolean paramsModified = renderAnimationParamsDirectly(currentMode, params);
                    if (paramsModified) {
                        modified = true;
                    }
                } catch (Exception e) {
                    LOGGER.warn("渲染动画参数时出错: {}", e.getMessage(), e);
                    ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, "⚠ 参数渲染失败: " + e.getMessage());
                }
            } else {
                LOGGER.warn("未找到动画模式 {} 对应的 Transformer", currentMode);
                ImGui.textDisabled("⚠ 未找到动画模式实现");
            }
        } catch (Exception e) {
            LOGGER.error("渲染动画参数时出错: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "错误: " + e.getMessage());
        }

        UITheme.separator();

        // ===== 2. 缓动曲线：枚举 + 曲线预览 =====
        UITheme.label("缓动曲线 (Easing)", UITheme.DarkTheme.TEXT_SELECTED);

        // 解析当前缓动
        EasingFunction currentEasingFunc;
        try {
            currentEasingFunc = EasingFunction.fromString(currentEasing);
        } catch (Exception e) {
            currentEasingFunc = EasingFunction.LINEAR;
        }

        int easingOrdinal = currentEasingFunc.ordinal();
        String[] easingLabels = getEasingFunctionLabels();
        if (easingOrdinal >= easingLabels.length) {
            easingOrdinal = 0;
        }
        easingFunctionIndex.set(easingOrdinal);

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##Easing", easingFunctionIndex, easingLabels)) {
            int newIdx = easingFunctionIndex.get();
            if (newIdx < 0 || newIdx >= EasingFunction.values().length) {
                newIdx = 0;
            }
            EasingFunction newFunc = EasingFunction.values()[newIdx];
            config.setEasing(newFunc.name());
            currentEasingFunc = newFunc;
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(currentEasingFunc.getDescription());
        }

        // UITheme.spacingSmall();
        // 可视化曲线预览（你已经实现了）
        renderEasingCurvePreview(currentEasingFunc);

        UITheme.separator();

        // ===== 4. 高级修饰（透明度 / 闪白光 / 重置） =====
        UITheme.label("高级修饰 (Advanced)", UITheme.DarkTheme.TEXT_SELECTED);

        // 透明度淡入
        opacityFade.set(getBooleanParam(params));
        if (ImGui.checkbox("透明度淡入", opacityFade)) {
            params.put("opacityFade", opacityFade.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("勾选后，动画过程中会从完全透明逐渐变为不透明。");
        }

        // 强度（仅在开启时显示）
        if (opacityFade.get()) {
            ImGui.sameLine();
            float fadeIntensity = getFloatParam(params);
            float[] fadeArr = { fadeIntensity };
            ImGui.pushItemWidth(100.0f);
            if (ImGui.dragFloat("##FadeIntensity", fadeArr, 0.05f, 0.1f, 2.0f, "强度: %.2f")) {
                fadeIntensity = fadeArr[0];
                params.put("opacityFadeIntensity", fadeIntensity);
                modified = true;
            }
            ImGui.popItemWidth();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("控制淡入的速度：>1 慢，<1 快。");
            }
        }

        // 闪白光已归入「特效系统」（EffectInstance: FLASH_WHITE），避免“UI 有开关但运行时不生效”的情况。
        ImGui.textDisabled("提示：闪白光请在下方「特效」面板添加 FLASH_WHITE（或使用特效预设包）。");

        if (ImGui.button("恢复当前模式默认参数")) {
            try {
                IAnimationTransformer transformer = AnimationTransformerFactory.getInstance()
                        .getTransformer(currentMode);
                if (transformer != null) {
                    Map<String, Object> defaultParams = transformer.getDefaultParams();
                    if (defaultParams != null) {
                        params.clear();
                        params.putAll(defaultParams);
                        // 高级修饰默认关闭
                        params.put("opacityFade", false);
                        params.put("opacityFadeIntensity", 1.0f);
                        opacityFade.set(false);
                        modified = true;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("恢复默认值时出错: {}", e.getMessage(), e);
                ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "恢复失败: " + e.getMessage());
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("将当前动画模式的参数全部恢复为默认值。");
        }

        return modified;
    }

    // ========== 工具方法 ==========

    /**
     * 安全获取浮点参数（改进类型安全）
     *
     * @param params 参数Map
     * @return 参数值或默认值
     */
    private float getFloatParam(Map<String, Object> params) {
        if (params == null)
            return (float) 1.0;
        Object val = params.get("opacityFadeIntensity");
        switch (val) {
            case null -> {
                return (float) 1.0;
            }
            case Number number -> {
                return number.floatValue();
            }
            case String s -> {
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    LOGGER.warn("参数 {} 无法解析为浮点数: {}", "opacityFadeIntensity", s);
                    return (float) 1.0;
                }
            }
            default -> {
            }
        }

        LOGGER.warn("参数 {} 类型不匹配，期望 Number 或 String，实际: {}", "opacityFadeIntensity", val.getClass().getSimpleName());
        return (float) 1.0;
    }

    /**
     * 安全获取布尔参数
     *
     * @param params 参数Map
     * @return 参数值或默认值
     */
    private boolean getBooleanParam(Map<String, Object> params) {
        if (params == null)
            return false;
        Object val = params.get("opacityFade");
        if (val == null) {
            return false;
        }
        return switch (val) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            default -> {
                LOGGER.warn("参数 {} 类型不匹配，期望 Boolean 或 String，实际: {}", "opacityFade", val.getClass().getSimpleName());
                yield false;
            }
        };
    }

    /**
     * 渲染「序列行为」面板
     * <p>
     * 新版 UI 采用 Flow + Fill + Origin + Weight + Modifiers 的 3 步流水线：
     * <p>
     * 1. 切分方式 (Flow) —— 决定整体波浪的方向/形状（按层、球形、柱形、随机）
     * 2. 起始锚点 (Origin) —— 决定从哪里开始（底部中心、几何中心、自定义）
     * 3. 内部填充 (Fill) —— 决定局部纹理（线性扫描、径向、螺旋、噪点）
     * 4. 层级分离度 + 修饰 —— 实现"严格分层 / 软过渡 + 反向 / 往复"
     */
    private boolean renderSequenceBehavior(TimelineClip clip) {
        boolean modified = false;

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            ImGui.textDisabled("动画配置为空，无法编辑序列行为");
            return false;
        }

        SequenceParameters params = config.getSequenceParameters();
        if (params == null) {
            ImGui.textDisabled("序列参数为空，使用默认配置");
            params = new SequenceParameters();
            config.setSequenceParameters(params);
            modified = true;
        }

        // 强制使用 Evaluator 模式（已废弃旧模式）
        if (config.getSequenceMode() != SequenceMode.EVALUATOR_BASED) {
            config.setSequenceMode(SequenceMode.EVALUATOR_BASED);
            modified = true;
        }

        // ✅ 快捷预设：排序策略（Sequence）属于“序列层”，从动画设置面板移到这里
        UITheme.label("排序策略预设（Sequence）", UITheme.DarkTheme.TEXT_SELECTED);
        // 定义所有预设按钮的标签和配置
        String[] presetLabels = {
                "顺序(默认)",
                "随机",
                "扩散(中心" + Icons.ARROW_RIGHT + "外)",
                "螺旋",
                "切片(逐层)",
                "波浪"
        };

        // 记录按钮起始位置，用于换行后对齐
        float buttonsStartX = ImGui.getCursorPosX();

        // 渲染所有按钮，支持自动换行
        for (int i = 0; i < presetLabels.length; i++) {
            String label = presetLabels[i];

            // 计算按钮宽度（文本宽度 + 内边距）
            float textWidth = ImGui.calcTextSize(label).x;
            float paddingX = ImGui.getStyle().getFramePadding().x * 2.0f;
            float buttonWidth = textWidth + paddingX;

            // 如果不是第一个按钮，先回到上一项末尾，然后检查是否需要换行
            if (i > 0) {
                ImGui.sameLine();
            }

            // 检查是否需要换行：如果当前行放不下，则换行
            float availWidth = ImGui.getContentRegionAvailX();
            if (buttonWidth > availWidth) {
                ImGui.newLine();
                ImGui.setCursorPosX(buttonsStartX);
            }

            // 渲染按钮并处理点击
            if (ImGui.button(label)) {
                switch (i) {
                    case 0: // 顺序(默认)
                        params.setFlowType(FlowType.PLANAR);
                        params.setOriginType(OriginType.BOTTOM_CENTER);
                        params.setFillType(FillType.LINEAR_SCAN);
                        params.setReverse(false);
                        params.setAlternating(false);
                        break;
                    case 1: // 随机
                        params.setFlowType(FlowType.RANDOM);
                        params.setFillType(FillType.LINEAR_SCAN);
                        params.setReverse(false);
                        params.setAlternating(false);
                        break;
                    case 2: // 扩散(中心→外)
                        params.setFlowType(FlowType.SPHERICAL);
                        params.setOriginType(OriginType.CENTER);
                        params.setFillType(FillType.RADIAL);
                        params.setReverse(false);
                        params.setAlternating(false);
                        break;
                    case 3: // 螺旋
                        params.setFlowType(FlowType.PLANAR);
                        params.setOriginType(OriginType.CENTER);
                        params.setFillType(FillType.SPIRAL);
                        params.setReverse(false);
                        params.setAlternating(false);
                        break;
                    case 4: // 切片(逐层)
                        params.setFlowType(FlowType.PLANAR);
                        params.setOriginType(OriginType.BOTTOM_CENTER);
                        params.setFillType(FillType.LINEAR_SCAN);
                        params.setPriorityWeight(100.0f); // 严格分层：当前层完全完成后才开始下一层
                        params.setLayerStep(1.0f);
                        params.setReverse(false);
                        params.setAlternating(false);
                        break;
                    case 5: // 波浪
                        params.setFlowType(FlowType.PLANAR);
                        params.setOriginType(OriginType.BOTTOM_CENTER);
                        params.setFillType(FillType.LINEAR_SCAN);
                        params.setReverse(false);
                        params.setAlternating(true);
                        break;
                }
                modified = true;
            }

            // 为最后一个按钮显示工具提示
            if (i == presetLabels.length - 1 && ImGui.isItemHovered()) {
                ImGui.setTooltip("这些预设等价于旧的\"随机/扩散/螺旋/切片/波浪\"等模式，但现在它们属于排序策略。\n" +
                        "入场(出现)与退场(擦除)都可以复用同一套排序。");
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("这些预设等价于旧的“随机/扩散/螺旋/切片/波浪”等模式，但现在它们属于排序策略。\n" +
                    "入场(出现)与退场(擦除)都可以复用同一套排序。");
        }

        UITheme.separator();

        // ========================= 主内容区域 =========================
        // ---------- 1. 切分方式 (Flow) ----------
        modified |= renderSequenceFlowSection(params);

        UITheme.separator();

        // ---------- 2. 起始锚点 (Origin) ----------
        modified |= renderSequenceOriginSection(params);

        UITheme.separator();

        // ---------- 3. 内部填充 (Fill) ----------
        modified |= renderSequenceFillSection(params);

        UITheme.separator();

        // ---------- 4. 层级分离度 + 修饰 ----------
        modified |= renderSequenceWeightAndModifiers(params);

        // ---------- 5. 节奏/并发（决定“同时/批次/分组推进”等时间映射） ----------
        UITheme.separator();
        modified |= renderSequenceTimingSection(clip, config, params);

        return modified;
    }

    private boolean renderSequenceTimingSection(TimelineClip clip, ClipAnimationConfig config,
            SequenceParameters params) {
        boolean modified = false;

        UITheme.label("5. 节奏/并发 (Timing)", UITheme.DarkTheme.TEXT_SELECTED);

        // 并发模式
        ConcurrencyMode[] conc = ConcurrencyMode.values();
        int concIdxVal = params.getConcurrencyMode().ordinal();
        ImInt concIdx = new ImInt(Math.max(0, Math.min(concIdxVal, conc.length - 1)));
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("并发模式", concIdx, getAllEnumDisplayNames(conc))) {
            params.setConcurrencyMode(conc[concIdx.get()]);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(params.getConcurrencyMode().getDescription());
        }

        // 时间映射策略
        TimeMappingStrategy[] maps = TimeMappingStrategy.values();
        int mapIdxVal = params.getTimeMappingStrategy().ordinal();
        ImInt mapIdx = new ImInt(Math.max(0, Math.min(mapIdxVal, maps.length - 1)));
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("时间映射", mapIdx, getAllEnumDisplayNames(maps))) {
            params.setTimeMappingStrategy(maps[mapIdx.get()]);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(params.getTimeMappingStrategy().getDescription());
        }

        // 全局延迟（叠加到 ClipAnimationConfig.delay 上）
        float[] globalDelay = { params.getGlobalDelay() };
        if (ImGui.dragFloat("序列全局延迟 (秒)", globalDelay, 0.05f, 0.0f, 30.0f, "%.2fs")) {
            params.setGlobalDelay(globalDelay[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("在片段自身 delay 基础上，再额外延迟“整套序列”的启动时间。");
        }

        // 批次参数：用于 IN_BATCHES / BATCH_MAPPING / GROUP_FIRST 的间隔
        boolean needsBatch = params.getConcurrencyMode() == ConcurrencyMode.IN_BATCHES
                || params.getTimeMappingStrategy() == TimeMappingStrategy.BATCH_MAPPING;
        boolean needsGroupGap = params.getTimeMappingStrategy() == TimeMappingStrategy.GROUP_FIRST
                || params.getTimeMappingStrategy() == TimeMappingStrategy.STAGGERED;

        if (needsBatch) {
            int[] bs = { params.getBatchSize() };
            if (ImGui.dragInt("批次大小 (batchSize)", bs, 1, 1, 10_000)) {
                params.setBatchSize(bs[0]);
                modified = true;
            }
            float[] bi = { params.getBatchInterval() };
            if (ImGui.dragFloat("批次间隔 (秒)", bi, 0.01f, 0.0f, 10.0f, "%.2fs")) {
                params.setBatchInterval(bi[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("批与批之间的间隔时间。实际播放会根据片段时长自动缩放/裁剪，避免排不下。");
            }
        } else if (needsGroupGap) {
            float[] gi = { params.getBatchInterval() };
            if (ImGui.dragFloat("组间隔 (秒)", gi, 0.01f, 0.0f, 10.0f, "%.2fs")) {
                // 复用 batchInterval 作为组间隔（避免新增字段破坏存档）
                params.setBatchInterval(gi[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("组与组之间的间隔时间（复用 batchInterval 字段）。");
            }
        }

        // 轻提示：这些字段现在已在运行时生效
        ImGui.textDisabled(String.format("片段长度 %.2fs / 动画时长 %.2fs / delay(含序列) %.2fs",
                clip.getLength(),
                config != null ? config.getDuration() : 0.0f,
                (config != null ? Math.max(0.0f, config.getDelay()) : 0.0f) + Math.max(0.0f, params.getGlobalDelay())));

        return modified;
    }

    /**
     * 1. 切分方式 Flow（两级选择：切分方式 + 平面方向）
     * <p>
     * NONE - 不切分（整体）
     * PLANAR - 平面切分（X/Y/Z/自定义角度）
     * SPHERICAL - 球形扩散
     * CYLINDRICAL - 柱形扩散
     * RANDOM - 完全随机
     */
    private boolean renderSequenceFlowSection(SequenceParameters params) {
        boolean modified = false;

        UITheme.label("1. 切分方式 (Flow)", UITheme.DarkTheme.TEXT_SELECTED);

        FlowType[] flows = FlowType.values();
        ImInt flowIdx = new ImInt(params.getFlowType().ordinal());

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##SeqFlow", flowIdx, getAllEnumDisplayNames(flows))) {
            params.setFlowType(flows[flowIdx.get()]);
            modified = true;
        }
        ImGui.popItemWidth();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("""
                    决定整体"波浪"如何在建筑中推进：
                    • 不切分：整体作为一个单元，只由内部填充决定顺序
                    • 平面切分：像扫描仪/盖楼一样一片片推进
                    • 球形扩散：从中心向外生长或爆炸
                    • 柱形扩散：从中心向外，但忽略高度
                    • 完全随机：没有明显流向，偏自然/噪点
                    """);
        }

        // 仅在 PLANAR 模式下显示平面方向选择
        if (params.getFlowType() == FlowType.PLANAR) {
            UITheme.label("平面方向", UITheme.DarkTheme.TEXT_SELECTED);

            // 快捷预设：X/Y/Z轴
            ImGui.textDisabled("快捷方向：");
            float planeYaw = params.getPlaneYaw();
            float planePitch = params.getPlanePitch();

            boolean xSelected = isAxisSelected(planeYaw, planePitch, 90.0f, 0.0f);
            if (renderAxisButton("X 轴", xSelected)) {
                params.setPlaneYaw(90.0f);
                params.setPlanePitch(0.0f);
                modified = true;
            }
            ImGui.sameLine();
            boolean ySelected = isAxisSelected(planeYaw, planePitch, 0.0f, 90.0f);
            if (renderAxisButton("Y 轴", ySelected)) {
                params.setPlaneYaw(0.0f);
                params.setPlanePitch(90.0f);
                modified = true;
            }
            ImGui.sameLine();
            boolean zSelected = isAxisSelected(planeYaw, planePitch, 0.0f, 0.0f);
            if (renderAxisButton("Z 轴", zSelected)) {
                params.setPlaneYaw(0.0f);
                params.setPlanePitch(0.0f);
                modified = true;
            }

            // 自定义角度开关
            ImGui.checkbox("自定义角度", showSequenceAdvanced);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("需要精细控制切片平面的倾斜角度时启用");
            }

            if (showSequenceAdvanced.get()) {
                float[] angles = { params.getPlaneYaw(), params.getPlanePitch() };
                if (ImGui.dragFloat2("平面角度 (Yaw/Pitch)", angles, 1.0f, -180.0f, 180.0f, "%.0f°")) {
                    params.setPlaneYaw(angles[0]);
                    params.setPlanePitch(angles[1]);
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("""
                            • Yaw：绕竖直轴旋转（0°=沿 Z 方向，90°=沿 X 方向）
                            • Pitch：0° 垂直切片，90° 水平切层
                            """);
                }
            }

            // Flow 层厚度（量化）：避免自定义角度时“层太碎”，让过程更像“盖一片片”
            float[] layerStep = { params.getLayerStep() };
            if (ImGui.dragFloat("层厚度 (layerStep)", layerStep, 0.1f, 0.1f, 32.0f, "%.1f")) {
                params.setLayerStep(layerStep[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("""
                        控制 Flow 的“层”有多厚：
                        1.0 = 每 1 格一个层（更细）
                        2.0 = 每 2 格一个层（更粗、更有片状推进感）
                        自定义角度时建议适当调大，避免层级被切得过碎。""");
            }

            // 视线对齐：将 Flow 方向对齐到当前摄像机视线（用于减少“先出现遮挡后出现”）
            if (ImGui.button("对齐到摄像机视线（远" + Icons.ARROW_RIGHT + "近）")) {
                Vec3d dir = null;
                try {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.player != null) {
                        // ⭐ 获取摄像机前向方向（从摄像机指向目标）
                        dir = client.player.getRotationVec(1.0f);
                    }
                } catch (Exception ignored) {
                }

                if (dir != null && dir.lengthSquared() > 1.0e-6) {
                    // ⭐ 反转方向：为了实现"远→近"排序，需要使用反向
                    // 因为 flowScore = rel.dotProduct(normal)，排序按升序
                    // 要让远处的方块先出现（更小的分数），需要让远处的点积为负
                    // 所以使用 -dir 作为 normal，这样远处的方块 rel.dotProduct(-dir) 为负且绝对值大
                    dir = dir.normalize().multiply(-1.0);

                    // 与 SequenceEvaluator.getNormalFromAngles 的参数约定一致：
                    // x = cos(pitch)*sin(yaw), z = cos(pitch)*cos(yaw), y = sin(pitch)
                    float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
                    float pitch = (float) Math.toDegrees(Math.asin(dir.y));
                    params.setPlaneYaw(yaw);
                    params.setPlanePitch(pitch);
                    modified = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("""
                        将 Flow 的推进方向对齐到当前摄像机视线。
                        排序会倾向于从远处开始出现、逐步向近处推进，减少“前面先出现把后面遮住”。
                        提示：这会把 Flow 变成“按深度切片”，更适合镜头演示建造过程。""");
            }
        }

        return modified;
    }

    /**
     * 2. 起始锚点 Origin
     * <p>
     * 决定"第一个方块"从哪里开始出现。
     * 根据FlowType限制选项：
     * - SPHERICAL：强制使用中心
     * - CYLINDRICAL：仅允许中心/底部中心
     * - PLANAR/NONE：允许所有选项
     */
    private boolean renderSequenceOriginSection(SequenceParameters params) {
        boolean modified = false;
        FlowType flow = params.getFlowType();

        UITheme.label("2. 起始锚点 (Origin)", UITheme.DarkTheme.TEXT_SELECTED);

        // 球形扩散：强制使用几何中心
        if (flow == FlowType.SPHERICAL) {
            if (params.getOriginType() != OriginType.CENTER) {
                params.setOriginType(OriginType.CENTER);
                modified = true;
            }
            ImGui.textDisabled("球形扩散：起点固定为几何中心");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("球形扩散必须从中心开始，无法自定义起点");
            }
            return modified;
        }

        // 柱形扩散：仅允许中心/底部中心
        OriginType[] availableOrigins;
        if (flow == FlowType.CYLINDRICAL) {
            availableOrigins = new OriginType[] { OriginType.BOTTOM_CENTER, OriginType.CENTER };
            // 如果当前是CUSTOM，自动重置为BOTTOM_CENTER
            if (params.getOriginType() == OriginType.CUSTOM || params.getOriginType() == OriginType.MIN_CORNER) {
                params.setOriginType(OriginType.BOTTOM_CENTER);
                modified = true;
            }
        } else {
            // PLANAR / NONE / RANDOM：允许所有选项
            availableOrigins = OriginType.values();
        }

        String[] originLabels = Arrays.stream(availableOrigins)
                .map(OriginType::getDisplayName)
                .toArray(String[]::new);

        int currentIndex = 0;
        for (int i = 0; i < availableOrigins.length; i++) {
            if (availableOrigins[i] == params.getOriginType()) {
                currentIndex = i;
                break;
            }
        }

        ImInt originIdx = new ImInt(currentIndex);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##SeqOrigin", originIdx, originLabels)) {
            params.setOriginType(availableOrigins[originIdx.get()]);
            modified = true;
        }
        ImGui.popItemWidth();

        if (ImGui.isItemHovered()) {
            if (flow == FlowType.CYLINDRICAL) {
                ImGui.setTooltip("""
                        柱形扩散的起点选项：
                        • 底部中心：从地面中心向外扩散
                        • 几何中心：从整体中心向外扩散
                        """);
            } else {
                ImGui.setTooltip("""
                        决定序列的"起点"：
                        • 最小角：从容器原点（通常是一角）开始
                        • 几何中心：从整体中心向外
                        • 底部中心：从地面中心往上，适合树/建筑生长
                        • 自定义：从你指定的坐标开始
                        """);
            }
        }

        if (params.getOriginType() == OriginType.CUSTOM) {
            int[] xyz = { params.getCustomX(), params.getCustomY(), params.getCustomZ() };
            if (ImGui.dragInt3("自定义坐标 (XYZ)", xyz)) {
                params.setCustomXYZ(xyz[0], xyz[1], xyz[2]);
                modified = true;
            }
        }

        return modified;
    }

    /**
     * 3. 内部填充 Fill
     * <p>
     * 在线条/切片内部，决定"谁先谁后"。
     * 支持 FLOOD（泛洪蔓延）和 FloodSeedMode 选择。
     */
    private boolean renderSequenceFillSection(SequenceParameters params) {
        boolean modified = false;

        UITheme.label("3. 内部填充 (Fill)", UITheme.DarkTheme.TEXT_SELECTED);

        FillType[] fills = FillType.values();
        ImInt fillIdx = new ImInt(params.getFillType().ordinal());

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("##SeqFill", fillIdx, getAllEnumDisplayNames(fills))) {
            params.setFillType(fills[fillIdx.get()]);
            modified = true;
        }
        ImGui.popItemWidth();

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("""
                    决定每一片区域内部的填充轨迹：
                    • 线性扫描：像打印机/扫描仪一样一行行扫过去
                    • 打印机扫描：先分行，再行内逐个（支持蛇形往返）
                    • 径向扩散：从切片中心向两边扩散
                    • 螺旋：绕中心画圈推进
                    • 平滑噪声：Perlin噪声，适合自然碎块/云状效果
                    • 泛洪蔓延：从起点沿相邻方块蔓延（BFS），适合火焰/藤蔓/生长感
                    """);
        }

        // RASTER 专属参数：行内扫描方向 / 换行步长 / 蛇形
        if (params.getFillType() == FillType.RASTER) {
            UITheme.label("打印机扫描设置", UITheme.DarkTheme.TEXT_SELECTED);

            // 根据当前 PLANAR 的“层平面”动态给出轴名；非轴对齐时退化为 U/V
            String[] scanAxisLabels = getStrings(params);
            String current = params.getRasterScanAxis(); // U/V
            int scanIdxVal = current.equals("V") ? 1 : 0;
            ImInt scanIdx = new ImInt(scanIdxVal);
            ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo("##RasterScanAxis", scanIdx, scanAxisLabels)) {
                params.setRasterScanAxis(scanIdx.get() == 1 ? "V" : "U");
                modified = true;
            }
            ImGui.popItemWidth();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("选择“行内扫描”沿哪条轴推进；另一条轴会作为“换行方向”。\n" +
                        "例如：XY 层里选 X，则每行沿 X 扫，按 Y 换行；选 Y 则反之。");
            }

            float[] rowStep = { params.getRasterRowStep() };
            if (ImGui.dragFloat("换行步长 (rowStep)", rowStep, 0.1f, 0.1f, 64.0f, "%.1f")) {
                params.setRasterRowStep(rowStep[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("每换一行跨越多少格。\n1.0=每1格换行；2.0=每2格换行（更粗的“行”）。");
            }

            ImBoolean zig = new ImBoolean(params.isRasterZigZag());
            if (ImGui.checkbox("行内蛇形往返 (ZigZag)", zig)) {
                params.setRasterZigZag(zig.get());
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("开启后：第1行从左到右，第2行从右到左……类似打印机回车换行的蛇形路径。");
            }
        }

        // FLOOD 专属参数：FloodSeedMode
        if (params.getFillType() == FillType.FLOOD) {
            UITheme.label("泛洪起点 (Flood Seed)", UITheme.DarkTheme.TEXT_SELECTED);

            FloodSeedMode[] seedModes = FloodSeedMode.values();
            ImInt seedIdx = new ImInt(params.getFloodSeedMode().ordinal());

            ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo("##FloodSeed", seedIdx, getAllEnumDisplayNames(seedModes))) {
                params.setFloodSeedMode(seedModes[seedIdx.get()]);
                modified = true;
            }
            ImGui.popItemWidth();

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("""
                        决定每层泛洪蔓延的起点：
                        • 每层中心：从该切片的几何中心开始蔓延
                        • 每层最小角：从该切片的 (minX,minY,minZ) 开始
                        • 每层随机点：每层随机挑选一个方块作为起点
                        • 全局随机：整个容器随机1个点，从上到下蔓延
                        """);
            }
        }

        return modified;
    }

    private static String @NotNull [] getStrings(SequenceParameters params) {
        String axisU = "U";
        String axisV = "V";
        try {
            if (params.getFlowType() == FlowType.PLANAR) {
                // 这里用与 SequenceEvaluator 一致的“是否轴对齐”判断阈值
                float yawDeg = params.getPlaneYaw();
                float pitchDeg = params.getPlanePitch();
                double yaw = Math.toRadians(yawDeg);
                double pitch = Math.toRadians(pitchDeg);
                double ny = Math.sin(pitch);
                double horiz = Math.cos(pitch);
                double nx = horiz * Math.sin(yaw);
                double nz = horiz * Math.cos(yaw);
                double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);

                if (az > 0.999) {
                    // XY 层
                    axisU = "X";
                    axisV = "Y";
                } else if (ay > 0.999) {
                    // XZ 层
                    axisU = "X";
                    axisV = "Z";
                } else if (ax > 0.999) {
                    // YZ 层
                    axisU = "Y";
                    axisV = "Z";
                }
            }
        } catch (Exception ignored) {
        }

        return new String[] {
                "行内扫描方向：" + axisU,
                "行内扫描方向：" + axisV
        };
    }

    /**
     * 4. 层级分离度 + 修饰
     * <p>
     * priorityWeight 控制 Flow 与 Fill 的"软硬程度"；
     * reverse / alternating 作为全局修饰符。
     */
    private boolean renderSequenceWeightAndModifiers(SequenceParameters params) {
        boolean modified = false;

        UITheme.label("4. 层级分离与修饰", UITheme.DarkTheme.TEXT_SELECTED);

        // 层级分离度
        ImGui.text("层级分离度 (硬度):");
        ImGui.sameLine();

        float[] weight = { params.getPriorityWeight() };
        // 0 = 完全融合，100 = 严格分层
        if (ImGui.sliderFloat("##SeqWeight", weight, 0.0f, 100.0f, "%.0f")) {
            params.setPriorityWeight(weight[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("""
                    控制 Flow 与 Fill 的混合程度：
                    • 0%：像流水一样整体推进，层与层之间模糊过渡
                    • 100%：当前层完全盖好后，才会开始下一层
                    """);
        }

        // 可视化提示：用文本模拟进度条
        float weightValue = params.getPriorityWeight();
        int filledBars = (int) (weightValue / 5.0f); // 每5%一个方块，共20个
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            bar.append(i < filledBars ? "#" : "-");
        }
        bar.append("] ").append(String.format("%.0f%%", weightValue));
        ImGui.textDisabled(bar.toString());
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("""
                    提示：
                    0%  = 柔和过渡（像水流）
                    100% = 严格分层（必须先完成上一层）
                    """);
        }

        // 修饰符
        ImGui.textDisabled("修饰：");
        ImGui.sameLine();

        ImBoolean rev = new ImBoolean(params.isReverse());
        if (ImGui.checkbox("反向 (Reverse)", rev)) {
            params.setReverse(rev.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("将整体播放顺序反转，例如从顶部往下建造。");
        }

        ImGui.sameLine();

        ImBoolean alt = new ImBoolean(params.isAlternating());
        if (ImGui.checkbox("往复/S 型 (ZigZag)", alt)) {
            params.setAlternating(alt.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("""
                    在相邻"层"之间交替反转内部填充顺序：
                    • 第一层从左到右，第二层从右到左，第三层再从左到右…
                    类似蛇形走位/耕地轨迹。
                    """);
        }

        return modified;
    }

    /**
     * 渲染「特效系统」面板（新版）
     * 
     * <p>
     * 结构：
     * <ul>
     * <li>1. 预设特效包（一键应用组合特效）</li>
     * <li>2. 已添加特效列表（带时间轴可视化）</li>
     * <li>3. 添加新特效按钮</li>
     * </ul>
     */
    private boolean renderEffectsSection(TimelineClip clip) {
        boolean modified = false;

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            ImGui.textDisabled("动画配置为空，无法编辑特效");
            return false;
        }

        // ✅ 旧高级参数迁移（保证旧片段“闪白光”等效果不会丢）
        com.chronoblocks.core.animation.LegacyAdvancedParamsMigrator.migrateIfNeeded(config);

        // ========== 1. 快捷添加 (Quick Add / Shortcuts) ==========
        UITheme.label("快捷特效 (Quick Add)", UITheme.DarkTheme.TEXT_SELECTED);

        // 第一排
        ImBoolean flash = new ImBoolean(hasEffectType(config, EffectType.FLASH_WHITE));
        if (ImGui.checkbox("闪白 (Flash)", flash)) {
            if (flash.get())
                ensureEffect(config, EffectType.FLASH_WHITE, 0.0f, 0.2f);
            else
                removeEffects(config, EffectType.FLASH_WHITE);
            modified = true;
        }
        ImGui.sameLine();
        ImBoolean dust = new ImBoolean(hasEffectType(config, EffectType.DUST_BURST));
        if (ImGui.checkbox("扬尘 (Dust)", dust)) {
            if (dust.get())
                ensureEffect(config, EffectType.DUST_BURST, 0.8f, 0.25f);
            else
                removeEffects(config, EffectType.DUST_BURST);
            modified = true;
        }

        // 第二排
        ImBoolean ripple = new ImBoolean(hasEffectType(config, EffectType.IMPACT_RIPPLE));
        if (ImGui.checkbox("涟漪 (Ripple)", ripple)) {
            if (ripple.get())
                ensureEffect(config, EffectType.IMPACT_RIPPLE, 0.85f, 0.3f);
            else
                removeEffects(config, EffectType.IMPACT_RIPPLE);
            modified = true;
        }
        ImGui.sameLine();
        ImBoolean trail = new ImBoolean(hasEffectType(config, EffectType.MOTION_TRAIL));
        if (ImGui.checkbox("尾迹 (Trail)", trail)) {
            if (trail.get())
                ensureEffect(config, EffectType.MOTION_TRAIL, 0.0f, 1.0f);
            else
                removeEffects(config, EffectType.MOTION_TRAIL);
            modified = true;
        }

        UITheme.separator();

        // ========== 2. 特效库 (Effect Library) ==========
        UITheme.label("特效库 (Library)", UITheme.DarkTheme.TEXT_SELECTED);

        // 功能性按钮行 (清空 & 粘贴)
        boolean hasEffects = !config.getEffects().isEmpty();
        boolean hasCopied = EffectClipboardManager.hasCopiedEffect();

        if (hasEffects || hasCopied) {
            if (hasEffects) {
                if (ImGui.button(Icons.DELETE + " 清空所有")) {
                    config.getEffects().clear();
                    modified = true;
                }
                if (ImGui.isItemHovered())
                    ImGui.setTooltip("清除当前片段的所有特效");
            }
            if (hasCopied) {
                if (hasEffects)
                    ImGui.sameLine();
                if (ImGui.button(Icons.CLIPBOARD + " 粘贴特效")) {
                    EffectInstance copied = EffectClipboardManager.getCopiedEffect();
                    if (copied != null) {
                        config.addEffect(copied);
                        modified = true;
                    }
                }
            }
            ImGui.spacing();
        }

        // 预设列表
        ImGui.textDisabled("组合预设 (Presets):");
        if (ImGui.beginChild("##PresetLibrary", 0, 80.0f, true)) {
            float availWidth = ImGui.getContentRegionAvailX();
            float btnWidth = (availWidth - ImGui.getStyle().getItemSpacingX()) * 0.5f;
            int count = 0;
            for (EffectPreset preset : EffectPresetRegistry.getPresets()) {
                if (count % 2 != 0)
                    ImGui.sameLine();
                if (ImGui.button(preset.getName() + "##" + count, btnWidth, 0)) {
                    preset.applyTo(config);
                    modified = true;
                }
                if (ImGui.isItemHovered())
                    ImGui.setTooltip(preset.getDescription());
                count++;
            }
        }
        ImGui.endChild();

        // 单个添加
        ImGui.textDisabled("手动添加 (Individual):");
        List<EffectType> allTypes = BlockEffectRegistry.getRegisteredTypes();
        String[] effectLabels = allTypes.stream().map(EffectType::getDisplayName).toArray(String[]::new);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.65f);
        ImGui.combo("##AddEffectCombo", addEffectSelectedIndex, effectLabels);
        ImGui.popItemWidth();
        ImGui.sameLine();
        if (ImGui.button("添加", ImGui.getContentRegionAvailX(), 0)) {
            EffectType type = allTypes.get(addEffectSelectedIndex.get());
            EffectInstance inst = getEffectInstance(type, config);
            IBlockEffect impl = BlockEffectRegistry.get(type);
            if (impl != null)
                inst.getParams().putAll(impl.getDefaultParams());
            config.addEffect(inst);
            modified = true;
        }

        UITheme.separator();

        // ========== 2. 已添加特效列表 ==========
        List<EffectInstance> effects = config.getEffects();
        if (effects.isEmpty()) {
            ImGui.textDisabled("当前未添加任何特效");
        } else {
            UITheme.label("当前特效", UITheme.DarkTheme.TEXT_SELECTED);

            int index = 0;
            for (EffectInstance inst : effects) {
                if (inst == null) {
                    continue;
                }

                String effectId = inst.getId().toString();
                ImGui.pushID("effect_" + index + "_" + effectId);

                // 折叠头
                ImGui.alignTextToFramePadding();
                String headerLabel = "[" + inst.getType().getDisplayName() + "]";
                boolean open = ImGui.treeNodeEx(headerLabel, ImGuiTreeNodeFlags.DefaultOpen);
                ImGui.sameLine();

                // 启用/禁用开关
                ImBoolean enabled = new ImBoolean(inst.isEnabled());
                if (ImGui.checkbox("启用##enable_" + index, enabled)) {
                    inst.setEnabled(enabled.get());
                    modified = true;
                }

                ImGui.sameLine();

                // 复制按钮
                if (ImGui.smallButton(Icons.COPY + "##copy_" + index)) {
                    EffectClipboardManager.setCopiedEffect(inst);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("复制此特效到剪贴板");
                }

                ImGui.sameLine();

                // 删除按钮
                if (ImGui.smallButton(Icons.DELETE + "##del_" + index)) {
                    // 使用标记删除，延后统一删除
                    inst.setMarkedForRemoval(true);
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("删除此特效");
                }

                if (open) {
                    // 2.1 时间轴设置（带可视化）
                    modified |= renderSingleEffectTimingUI(inst, clip);

                    // 2.2 特效自身参数 UI（委托给具体实现类）
                    IBlockEffect effectImpl = BlockEffectRegistry.get(inst.getType());
                    if (effectImpl != null) {
                        try {
                            UITheme.separator();
                            UITheme.label("特效参数", UITheme.DarkTheme.TEXT_SELECTED);
                            modified |= renderEffectParams(inst, inst.getType());
                        } catch (Exception e) {
                            LOGGER.error("渲染特效参数 UI 出错: {}", e.getMessage(), e);
                            ImGui.textColored(1f, 0f, 0f, 1f, "特效参数渲染错误: " + e.getMessage());
                        }
                    } else {
                        ImGui.textDisabled("未找到对应特效实现: " + inst.getType().name());
                    }

                    ImGui.treePop();
                }

                ImGui.popID();
                index++;
            }

            // 真正删除被标记的特效
            effects.removeIf(EffectInstance::isMarkedForRemoval);
        }

        return modified;
    }

    private static @NotNull EffectInstance getEffectInstance(EffectType type, ClipAnimationConfig config) {
        EffectInstance inst = new EffectInstance(type);
        // 默认时间（更克制）：尽量缩短窗口，避免大量方块时“屏幕充满粒子”
        // 注意：特效时间轴相对“单块动画窗口”（0..1）
        float start = 0.15f;
        float dur = 0.22f;
        if (type == EffectType.FLASH_WHITE) {
            start = 0.0f;
            dur = 0.12f;
        } else if (type == EffectType.DUST_BURST) {
            start = 0.85f;
            dur = 0.12f;
        } else if (type == EffectType.MAGIC_FLARE) {
            start = 0.10f;
            dur = 0.25f;
        } else if (type == EffectType.GLOW_PULSE) {
            start = 0.10f;
            dur = 0.25f;
        }
        inst.setStartOffset(start);
        inst.setDuration(dur);
        inst.setEnabled(true);
        inst.setPhase(config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER);
        if (type == EffectType.FLASH_WHITE || type == EffectType.DUST_BURST) {
            inst.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        }
        return inst;
    }

    private static boolean hasEffectType(ClipAnimationConfig config, EffectType type) {
        if (config == null || type == null)
            return false;
        List<EffectInstance> effects = config.getEffects();
        for (EffectInstance e : effects) {
            if (e != null && e.getType() == type && e.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    private static void ensureEffect(ClipAnimationConfig config, EffectType type, float startOffset, float duration) {
        if (config == null || type == null)
            return;
        for (EffectInstance e : config.getEffects()) {
            if (e != null && e.getType() == type) {
                e.setEnabled(true);
                return;
            }
        }
        EffectInstance inst = new EffectInstance(type);
        inst.setEnabled(true);
        inst.setPhase(config.getPhase() == com.chronoblocks.core.animation.ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER);
        // 粒子类特效默认 one-shot，更符合“到点爆一下”的预期
        if (type == EffectType.FLASH_WHITE || type == EffectType.DUST_BURST) {
            inst.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        }
        inst.setStartOffset(startOffset);
        inst.setDuration(duration);

        IBlockEffect impl = BlockEffectRegistry.get(type);
        if (impl != null) {
            Map<String, Object> defaults = impl.getDefaultParams();
            if (defaults != null) {
                inst.getParams().putAll(defaults);
            }
        }
        config.addEffect(inst);
    }

    private static void removeEffects(ClipAnimationConfig config, EffectType type) {
        if (config == null || type == null)
            return;
        config.getEffects().removeIf(e -> e != null && e.getType() == type);
    }

    /**
     * 渲染单个特效的时间轴 UI（带可视化预览）
     * 
     * @param inst 特效实例
     * @param clip 时间片段
     * @return 是否修改了参数
     */
    private boolean renderSingleEffectTimingUI(EffectInstance inst, TimelineClip clip) {
        boolean modified = false;

        UITheme.label("时间轴", UITheme.DarkTheme.TEXT_SELECTED);

        // ✅ 特效时间轴是“相对于单块动画窗口”的归一化（0..1），而不是相对于片段长度
        ClipAnimationConfig cfg = clip.getAnimationConfig();
        float clipLength = Math.max(clip.getLength(), 0.0001f);
        float animLen = 0.05f;
        if (cfg != null) {
            animLen = Math.max(0.05f, Math.min(cfg.getDuration(), clipLength));
        }

        // 作用阶段
        EffectInstance.EffectPhase[] phases = EffectInstance.EffectPhase.values();
        String[] phaseLabels = new String[] { "出现(Enter)", "擦除(Exit)", "两者(Both)" };
        int phaseIdx = inst.getPhase().ordinal();
        if (phaseIdx >= phases.length)
            phaseIdx = 2;
        ImInt phaseIdxIm = new ImInt(phaseIdx);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("作用阶段", phaseIdxIm, phaseLabels)) {
            int ni = Math.max(0, Math.min(phaseIdxIm.get(), phases.length - 1));
            inst.setPhase(phases[ni]);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制该特效在哪种片段阶段生效：出现片段 / 擦除片段 / 两者都生效。\n" +
                    "注意：如果特效阶段与片段阶段不匹配，特效不会触发。");
        }

        // ✅ 检查阶段是否匹配，如果不匹配则显示警告
        ClipAnimationConfig.AnimationPhase clipPhase = cfg != null ? cfg.getPhase()
                : ClipAnimationConfig.AnimationPhase.ENTER;
        EffectInstance.EffectPhase effectPhase = inst.getPhase();
        boolean phaseMismatch = (effectPhase != EffectInstance.EffectPhase.BOTH)
                && ((clipPhase == ClipAnimationConfig.AnimationPhase.ENTER
                        && effectPhase != EffectInstance.EffectPhase.ENTER)
                        || (clipPhase == ClipAnimationConfig.AnimationPhase.EXIT
                                && effectPhase != EffectInstance.EffectPhase.EXIT));
        if (phaseMismatch) {
            ImGui.textColored(1.0f, 0.6f, 0.0f, 1.0f, "⚠ 警告：特效阶段与片段阶段不匹配，特效可能不会触发！");
            if (ImGui.isItemHovered()) {
                String clipPhaseName = clipPhase == ClipAnimationConfig.AnimationPhase.ENTER ? "出现(Enter)" : "擦除(Exit)";
                String effectPhaseName = effectPhase == EffectInstance.EffectPhase.ENTER ? "出现(Enter)"
                        : effectPhase == EffectInstance.EffectPhase.EXIT ? "擦除(Exit)" : "两者(Both)";
                ImGui.setTooltip("当前片段阶段：" + clipPhaseName +
                        "\n特效阶段：" + effectPhaseName +
                        "\n建议：将特效阶段设置为'两者(Both)'或与片段阶段匹配。");
            }
        }

        // 触发模式
        EffectInstance.TriggerMode[] triggerModes = EffectInstance.TriggerMode.values();
        String[] triggerLabels = new String[] { "连续(每帧)", "一次(One-shot)" };
        int trigIdx = inst.getTriggerMode().ordinal();
        if (trigIdx >= triggerModes.length)
            trigIdx = 0;
        ImInt trigIdxIm = new ImInt(trigIdx);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.combo("触发方式", trigIdxIm, triggerLabels)) {
            int ni = Math.max(0, Math.min(trigIdxIm.get(), triggerModes.length - 1));
            inst.setTriggerMode(triggerModes[ni]);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("连续：窗口内每帧触发（适合持续光效）。\n一次：进入窗口时只触发一次（适合爆裂/闪白光，性能更稳）。");
        }

        // startOffset 和 duration 都是归一化的 [0..1]
        float startNorm = inst.getStartOffset();
        float durNorm = inst.getDuration();

        // 转换为实际秒数显示（相对于单块动画窗口）
        float startSec = startNorm * animLen;
        float durSec = durNorm > 0.0f ? durNorm * animLen : animLen;

        float[] startBuf = { startSec };
        float[] durBuf = { durSec };

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        if (ImGui.dragFloat("开始时间 (秒)", startBuf, 0.02f, 0.0f, animLen, "%.2f")) {
            startBuf[0] = Math.max(0f, Math.min(startBuf[0], animLen));
            inst.setStartOffset(startBuf[0] / animLen);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("相对于“单个方块动画起点”的时间偏移。");
        }

        ImGui.sameLine();

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        if (ImGui.dragFloat("持续时长 (秒)", durBuf, 0.02f, 0.0f, animLen, "%.2f")) {
            durBuf[0] = Math.max(0.0f, Math.min(durBuf[0], animLen));
            inst.setDuration(durBuf[0] / animLen);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("特效从开始到结束的持续时间（相对于单块动画窗口）。\n" +
                    "单块动画窗口长度：" + String.format("%.2f", animLen) + "秒\n" +
                    "提示：后段特效（如 startOffset > 0.8）适合在动画结束时触发。");
        }

        // ✅ 添加提示：说明特效时间窗口的概念
        ImGui.textDisabled("提示：特效时间窗口是相对于单个方块动画窗口的归一化时间（0..1）。");
        ImGui.textDisabled(String.format("当前方块动画窗口：%.2f秒 | 特效窗口：[%.2f, %.2f]秒",
                animLen, startSec, startSec + durSec));

        // 简单的时间条预览 [====effect====]
        float previewWidth = ImGui.getContentRegionAvailX();
        float height = ImGui.getTextLineHeight() * 1.2f;
        ImVec2 cursor = ImGui.getCursorScreenPos();

        ImGui.invisibleButton("##EffectTimelinePreview", previewWidth, height);
        ImDrawList dl = ImGui.getWindowDrawList();

        float x = cursor.x;
        float y = cursor.y;

        int bgColor = ImGui.getColorU32(ImGuiCol.FrameBg);
        int barColor = ImGui.getColorU32(ImGuiCol.PlotHistogram);

        dl.addRectFilled(x, y, x + previewWidth, y + height, bgColor, 4.0f);

        float actualStartNorm = inst.getStartOffset();
        float actualDurNorm = inst.getDuration() > 0.0f ? inst.getDuration() : 1.0f;
        float startX = x + previewWidth * actualStartNorm;
        float endX = x + previewWidth * Math.min(1.0f, actualStartNorm + actualDurNorm);

        dl.addRectFilled(startX, y, endX, y + height, barColor, 3.0f);

        // 边框
        int borderColor = ImGui.getColorU32(ImGuiCol.Border);
        dl.addRect(x, y, x + previewWidth, y + height, borderColor, 4.0f);

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(String.format(
                    "开始: %.2fs (%.0f%%)\n结束: %.2fs (%.0f%%)",
                    actualStartNorm * animLen,
                    actualStartNorm * 100f,
                    (actualStartNorm + actualDurNorm) * animLen,
                    (actualStartNorm + actualDurNorm) * 100f));
        }

        return modified;
    }

    /**
     * 渲染Easing曲线预览（参考Unity Animator和After Effects的曲线编辑器）
     */
    private void renderEasingCurvePreview(EasingFunction easingFunc) {
        if (easingFunc == null) {
            ImGui.textDisabled("无法预览：缓动函数为空");
            return;
        }

        // 更新缓存（仅在缓动函数改变时）
        if (cachedEasingFunction != easingFunc) {
            for (int i = 0; i < EASING_CURVE_SAMPLES; i++) {
                float t = i / (float) (EASING_CURVE_SAMPLES - 1);
                easingCurveCache[i] = easingFunc.apply(t);
            }
            cachedEasingFunction = easingFunc;
        }

        // 计算曲线的实际范围（可能超出 [0,1]）
        float minValue = easingCurveCache[0];
        float maxValue = easingCurveCache[0];
        for (int i = 1; i < EASING_CURVE_SAMPLES; i++) {
            float value = easingCurveCache[i];
            if (value < minValue)
                minValue = value;
            if (value > maxValue)
                maxValue = value;
        }

        // 确保有合理的范围（避免除以零）
        float valueRange = maxValue - minValue;
        if (valueRange < 0.0001f) {
            valueRange = 1.0f; // 如果值几乎不变，使用默认范围
            minValue = 0.0f;
        }

        // 绘制曲线预览区域
        float totalWidth = ImGui.getContentRegionAvailX();
        float totalHeight = 80.0f; // 固定高度，类似Unity的曲线预览

        // 添加边距（出血区域），缩放0.9，参考 easings.net
        float padding = Math.min(totalWidth, totalHeight) * 0.05f; // 5% 边距
        float chartWidth = totalWidth - padding * 2;
        float chartHeight = totalHeight - padding * 2;

        ImVec2 cursor = ImGui.getCursorScreenPos();
        float x = cursor.x;
        float y = cursor.y;

        // 计算实际的绘制区域坐标
        float chartStartX = x + padding;
        float chartStartY = y + padding;
        float chartEndX = chartStartX + chartWidth;
        float chartEndY = chartStartY + chartHeight;

        // 创建不可见按钮作为交互区域
        ImGui.invisibleButton("##EasingCurvePreview", totalWidth, totalHeight);
        ImDrawList drawList = ImGui.getWindowDrawList();

        // 绘制背景（整个区域）
        int bgColor = ImGui.getColorU32(ImGuiCol.FrameBg);
        drawList.addRectFilled(x, y, x + totalWidth, y + totalHeight, bgColor, 4.0f);

        // 设置裁剪区域，确保所有绘制内容都在边界内
        drawList.pushClipRect(x, y, x + totalWidth, y + totalHeight, true);

        // 绘制网格线（参考 easings.net，更密集的网格）
        // 主要网格线（粗线，4等分）
        int mainGridColor = ImGui.getColorU32(ImGuiCol.Border, 0.3f);
        for (int i = 1; i < 4; i++) {
            float gx = chartStartX + chartWidth * i / 4.0f;
            drawList.addLine(gx, chartStartY, gx, chartEndY, mainGridColor, 1.0f);
        }
        for (int i = 1; i < 4; i++) {
            float gy = chartStartY + chartHeight * i / 4.0f;
            drawList.addLine(chartStartX, gy, chartEndX, gy, mainGridColor, 1.0f);
        }

        // 次要网格线（细线，8等分）
        int minorGridColor = ImGui.getColorU32(ImGuiCol.Border, 0.15f);
        for (int i = 1; i < 8; i++) {
            if (i % 2 != 0) { // 跳过主要网格线的位置
                float gx = chartStartX + chartWidth * i / 8.0f;
                drawList.addLine(gx, chartStartY, gx, chartEndY, minorGridColor, 0.5f);
            }
        }
        for (int i = 1; i < 8; i++) {
            if (i % 2 != 0) { // 跳过主要网格线的位置
                float gy = chartStartY + chartHeight * i / 8.0f;
                drawList.addLine(chartStartX, gy, chartEndX, gy, minorGridColor, 0.5f);
            }
        }

        // 绘制边框（仅绘制图表区域）
        int borderColor = ImGui.getColorU32(ImGuiCol.Border);
        drawList.addRect(chartStartX, chartStartY, chartEndX, chartEndY, borderColor, 0.0f, 0, 1.0f);

        // 绘制参考线（线性，从(0,1)到(1,0)）
        int refColor = ImGui.getColorU32(ImGuiCol.TextDisabled, 0.4f);
        drawList.addLine(chartStartX, chartEndY, chartEndX, chartStartY, refColor, 1.0f);

        // 绘制缓动曲线
        // 关键：根据曲线的实际范围进行归一化和缩放，保持曲线形状不变
        int curveColor = ImGui.getColorU32(ImGuiCol.PlotLines);
        float prevX = chartStartX;
        float prevValue = easingCurveCache[0];
        // 将值从 [minValue, maxValue] 映射到 [0, chartHeight]，然后反转Y轴
        float normalizedPrevValue = (prevValue - minValue) / valueRange; // 归一化到 [0, 1]
        float prevY = chartStartY + chartHeight - normalizedPrevValue * chartHeight;

        for (int i = 1; i < EASING_CURVE_SAMPLES; i++) {
            float t = i / (float) (EASING_CURVE_SAMPLES - 1);
            float currentX = chartStartX + chartWidth * t;
            float currentValue = easingCurveCache[i];
            // 归一化到 [0, 1]，然后映射到图表高度
            float normalizedCurrentValue = (currentValue - minValue) / valueRange;
            float currentY = chartStartY + chartHeight - normalizedCurrentValue * chartHeight;

            drawList.addLine(prevX, prevY, currentX, currentY, curveColor, 2.5f);
            prevX = currentX;
            prevY = currentY;
        }

        // 恢复裁剪区域
        drawList.popClipRect();

        // 绘制外边框（整个预览区域）
        int outerBorderColor = ImGui.getColorU32(ImGuiCol.Border, 0.5f);
        drawList.addRect(x, y, x + totalWidth, y + totalHeight, outerBorderColor, 4.0f, 0, 1.5f);

        // 工具提示
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("缓动曲线预览\nX轴：时间进度 (0.0 - 1.0)\nY轴：缓动值 (0.0 - 1.0)\n虚线：线性参考");
        }
    }

    /**
     * 绘制帮助提示框
     */
    private void drawHelpBox(String text) {

        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.15f, 0.15f, 0.15f, 0.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 4.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8.0f, 8.0f);

        float width = ImGui.getContentRegionAvailX();
        float height = ImGui.calcTextSize(text).y + 16.0f;
        if (ImGui.beginChild("##HelpBox", width, height, false)) {

            ImGui.textWrapped(text);

        }

        ImGui.endChild();
        ImGui.popStyleVar(2);
        ImGui.popStyleColor();

    }

    /**
     * 将 int 颜色值转换为 float[4] 数组 (RGBA, 0.0-1.0)
     *
     * <p>
     * 注意：TimelineColors.rgba() 返回的格式是 ABGR（Alpha, Blue, Green, Red）
     * 位布局：A(24-31) | B(16-23) | G(8-15) | R(0-7)
     * 
     * <p>
     * 转换后的 float 数组是 RGBA 顺序，用于 ImGui.colorEdit4
     */
    private float[] colorIntToFloat(int color) {

        // ABGR 格式解析

        int r = (color) & 0xFF; // R 在最低位 (0-7)
        int g = (color >> 8) & 0xFF; // G 在 8-15 位
        int b = (color >> 16) & 0xFF; // B 在 16-23 位
        int a = (color >> 24) & 0xFF; // A 在最高位 (24-31)
        // 返回 RGBA 顺序的 float 数组（ImGui 使用）
        return new float[] {
                r / 255.0f,
                g / 255.0f,
                b / 255.0f,
                a / 255.0f
        };

    }

    /**
     * 将 float[4] 数组 (RGBA, 0.0-1.0) 转换为 int 颜色值
     * <p>
     * 注意：TimelineColors.rgba() 返回的格式是 ABGR（Alpha, Blue, Green, Red）
     * 位布局：A(24-31) | B(16-23) | G(8-15) | R(0-7)
     * <p>
     * 输入的 float 数组是 RGBA 顺序（来自 ImGui.colorEdit4）
     */
    private int colorFloatToInt(float[] rgba) {

        int r = (int) (rgba[0] * 255.0f) & 0xFF;
        int g = (int) (rgba[1] * 255.0f) & 0xFF;
        int b = (int) (rgba[2] * 255.0f) & 0xFF;
        int a = (int) (rgba[3] * 255.0f) & 0xFF;
        // 转换为 ABGR 格式：A(24-31) | B(16-23) | G(8-15) | R(0-7)
        return (a << 24) | (b << 16) | (g << 8) | r;

    }

    /**
     * 渲染特效参数 UI（重构后：委托给具体实现类）
     * 
     * @param instance 特效实例
     * @param type     特效类型
     * @return 是否修改了参数
     */
    private boolean renderEffectParams(EffectInstance instance, EffectType type) {
        IBlockEffect effectImpl = BlockEffectRegistry.get(type);
        if (effectImpl != null) {
            // 直接委托给具体实现类的 renderUI 方法
            return effectImpl.renderUI(instance.getParams());
        } else {
            ImGui.textDisabled("⚠ 未找到实现: " + type.name());
            return false;
        }
    }

    /**
     * 渲染平面切分轴按钮，支持选中高亮
     */
    private boolean renderAxisButton(String label, boolean selected) {
        if (selected) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.55f, 0.95f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.28f, 0.62f, 1.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.16f, 0.45f, 0.85f, 1.0f);
        }
        boolean clicked = ImGui.smallButton(label);
        if (selected) {
            ImGui.popStyleColor(3);
        }
        return clicked;
    }

    /**
     * 判断当前平面角度是否匹配指定轴（带容差）
     */
    private boolean isAxisSelected(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        final float EPSILON = 0.01f;
        return Math.abs(currentYaw - targetYaw) < EPSILON &&
                Math.abs(currentPitch - targetPitch) < EPSILON;
    }

    /**
     * 直接使用 ImGui 渲染动画参数（不依赖 UIHelper 的反射）
     */
    private boolean renderAnimationParamsDirectly(AnimationMode mode, Map<String, Object> params) {
        boolean modified = false;

        switch (mode) {
            case FLY_IN:
            case FLY_OUT: {
                // 飞入/飞离方向
                String direction = getStringParam(params, "direction", "X+");
                String[] directions = { "X+", "X-", "Y+", "Y-", "Z+", "Z-" };
                int dirIndex = 0;
                for (int i = 0; i < directions.length; i++) {
                    if (directions[i].equals(direction)) {
                        dirIndex = i;
                        break;
                    }
                }
                ImInt dirIdx = new ImInt(dirIndex);
                if (ImGui.combo("飞入方向", dirIdx, directions)) {
                    params.put("direction", directions[dirIdx.get()]);
                    modified = true;
                }

                // 飞行距离
                float flyDistance = getFloatParam(params, "flyDistance", 10.0f);
                float[] distanceArray = { flyDistance };
                if (ImGui.dragFloat("飞行距离", distanceArray, 1.0f, 5.0f, 50.0f, "%.0f 格")) {
                    params.put("flyDistance", distanceArray[0]);
                    modified = true;
                }
                break;
            }
            case FALL_DOWN:
            case GRAVITY_FALL: {
                if (mode == AnimationMode.FALL_DOWN) {
                    // 下落高度
                    float height = getFloatParam(params, "height", 10.0f);
                    float[] heightArray = { height };
                    if (ImGui.dragFloat("下落高度", heightArray, 0.5f, 0.0f, 50.0f, "%.1f 格")) {
                        params.put("height", heightArray[0]);
                        modified = true;
                    }

                    // 回弹力度
                    float bounciness = getFloatParam(params, "bounciness", 0.3f);
                    float[] bouncinessArray = { bounciness };
                    if (ImGui.dragFloat("回弹力度", bouncinessArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
                        params.put("bounciness", bouncinessArray[0]);
                        modified = true;
                    }
                } else {
                    // 重力坍塌：下落速度
                    float fallSpeed = getFloatParam(params, "fallSpeed", 5.0f);
                    float[] speedArray = { fallSpeed };
                    if (ImGui.dragFloat("下落速度", speedArray, 0.5f, 1.0f, 20.0f, "%.1f 格/秒")) {
                        params.put("fallSpeed", speedArray[0]);
                        modified = true;
                    }
                }

                // 翻滚旋转（两个模式都有）
                boolean tumble = getBooleanParam(params, true);
                ImBoolean tumbleBool = new ImBoolean(tumble);
                if (ImGui.checkbox("翻滚旋转", tumbleBool)) {
                    params.put("tumble", tumbleBool.get());
                    modified = true;
                }

                // 旋转强度（仅在启用翻滚时显示）
                if (tumbleBool.get()) {
                    float tumbleIntensity = getFloatParam(params, "tumbleIntensity", 1.0f);
                    float[] intensityArray = { tumbleIntensity };
                    if (ImGui.dragFloat("旋转强度", intensityArray, 0.1f, 0.0f, 3.0f, "%.1f")) {
                        params.put("tumbleIntensity", intensityArray[0]);
                        modified = true;
                    }
                }
                break;
            }
            case GROW_UP: {
                // 生长轴向
                String axis = getStringParam(params, "axis", "Y");
                String[] axes = { "X", "Y", "Z", "XYZ" };
                int axisIndex = 0;
                for (int i = 0; i < axes.length; i++) {
                    if (axes[i].equals(axis)) {
                        axisIndex = i;
                        break;
                    }
                }
                ImInt axisIdx = new ImInt(axisIndex);
                if (ImGui.combo("生长轴向", axisIdx, axes)) {
                    params.put("axis", axes[axisIdx.get()]);
                    modified = true;
                }

                // 缩放锚点
                String pivot = getStringParam(params, "pivot", "BOTTOM");
                String[] pivots = { "CENTER", "BOTTOM" };
                int pivotIndex = pivot.equals("BOTTOM") ? 1 : 0;
                ImInt pivotIdx = new ImInt(pivotIndex);
                if (ImGui.combo("缩放锚点", pivotIdx, pivots)) {
                    params.put("pivot", pivots[pivotIdx.get()]);
                    modified = true;
                }

                // 升起距离
                float distance = getFloatParam(params, "distance", 0.0f);
                float[] distanceArray = { distance };
                if (ImGui.dragFloat("升起距离", distanceArray, 0.5f, 0.0f, 20.0f, "%.1f 格")) {
                    params.put("distance", distanceArray[0]);
                    modified = true;
                }
                break;
            }
            case SHAKER: {
                // 震动幅度
                float intensity = getFloatParam(params, "intensity", 0.5f);
                float[] intensityArray = { intensity };
                if (ImGui.dragFloat("震动幅度", intensityArray, 0.05f, 0.0f, 2.0f, "%.2f")) {
                    params.put("intensity", intensityArray[0]);
                    modified = true;
                }

                // 衰减速度
                float decay = getFloatParam(params, "decay", 0.8f);
                float[] decayArray = { decay };
                if (ImGui.dragFloat("衰减速度", decayArray, 0.05f, 0.1f, 1.0f, "%.2f")) {
                    params.put("decay", decayArray[0]);
                    modified = true;
                }
                break;
            }
            case IMPLODE:
            case SHRINK_OUT: {
                // 目标比例
                String paramKey = mode == AnimationMode.IMPLODE ? "scale" : "targetScale";
                float scale = getFloatParam(params, paramKey, 0.0f);
                float[] scaleArray = { scale };
                String label = "目标比例";
                if (ImGui.dragFloat(label, scaleArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    params.put(paramKey, scaleArray[0]);
                    modified = true;
                }
                break;
            }
            case EXPAND: {
                // 扩散速度
                float expandSpeed = getFloatParam(params, "expandSpeed", 1.0f);
                float[] speedArray = { expandSpeed };
                if (ImGui.dragFloat("扩散速度", speedArray, 0.1f, 0.1f, 5.0f, "%.1f")) {
                    params.put("expandSpeed", speedArray[0]);
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制方块从中心膨胀扩散的速度，越大越快。");
                }

                // 使用距离
                boolean useDistance = true;
                Object udVal = params.get("useDistance");
                if (udVal instanceof Boolean b) useDistance = b;
                else if (udVal instanceof String s) useDistance = Boolean.parseBoolean(s);
                ImBoolean useDistBool = new ImBoolean(useDistance);
                if (ImGui.checkbox("基于距离缩放", useDistBool)) {
                    params.put("useDistance", useDistBool.get());
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("勾选后，离中心越远的方块缩放越大（更有扩散感）。");
                }
                break;
            }
            case ASSEMBLE: {
                // 散开半径（用于 ENTER 阶段的碎片聚合）
                float spread = getFloatParam(params, "spread", 5.0f);
                float[] spreadArray = { spread };
                if (ImGui.dragFloat("散开半径", spreadArray, 0.5f, 1.0f, 20.0f, "%.1f 格")) {
                    params.put("spread", spreadArray[0]);
                    modified = true;
                }
                break;
            }
            case EXPLODE: {
                // 爆炸参数（用于 EXIT 阶段的碎片散射）
                // 散射半径
                float radius = getFloatParam(params, "radius", 2.0f);
                float[] radiusArray = { radius };
                if (ImGui.dragFloat("散射半径", radiusArray, 0.1f, 0.1f, 20.0f, "%.1f 格")) {
                    params.put("radius", radiusArray[0]);
                    modified = true;
                }

                // 上抛偏置
                float upBias = getFloatParam(params, "upBias", 0.25f);
                float[] upBiasArray = { upBias };
                if (ImGui.dragFloat("上抛偏置", upBiasArray, 0.05f, -1.0f, 2.0f, "%.2f")) {
                    params.put("upBias", upBiasArray[0]);
                    modified = true;
                }

                // 最小缩放
                float scaleMin = getFloatParam(params, "scaleMin", 0.7f);
                float[] scaleMinArray = { scaleMin };
                if (ImGui.dragFloat("最小缩放", scaleMinArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    params.put("scaleMin", scaleMinArray[0]);
                    modified = true;
                }
                break;
            }
            case FADE_OUT: {
                // 起始透明度
                float startOpacity = getFloatParam(params, "startOpacity", 1.0f);
                float[] startArray = { startOpacity };
                if (ImGui.dragFloat("起始透明度", startArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    params.put("startOpacity", startArray[0]);
                    modified = true;
                }

                // 结束透明度
                float endOpacity = getFloatParam(params, "endOpacity", 0.0f);
                float[] endArray = { endOpacity };
                if (ImGui.dragFloat("结束透明度", endArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    params.put("endOpacity", endArray[0]);
                    modified = true;
                }
                break;
            }
            case POP_IN: {
                // 初始缩放
                float initialScale = getFloatParam(params, "initialScale", 0.0f);
                float[] scaleArray = { initialScale };
                if (ImGui.dragFloat("初始缩放", scaleArray, 0.05f, 0.0f, 1.0f, "%.2f")) {
                    params.put("initialScale", scaleArray[0]);
                    modified = true;
                }

                // 弹性程度
                float elasticity = getFloatParam(params, "elasticity", 1.5f);
                float[] elasticityArray = { elasticity };
                if (ImGui.dragFloat("弹性程度", elasticityArray, 0.1f, 0.5f, 3.0f, "%.1f")) {
                    params.put("elasticity", elasticityArray[0]);
                    modified = true;
                }
                break;
            }
            default:
                // 其他模式可能没有参数或使用默认渲染
                break;
        }

        return modified;
    }

    /**
     * 从 params Map 中安全获取浮点参数
     */
    private float getFloatParam(Map<String, Object> params, String key, float defaultValue) {
        if (params == null || key == null)
            return defaultValue;
        Object val = params.get(key);
        switch (val) {
            case null -> {
                return defaultValue;
            }
            case Number number -> {
                return number.floatValue();
            }
            case String s -> {
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            default -> {
            }
        }
        return defaultValue;
    }

    /**
     * 从 params Map 中安全获取布尔参数
     */
    private boolean getBooleanParam(Map<String, Object> params, boolean defaultValue) {
        if (params == null)
            return defaultValue;
        Object val = params.get("tumble");
        return switch (val) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> defaultValue;
        };
    }

    /**
     * 从 params Map 中安全获取字符串参数
     */
    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        if (params == null || key == null)
            return defaultValue;
        Object val = params.get(key);
        if (val == null)
            return defaultValue;
        return val.toString();
    }
}
