package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.easing.EasingFunction;
import com.director.core.animation.transform.AnimationTransformerFactory;
import com.director.core.animation.transform.IAnimationTransformer;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.container.AnimationMode;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.theme.Icons;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import java.util.UUID;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 拆除片段属性面板适配器
 *
 * <p>专门用于拆除容器（ContainerType.DEMOLITION）的 TimelineClip，处理拆除片段的属性编辑。
 * 拆除片段与容器片段在很多方面是相反的（方块消失 vs 出现），因此需要独立的适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EraseTimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EraseTimelineClipPropertyAdapter.class);

    private final TimelineClipPropertyUi propertyUi = new TimelineClipPropertyUi();

    private final ImInt animationModeIndex = new ImInt();
    private final ImInt easingFunctionIndex = new ImInt();
    private final ImBoolean opacityFade = new ImBoolean(false);

    @Override
    public Class<TimelineClip> getTargetType() {
        return TimelineClip.class;
    }

    @Override
    public String getAdapterName() {
        return "拆除片段属性";
    }

    @Override
    public int getPriority() {
        return 100; // 高优先级，确保优先匹配拆除片段
    }

    @Override
    public boolean supports(Object target) {
        if (!(target instanceof TimelineClip clip)) {
            return false;
        }
        try {
            // ✅ 基于容器类型判断是否为拆除容器（不再依赖轨道类型）
            UUID containerId = clip.getBoundContainerId();
            if (containerId == null) {
                return false;
            }
            Container container = ContainerManager.getInstance() != null
                    ? ContainerManager.getInstance().getContainer(containerId)
                    : null;
            return container != null && container.getType() == com.director.core.container.ContainerType.DEMOLITION;
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
            LOGGER.error("渲染拆除片段属性时发生异常: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "渲染错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 渲染属性（内部实现）
     */
    private boolean renderPropertiesInternal(TimelineClip clip) {
        boolean modified = false;

        final float oldStartTime = clip.getStartTime();
        final float oldEndTime = clip.getEndTime();

        // 1. 基础设置组
        if (ImGui.collapsingHeader(Icons.SETTINGS + " 基础设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderBasicProperties(clip, TimelineClipUiVariant.DEMOLITION);
            ImGui.unindent();
        }

        boolean sequenceModified = false;
        boolean animationModified = false;

        // 2. 序列行为组
        if (ImGui.collapsingHeader(Icons.LAYERS + " 序列行为", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            sequenceModified = propertyUi.renderSequenceBehavior(clip, TimelineClipUiVariant.DEMOLITION);
            modified |= sequenceModified;
            ImGui.unindent();
        }

        // 3. 动画设置组
        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            animationModified = renderAnimationSettings(clip);
            modified |= animationModified;
            ImGui.unindent();
        }

        // 4. 特效组
        List<EffectInstance> effects = clip.getAnimationConfig() != null ? 
                clip.getAnimationConfig().getEffects() : Collections.emptyList();
        String effectsHeader = Icons.PARTICLE + " 特效 (Effects)";
        if (!effects.isEmpty()) {
            effectsHeader += " (" + effects.size() + ")";
        }
        if (ImGui.collapsingHeader(effectsHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderEffectsSection(clip);
            ImGui.unindent();
        }

        // 动画配置联动
        try {
            if (sequenceModified || animationModified) {
                if (!ImGui.isAnyItemActive()) {
                    com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                            .refreshAutoSoundEventsIfNeeded(clip);
                }
            }
        } catch (Exception ignored) {
        }

        // 时间范围联动
        try {
            float newStart = clip.getStartTime();
            float newEnd = clip.getEndTime();
            boolean timeChanged = Math.abs(newStart - oldStartTime) > 0.0005f
                    || Math.abs(newEnd - oldEndTime) > 0.0005f;
            if (timeChanged) {
                com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                        .onClipTimeRangeChanged(clip, oldStartTime, oldEndTime, newStart, newEnd);

                var timelineModel = com.director.ui.timeline.container.ContainerTimelineService.getInstance()
                        .getTimelineModel();
                if (timelineModel != null) {
                    timelineModel.markDirty();
                }
            }
        } catch (Exception ignored) {
        }

        return modified;
    }

    /**
     * 渲染动画设置（拆除片段专用：强制 EXIT 阶段）
     */
    private boolean renderAnimationSettings(TimelineClip clip) {
        try {
            return renderAnimationSettingsInternal(clip);
        } catch (Exception e) {
            LOGGER.error("渲染动画设置时发生异常: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f, "错误: " + e.getMessage());
            return false;
        }
    }

    private boolean renderAnimationSettingsInternal(TimelineClip clip) {
        boolean modified = false;

        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            LOGGER.warn("片段的动画配置为空");
            ImGui.textDisabled("动画配置为空");
            return false;
        }

        // 旧模式迁移
        com.director.core.animation.LegacyAnimationModeMigrator.migrateIfNeeded(config);
        com.director.core.animation.LegacyAdvancedParamsMigrator.migrateIfNeeded(config);

        // 防御式初始化
        AnimationMode currentMode = config.getMode();
        if (currentMode == null) {
            currentMode = AnimationMode.INSTANT;
            config.setMode(currentMode);
        }

        String currentEasing = config.getEasing();
        if (currentEasing == null || currentEasing.isEmpty()) {
            currentEasing = EasingFunction.LINEAR.name();
            config.setEasing(currentEasing);
        }

        // ===== 1. 总体节奏：阶段(EXIT) + 模式 + 时长 =====
        String sectionTitle = "清除动画模式与节奏";
        UITheme.label(sectionTitle, UITheme.DarkTheme.TEXT_SELECTED);
        
        UITheme.label("拆除容器片段：方块会先播放退出动画，然后在动画结束后被清除", UITheme.DarkTheme.TEXT_DISABLED);
        UITheme.spacingSmall();

        // 片段阶段：强制 EXIT（拆除片段）
        com.director.core.animation.ClipAnimationConfig.AnimationPhase desiredPhase = 
                com.director.core.animation.ClipAnimationConfig.AnimationPhase.EXIT;
        if (config.getPhase() != desiredPhase) {
            config.setPhase(desiredPhase);
            modified = true;
        }

        // 模式选择（仅 EXIT 模式）
        AnimationMode[] availableModes = new AnimationMode[]{
            AnimationMode.INSTANT, AnimationMode.FADE_OUT, AnimationMode.EXPLODE, 
            AnimationMode.IMPLODE, AnimationMode.SHRINK_OUT, AnimationMode.GRAVITY_FALL, AnimationMode.FLY_OUT
        };

        String[] availableLabels = Arrays.stream(availableModes)
                .map(m -> {
                    if (m == AnimationMode.ASSEMBLE) {
                        return "碎片散射";
                    }
                    return m.getDisplayName();
                })
                .toArray(String[]::new);
        
        int selectedIdx = 0;
        for (int i = 0; i < availableModes.length; i++) {
            if (availableModes[i] == currentMode) {
                selectedIdx = i;
                break;
            }
        }
        
        // 当前 mode 不在可选列表：自动纠正
        if (selectedIdx == 0 && currentMode != availableModes[0]) {
            AnimationMode fallback = AnimationMode.FADE_OUT;
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
                    String recommended = transformer.getRecommendedEasing();
                    if (recommended != null && !recommended.isEmpty()) {
                        config.setEasing(recommended);
                        currentEasing = recommended;
                    }
                    
                    Map<String, Object> params = config.getParams();
                    Map<String, Object> defaultParams = transformer.getDefaultParams();
                    if (defaultParams != null) {
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
                                    // ignore
                                }
                            }
                        }
                        
                        params.clear();
                        params.putAll(defaultParams);
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
            ImGui.setTooltip("选择擦除片段的退出方式（方块先播放退出动画，再被清除）");
        }

        // 模式描述
        try {
            if (currentMode == AnimationMode.ASSEMBLE) {
                propertyUi.drawHelpBox("方块向外散射并淡出（由'碎片聚合'自动反向播放得到），适合擦除片段的碎片效果");
            } else {
                propertyUi.drawHelpBox(currentMode.getDescription());
            }
        } catch (Exception e) {
            LOGGER.error("显示模式描述时出错: {}", e.getMessage(), e);
        }

        // 动画时长
        float[] duration = {config.getDuration()};
        if (ImGui.dragFloat("动画时长 (秒)", duration, 0.05f, 0.0f, clip.getLength(), "%.2fs")) {
            if (duration[0] < 0.0f) duration[0] = 0.0f;
            config.setDuration(duration[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制单个方块从开始运动到完全消失所用的时间。\n建议不超过片段总时长。");
        }

        // 动画模式特定参数
        UITheme.spacingSmall();
        Map<String, Object> params = config.getParams();

        try {
            IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(currentMode);
            if (transformer != null) {
                Map<String, Object> defaultParams = transformer.getDefaultParams();
                if (defaultParams != null) {
                    defaultParams.forEach(params::putIfAbsent);
                }
                
                try {
                    boolean paramsModified = propertyUi.renderAnimationParamsDirectly(currentMode, params);
                    if (paramsModified) {
                        modified = true;
                    }
                } catch (Exception e) {
                    LOGGER.warn("渲染动画参数时出错: {}", e.getMessage());
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

        // ===== 2. 缓动曲线 =====
        UITheme.label("缓动曲线 (Easing)", UITheme.DarkTheme.TEXT_SELECTED);

        EasingFunction currentEasingFunc;
        try {
            currentEasingFunc = EasingFunction.fromString(currentEasing);
        } catch (Exception e) {
            currentEasingFunc = EasingFunction.LINEAR;
        }

        int easingOrdinal = currentEasingFunc.ordinal();
        String[] easingLabels = TimelineClipPropertyUi.getEasingFunctionLabels();
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

        propertyUi.renderEasingCurvePreview(currentEasingFunc);

        UITheme.separator();

        // ===== 3. 高级修饰 =====
        UITheme.label("高级修饰 (Advanced)", UITheme.DarkTheme.TEXT_SELECTED);

        opacityFade.set(propertyUi.getBooleanParam(params));
        if (ImGui.checkbox("透明度淡入", opacityFade)) {
            params.put("opacityFade", opacityFade.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("勾选后，动画过程中会从完全透明逐渐变为不透明。");
        }

        if (opacityFade.get()) {
            ImGui.sameLine();
            float fadeIntensity = propertyUi.getFloatParam(params);
            float[] fadeArr = {fadeIntensity};
            ImGui.pushItemWidth(100.0f);
            if (ImGui.dragFloat("##FadeIntensity", fadeArr, 0.05f, 0.1f, 2.0f, "强度: %.2f")) {
                fadeIntensity = fadeArr[0];
                params.put("opacityFadeIntensity", fadeIntensity);
                modified = true;
            }
            ImGui.popItemWidth();
        }

        ImGui.textDisabled("提示：闪白光请在下方「特效」面板添加 FLASH_WHITE（或使用特效预设包）。");

        if (ImGui.button("恢复当前模式默认参数")) {
            try {
                IAnimationTransformer transformer = AnimationTransformerFactory.getInstance().getTransformer(currentMode);
                if (transformer != null) {
                    Map<String, Object> defaultParams = transformer.getDefaultParams();
                    if (defaultParams != null) {
                        params.clear();
                        params.putAll(defaultParams);
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

        return modified;
    }
}
