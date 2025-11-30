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
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.animation.sequence.BlockSequenceGenerator;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import net.minecraft.util.math.BlockPos;
import com.chronoblocks.core.animation.sequence.SequenceMode;
import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.core.animation.sequence.evaluator.FillType;
import com.chronoblocks.core.animation.sequence.evaluator.FlowType;
import com.chronoblocks.core.animation.sequence.evaluator.FloodSeedMode;
import com.chronoblocks.core.animation.sequence.evaluator.OriginType;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.theme.Icons;
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
 * <p>基于ImGui的属性编辑面板。
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

    private final ImBoolean flashWhite = new ImBoolean(false);

    // 序列行为预览状态
    private final ImBoolean showSequencePreview = new ImBoolean(false);
    
    // ==== Effects UI state ====
    private final ImInt addEffectSelectedIndex = new ImInt(0);
    
    // 序列行为高级选项折叠
    private final ImBoolean showSequenceAdvanced = new ImBoolean(false);
    
    // Easing曲线预览缓存（避免每帧重新计算）
    private static final int EASING_CURVE_SAMPLES = 100;
    private float[] easingCurveCache = new float[EASING_CURVE_SAMPLES];
    private EasingFunction cachedEasingFunction = null;

    // 静态缓存标签数组 (减少GC)
    private static final String[] ANIMATION_MODE_LABELS = buildEnumLabels(AnimationMode.values());
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
                EASING_FUNCTION_LABELS = new String[]{"线性"};
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

        // 1. 基础设置组 - 默认展开
        if (ImGui.collapsingHeader(Icons.SETTINGS + " 基础设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderBasicProperties(clip);
            //UITheme.spacing();
        }

        // 2. 序列行为组 - 默认展开
        if (ImGui.collapsingHeader(Icons.LAYERS + " 序列行为", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderSequenceBehavior(clip);
            //UITheme.spacing();
        }

        // 3. 动画设置组 - 默认展开
        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderAnimationSettings(clip);
            //UITheme.spacing();
        }

        // 4. 特效 && 特效预设组
        List<EffectInstance> effects = clip.getAnimationConfig() != null ? 
                clip.getAnimationConfig().getEffects() : java.util.Collections.emptyList();
        String effectsHeader = Icons.PARTICLE + " 特效 (Effects)";
        if (!effects.isEmpty()) {
            effectsHeader += " (" + effects.size() + ")";
        }
        if (ImGui.collapsingHeader(effectsHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderEffectsSection(clip);
            //UITheme.spacing();
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
        float[] startVal = {clip.getStartTime()};
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
        float[] lengthVal = {clip.getLength()};
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
            ImGui.indent();
            float[] opacity = {clip.getAutoFadeOpacity()};
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
            ImGui.unindent();
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
     * <p>结构：
     * <ul>
     *   <li>1. 总体节奏（模式 + 时长）</li>
     *   <li>2. 缓动曲线（枚举 + 曲线预览）</li>
     *   <li>3. 动画模式参数（位移 / 缩放 / 旋转等）</li>
     *   <li>4. 高级修饰（透明度 / 闪白光等）</li>
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

        // ===== 1. 总体节奏：模式 + 时长 =====
        UITheme.label("动画模式与节奏", UITheme.DarkTheme.TEXT_SELECTED);

            // 模式选择
            int modeOrdinal = currentMode.ordinal();
            if (modeOrdinal >= ANIMATION_MODE_LABELS.length) {
                modeOrdinal = 0;
            }
            animationModeIndex.set(modeOrdinal);

            ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.combo("##AnimMode", animationModeIndex, ANIMATION_MODE_LABELS)) {
                int newIndex = animationModeIndex.get();
                if (newIndex < 0 || newIndex >= AnimationMode.values().length) {
                    newIndex = 0;
                }
                AnimationMode newMode = AnimationMode.values()[newIndex];
                config.setMode(newMode);
                currentMode = newMode;

                // 智能默认缓动函数
                try {
                    IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(newMode);
                    if (transformer != null) {
                        String recommended = transformer.getRecommendedEasing();
                        if (recommended != null && !recommended.isEmpty()) {
                            config.setEasing(recommended);
                            currentEasing = recommended;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("获取推荐缓动函数时出错: {}", e.getMessage());
                }

                modified = true;
            }
            ImGui.popItemWidth();
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("选择动画入场方式");
            }

            // 模式描述（来自 AnimationMode 描述）
            //UITheme.spacingSmall();
            try {
                drawHelpBox(currentMode.getDescription());
            } catch (Exception e) {
                LOGGER.error("显示模式描述时出错: {}", e.getMessage(), e);
            }

            // 动画时长（相对于片段长度的绝对秒数）
            //UITheme.spacingSmall();
            float[] duration = {config.getDuration()};
            if (ImGui.dragFloat("动画时长 (秒)", duration, 0.05f, 0.0f, clip.getLength(), "%.2fs")) {
                if (duration[0] < 0.0f) duration[0] = 0.0f;
                config.setDuration(duration[0]);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("控制单个方块从开始运动到完全就位所用的时间。\n" +
                                 "建议不超过片段总时长。");
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

            //UITheme.spacingSmall();
            // 可视化曲线预览（你已经实现了）
            renderEasingCurvePreview(currentEasingFunc);

            UITheme.separator();

            // ===== 3. 模式特定参数 =====
            UITheme.label("动画细节参数", UITheme.DarkTheme.TEXT_SELECTED);
            Map<String, Object> params = config.getParams();

            // 填入默认值，避免 NPE
            try {
                IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(currentMode);
                if (transformer != null) {
                    Map<String, Object> defaultParams = transformer.getDefaultParams();
                    if (defaultParams != null) {
                        defaultParams.forEach(params::putIfAbsent);
                    }
                    
                    // 调用 Transformer 的 renderUI 方法（由每个 Transformer 自己负责渲染UI）
                    try {
                        modified |= transformer.renderUI(params);
                    } catch (Exception e) {
                        LOGGER.warn("Transformer.renderUI() 失败，使用默认参数渲染: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("渲染动画参数时出错: {}", e.getMessage(), e);
                ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "错误: " + e.getMessage());
            }

            UITheme.separator();

            // ===== 4. 高级修饰（透明度 / 闪白光 / 重置） =====
            UITheme.label("高级修饰 (Advanced)", UITheme.DarkTheme.TEXT_SELECTED);

            // 透明度淡入
            opacityFade.set(getBooleanParam(params, "opacityFade"));
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
                float fadeIntensity = getFloatParam(params, "opacityFadeIntensity");
                float[] fadeArr = {fadeIntensity};
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

            // 闪白光
            ImGui.sameLine();
            flashWhite.set(getBooleanParam(params, "flashWhite"));
            if (ImGui.checkbox("出场闪白光", flashWhite)) {
                params.put("flashWhite", flashWhite.get());
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("勾选后，方块在出现瞬间会闪一下白光。");
            }

            if (flashWhite.get()) {
                ImGui.sameLine();
                float flashIntensity = getFloatParam(params, "flashWhiteIntensity");
                float[] flashArr = {flashIntensity};
                ImGui.pushItemWidth(100.0f);
                if (ImGui.dragFloat("##FlashIntensity", flashArr, 0.1f, 0.5f, 3.0f, "强度: %.1f")) {
                    flashIntensity = flashArr[0];
                    params.put("flashWhiteIntensity", flashIntensity);
                    modified = true;
                }
                ImGui.popItemWidth();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("控制闪白光的亮度。");
                }
            }

            //UITheme.spacingSmall();
            if (ImGui.button("恢复当前模式默认参数")) {
                try {
                    IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(currentMode);
                    if (transformer != null) {
                        Map<String, Object> defaultParams = transformer.getDefaultParams();
                        if (defaultParams != null) {
                            params.clear();
                            params.putAll(defaultParams);
                            // 高级修饰默认关闭
                            params.put("opacityFade", false);
                            params.put("flashWhite", false);
                            params.put("opacityFadeIntensity", 1.0f);
                            params.put("flashWhiteIntensity", 1.0f);
                            opacityFade.set(false);
                            flashWhite.set(false);
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
     * @param key    参数键
     * @return 参数值或默认值
     */
    private float getFloatParam(Map<String, Object> params, String key) {
        if (params == null) return (float) 1.0;
        Object val = params.get(key);
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
                    LOGGER.warn("参数 {} 无法解析为浮点数: {}", key, s);
                    return (float) 1.0;
                }
            }
            default -> {
            }
        }

        LOGGER.warn("参数 {} 类型不匹配，期望 Number 或 String，实际: {}", key, val.getClass().getSimpleName());
        return (float) 1.0;
    }

    /**
     * 安全获取布尔参数
     *
     * @param params 参数Map
     * @param key    参数键
     * @return 参数值或默认值
     */
    private boolean getBooleanParam(Map<String, Object> params, String key) {
        if (params == null) return false;
        Object val = params.get(key);
        if (val == null) {
            return false;
        }
        return switch (val) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            default -> {
                LOGGER.warn("参数 {} 类型不匹配，期望 Boolean 或 String，实际: {}", key, val.getClass().getSimpleName());
                yield false;
            }
        };
    }


    /**
     * 渲染「序列行为」面板
     *
     * 新版 UI 采用 Flow + Fill + Origin + Weight + Modifiers 的 3 步流水线：
     *
     * 1. 切分方式 (Flow)  —— 决定整体波浪的方向/形状（按层、球形、柱形、随机）
     * 2. 起始锚点 (Origin) —— 决定从哪里开始（底部中心、几何中心、自定义）
     * 3. 内部填充 (Fill)  —— 决定局部纹理（线性扫描、径向、螺旋、噪点）
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

        // ========================= 预览按钮 =========================
        String previewLabel = showSequencePreview.get()
            ? Icons.VISIBLE + " 隐藏预览"
            : Icons.INVISIBLE + " 预览";
        if (ImGui.button(previewLabel)) {
            showSequencePreview.set(!showSequencePreview.get());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("预览方块出现顺序（需要绑定容器）");
        }

        // 预览区域
        if (showSequencePreview.get()) {
            UITheme.spacingSmall();
            renderSequencePreview(clip, params);
            UITheme.separator();
        }

        // ========================= 主内容区域 =========================
        // ---------- 1. 切分方式 (Flow) ----------
        modified |= renderSequenceFlowSection(params);

        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();

        // ---------- 2. 起始锚点 (Origin) ----------
        modified |= renderSequenceOriginSection(params);

        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();

        // ---------- 3. 内部填充 (Fill) ----------
        modified |= renderSequenceFillSection(params);

        //UITheme.spacing();
        UITheme.separator();
        //UITheme.spacing();

        // ---------- 4. 层级分离度 + 修饰 ----------
        modified |= renderSequenceWeightAndModifiers(params);

        return modified;
    }

    /**
     * 1. 切分方式 Flow（两级选择：切分方式 + 平面方向）
     *
     * NONE       - 不切分（整体）
     * PLANAR     - 平面切分（X/Y/Z/自定义角度）
     * SPHERICAL  - 球形扩散
     * CYLINDRICAL - 柱形扩散
     * RANDOM     - 完全随机
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
            //UITheme.spacingSmall();
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
            //UITheme.spacingSmall();
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
        }

        return modified;
    }

    /**
     * 2. 起始锚点 Origin
     *
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
            availableOrigins = new OriginType[]{OriginType.BOTTOM_CENTER, OriginType.CENTER};
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
     *
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
                • 径向扩散：从切片中心向两边扩散
                • 螺旋：绕中心画圈推进
                • 平滑噪声：Perlin噪声，适合自然碎块/云状效果
                • 泛洪蔓延：从起点沿相邻方块蔓延（BFS），适合火焰/藤蔓/生长感
                """);
        }

        // FLOOD 专属参数：FloodSeedMode
        if (params.getFillType() == FillType.FLOOD) {
            //UITheme.spacingSmall();
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

    /**
     * 4. 层级分离度 + 修饰
     *
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

        //UITheme.spacingSmall();

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
     * 渲染序列行为预览
     */
    private void renderSequencePreview(TimelineClip clip, SequenceParameters params) {
        //UITheme.spacingSmall();
        UITheme.separator();

        UUID containerId = clip.getBoundContainerId();
        if (containerId == null) {
            ImGui.textDisabled("⚠ 未绑定容器，无法预览");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("请先为片段绑定一个容器");
            }
            return;
        }

        Container container = Objects.requireNonNull(ContainerManager.getInstance()).getContainer(containerId);
        if (container == null) {
            ImGui.textDisabled("⚠ 容器不存在或已被删除");
            return;
        }

        try {
            // 生成序列
            List<BlockPos> blocks = new java.util.ArrayList<>(container.getBlocks());
            if (blocks.isEmpty()) {
                ImGui.textDisabled("容器为空，无方块可预览");
                return;
            }

            List<BlockSequenceEntry> entries = BlockSequenceGenerator.generate(blocks, container, params);

            // 显示预览信息
            UITheme.label("序列预览", UITheme.DarkTheme.TEXT_SELECTED);
            ImGui.text(String.format("总方块数: %d", entries.size()));

            // 按组显示
            Map<Integer, List<BlockSequenceEntry>> groups = new java.util.HashMap<>();
            for (BlockSequenceEntry entry : entries) {
                groups.computeIfAbsent(entry.getGroupId(), k -> new java.util.ArrayList<>()).add(entry);
            }

            if (groups.size() > 1) {
                ImGui.text(String.format("分组数: %d", groups.size()));
            }

            //UITheme.spacingSmall();

            // 显示前20个方块的出现顺序
            int previewCount = Math.min(20, entries.size());
            if (ImGui.collapsingHeader(String.format("前 %d 个方块的出现顺序", previewCount))) {
                for (int i = 0; i < previewCount; i++) {
                    String info = getString(entries, i, groups);
                    ImGui.text(info);
                }
                if (entries.size() > previewCount) {
                    ImGui.textDisabled(String.format("... 还有 %d 个方块", entries.size() - previewCount));
                }
            }

            // 显示分组信息
            if (groups.size() > 1 && ImGui.collapsingHeader("分组详情")) {
                for (Map.Entry<Integer, List<BlockSequenceEntry>> groupEntry : groups.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList()) {
                    int groupId = groupEntry.getKey();
                    List<BlockSequenceEntry> groupEntries = groupEntry.getValue();
                    ImGui.text(String.format("组 %d: %d 个方块", groupId, groupEntries.size()));
                }
            }
        } catch (Exception e) {
            LOGGER.error("生成序列预览时出错: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "预览生成失败: " + e.getMessage());
        }
    }

    private static @NotNull String getString(List<BlockSequenceEntry> entries, int i, Map<Integer, List<BlockSequenceEntry>> groups) {

        BlockSequenceEntry entry = entries.get(i);
        BlockPos pos = entry.getPos();
        float normalizedTime = entry.getNormalizedTime();
        int groupId = entry.getGroupId();

        String info = String.format("%d. [%d,%d,%d] - 时间: %.2f%%",

            i + 1, pos.getX(), pos.getY(), pos.getZ(), normalizedTime * 100.0f);

        if (groups.size() > 1) {

            info += String.format(" (组 %d)", groupId);

        }

        return info;

    }

    /**
     * 渲染「特效系统」面板（新版）
     * 
     * <p>结构：
     * <ul>
     *   <li>1. 预设特效包（一键应用组合特效）</li>
     *   <li>2. 已添加特效列表（带时间轴可视化）</li>
     *   <li>3. 添加新特效按钮</li>
     * </ul>
     */
    private boolean renderEffectsSection(TimelineClip clip) {
        boolean modified = false;

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            ImGui.textDisabled("动画配置为空，无法编辑特效");
            return false;
        }

        // ========== 1. 预设特效包区域 ==========
        UITheme.label("特效预设包", UITheme.DarkTheme.TEXT_SELECTED);
        ImGui.textDisabled("一键应用一组常用特效组合");
        
        // 清空特效按钮（仅在有效果时显示）
        if (!config.getEffects().isEmpty()) {
            float buttonWidth = ImGui.calcTextSize(Icons.DELETE + " 清空所有特效").x + ImGui.getStyle().getFramePadding().x * 2;
            ImGui.sameLine(ImGui.getContentRegionAvailX() - buttonWidth);
            if (ImGui.button(Icons.DELETE + " 清空所有特效")) {
                config.getEffects().clear();
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("清除当前片段的所有特效");
            }
        }
        
        //UITheme.spacingSmall();

        // 使用两列布局显示预设按钮
        float buttonWidth = (ImGui.getContentRegionAvailX() - ImGui.getStyle().getItemSpacingX()) * 0.5f;
        int buttonCount = 0;
        
        for (EffectPreset preset : EffectPresetRegistry.getPresets()) {
            if (buttonCount > 0 && buttonCount % 2 == 0) {
                ImGui.newLine();
            }
            
            ImGui.pushItemWidth(buttonWidth);
            if (ImGui.button(preset.getName() + "##preset_" + preset.getName())) {
                preset.applyTo(config);
                modified = true;
            }
            ImGui.popItemWidth();
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(preset.getDescription());
            }
            
            if (buttonCount % 2 == 0) {
                ImGui.sameLine();
            }
            
            buttonCount++;
        }
        
        // 如果按钮数量是奇数，换行对齐
        if (buttonCount % 2 == 1) {
            ImGui.newLine();
        }
        
        //UITheme.spacingSmall();
        UITheme.separator();
        //UITheme.spacingSmall();

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
                            //UITheme.spacingSmall();
                            UITheme.separator();
                            //UITheme.spacingSmall();
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
                //UITheme.spacingSmall();
            }

            // 真正删除被标记的特效
            effects.removeIf(EffectInstance::isMarkedForRemoval);
        }

        UITheme.separator();
        //UITheme.spacingSmall();

        // ========== 3. 添加新特效 ==========
        UITheme.label("添加特效", UITheme.DarkTheme.TEXT_SELECTED);

        List<EffectType> allTypes = BlockEffectRegistry.getRegisteredTypes();
        if (allTypes.isEmpty()) {
            ImGui.textDisabled("当前没有注册任何特效类型");
            return modified;
        }

        String[] effectLabels = allTypes.stream()
                .map(EffectType::getDisplayName)
                .toArray(String[]::new);

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.6f);
        ImGui.combo("可用特效", addEffectSelectedIndex, effectLabels);
        ImGui.popItemWidth();

        ImGui.sameLine();
        if (ImGui.button("添加到片段")) {
            int idx = Math.max(0, Math.min(addEffectSelectedIndex.get(), allTypes.size() - 1));
            EffectType type = allTypes.get(idx);

            EffectInstance inst = new EffectInstance(type);
            // 默认时间：入场中段触发，持续 0.3（归一化）
            inst.setStartOffset(0.2f);
            inst.setDuration(0.3f);
            inst.setEnabled(true);

            // 初始化参数为特效默认配置
            IBlockEffect impl = BlockEffectRegistry.get(type);
            if (impl != null) {
                Map<String, Object> defaultParams = impl.getDefaultParams();
                if (defaultParams != null) {
                    inst.getParams().putAll(defaultParams);
                }
            }

            config.addEffect(inst);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("从注册表中选择一个特效，并将其添加到当前片段中");
        }

        // 粘贴按钮（如果有复制的特效）
        if (EffectClipboardManager.hasCopiedEffect()) {
            ImGui.sameLine();
            if (ImGui.button(Icons.CLIPBOARD + " 粘贴特效")) {
                EffectInstance copied = EffectClipboardManager.getCopiedEffect();
                if (copied != null) {
                    config.addEffect(copied);
                    modified = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("粘贴剪贴板中的特效");
            }
        }

        return modified;
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

        float clipLength = Math.max(clip.getLength(), 0.0001f);

        // startOffset 和 duration 都是归一化的 [0..1]
        float startNorm = inst.getStartOffset();
        float durNorm = inst.getDuration();

        // 转换为实际秒数显示
        float startSec = startNorm * clipLength;
        float durSec = durNorm > 0.0f ? durNorm * clipLength : clipLength;

        float[] startBuf = { startSec };
        float[] durBuf = { durSec };

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        if (ImGui.dragFloat("开始时间 (秒)", startBuf, 0.02f, 0.0f, clipLength, "%.2f")) {
            startBuf[0] = Math.max(0f, Math.min(startBuf[0], clipLength));
            inst.setStartOffset(startBuf[0] / clipLength);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("相对于片段开始的时间偏移。");
        }

        ImGui.sameLine();

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() * 0.45f);
        if (ImGui.dragFloat("持续时长 (秒)", durBuf, 0.02f, 0.0f, clipLength, "%.2f")) {
            durBuf[0] = Math.max(0.0f, Math.min(durBuf[0], clipLength));
            inst.setDuration(durBuf[0] / clipLength);
            modified = true;
        }
        ImGui.popItemWidth();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("特效从开始到结束的持续时间。");
        }

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
                actualStartNorm * clipLength,
                actualStartNorm * 100f,
                (actualStartNorm + actualDurNorm) * clipLength,
                (actualStartNorm + actualDurNorm) * 100f
            ));
        }

        //UITheme.spacingSmall();
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
            if (value < minValue) minValue = value;
            if (value > maxValue) maxValue = value;
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
     * <p>注意：TimelineColors.rgba() 返回的格式是 ABGR（Alpha, Blue, Green, Red）
     * 位布局：A(24-31) | B(16-23) | G(8-15) | R(0-7)
     * 
     * <p>转换后的 float 数组是 RGBA 顺序，用于 ImGui.colorEdit4
     */
    private float[] colorIntToFloat(int color) {

        // ABGR 格式解析

        int r = (color) & 0xFF;           // R 在最低位 (0-7)
        int g = (color >> 8) & 0xFF;      // G 在 8-15 位
        int b = (color >> 16) & 0xFF;     // B 在 16-23 位
        int a = (color >> 24) & 0xFF;     // A 在最高位 (24-31)
        // 返回 RGBA 顺序的 float 数组（ImGui 使用）
        return new float[]{
            r / 255.0f,
            g / 255.0f,
            b / 255.0f,
            a / 255.0f
        };

    }

    /**
     * 将 float[4] 数组 (RGBA, 0.0-1.0) 转换为 int 颜色值
     * <p>注意：TimelineColors.rgba() 返回的格式是 ABGR（Alpha, Blue, Green, Red）
     * 位布局：A(24-31) | B(16-23) | G(8-15) | R(0-7)
     * <p>输入的 float 数组是 RGBA 顺序（来自 ImGui.colorEdit4）
     */
    private int colorFloatToInt(float[] rgba) {

        int r = (int)(rgba[0] * 255.0f) & 0xFF;
        int g = (int)(rgba[1] * 255.0f) & 0xFF;
        int b = (int)(rgba[2] * 255.0f) & 0xFF;
        int a = (int)(rgba[3] * 255.0f) & 0xFF;
        // 转换为 ABGR 格式：A(24-31) | B(16-23) | G(8-15) | R(0-7)
        return (a << 24) | (b << 16) | (g << 8) | r;

    }
    
    /**
     * 渲染特效参数 UI（重构后：委托给具体实现类）
     * 
     * @param instance 特效实例
     * @param type 特效类型
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
}
