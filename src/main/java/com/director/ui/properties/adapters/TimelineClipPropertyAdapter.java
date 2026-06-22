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
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.theme.Icons;
import com.director.ui.timeline.model.TrackType;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimelineClip 属性面板适配器（最终清洗版）
 *
 * <p>
 * 基于ImGui的属性编辑面板。
 * 修复了旧的字符串硬编码问题，统一了枚举处理逻辑。
 *
 * @author Director Team
 * @version 3.1 - Enum Refactored
 */

public class TimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipPropertyAdapter.class);

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
                return container.getType() != com.director.core.container.ContainerType.DEMOLITION;
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
            modified |= propertyUi.renderBasicProperties(clip, TimelineClipUiVariant.CONTAINER);
            ImGui.unindent();
        }

        boolean sequenceModified = false;
        boolean animationModified = false;

        // 2. 序列行为组 - 默认展开
        if (ImGui.collapsingHeader(Icons.LAYERS + " 序列行为", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            sequenceModified = propertyUi.renderSequenceBehavior(clip, TimelineClipUiVariant.CONTAINER);
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
            modified |= propertyUi.renderEffectsSection(clip);
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
                    com.director.ui.timeline.audio.TimelineAudioService.getInstance()
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
                    com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                            .onClipTimeRangeChanged(clip, oldStartTime, oldEndTime, newStart, newEnd);

                    // 标记时间线变更：更新总时长 + 自动保存（与拖动/命令路径一致）
                    var timelineModel = com.director.ui.timeline.container.ContainerTimelineService.getInstance()
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
        com.director.core.animation.LegacyAnimationModeMigrator.migrateIfNeeded(config);
        // ✅ 旧高级参数迁移：例如 flashWhite -> EffectInstance（防止开关“看起来有用但实际不生效”）
        com.director.core.animation.LegacyAdvancedParamsMigrator.migrateIfNeeded(config);

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
        com.director.core.animation.ClipAnimationConfig.AnimationPhase desiredPhase = com.director.core.animation.ClipAnimationConfig.AnimationPhase.ENTER;
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
            propertyUi.drawHelpBox(currentMode.getDescription());
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
                    boolean paramsModified = propertyUi.renderAnimationParamsDirectly(currentMode, params);
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

        // UITheme.spacingSmall();
        // 可视化曲线预览（你已经实现了）
        propertyUi.renderEasingCurvePreview(currentEasingFunc);

        UITheme.separator();

        // ===== 4. 高级修饰（透明度 / 闪白光 / 重置） =====
        UITheme.label("高级修饰 (Advanced)", UITheme.DarkTheme.TEXT_SELECTED);

        // 透明度淡入
        opacityFade.set(propertyUi.getBooleanParam(params));
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
            float fadeIntensity = propertyUi.getFloatParam(params);
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
}
