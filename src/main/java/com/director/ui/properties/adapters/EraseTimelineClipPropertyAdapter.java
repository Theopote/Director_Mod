package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.ui.properties.IPropertyAdapter;
import com.director.timeline.model.TimelineClip;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 拆除片段属性面板适配器（DEMOLITION 容器 / 消失动画）
 */
public class EraseTimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EraseTimelineClipPropertyAdapter.class);

    private final TimelineClipPropertyUi propertyUi = new TimelineClipPropertyUi();

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
        return 100;
    }

    @Override
    public boolean supports(Object target) {
        if (!(target instanceof TimelineClip clip)) {
            return false;
        }
        try {
            UUID containerId = clip.getBoundContainerId();
            if (containerId == null) {
                return false;
            }
            Container container = DirectorServices.get().containers().getContainer(containerId);
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

    private boolean renderPropertiesInternal(TimelineClip clip) {
        boolean modified = false;

        final float oldStartTime = clip.getStartTime();
        final float oldEndTime = clip.getEndTime();

        if (ImGui.collapsingHeader(Icons.SETTINGS + " 基础设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderBasicProperties(clip, TimelineClipUiVariant.DEMOLITION);
            ImGui.unindent();
        }

        boolean sequenceModified = false;
        boolean animationModified = false;

        if (ImGui.collapsingHeader(Icons.LAYERS + " 序列行为", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            sequenceModified = propertyUi.renderSequenceBehavior(clip, TimelineClipUiVariant.DEMOLITION);
            modified |= sequenceModified;
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            animationModified = propertyUi.renderAnimationSettings(clip, TimelineClipUiVariant.DEMOLITION);
            modified |= animationModified;
            ImGui.unindent();
        }

        List<EffectInstance> effects = clip.getAnimationConfig() != null
                ? clip.getAnimationConfig().getEffects() : Collections.emptyList();
        String effectsHeader = Icons.PARTICLE + " 特效 (Effects)";
        if (!effects.isEmpty()) {
            effectsHeader += " (" + effects.size() + ")";
        }
        if (ImGui.collapsingHeader(effectsHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderEffectsSection(clip);
            ImGui.unindent();
        }

        try {
            if (sequenceModified || animationModified) {
                if (!ImGui.isAnyItemActive()) {
                    com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                            .refreshAutoSoundEventsIfNeeded(clip);
                }
            }
        } catch (Exception ignored) {
        }

        try {
            float newStart = clip.getStartTime();
            float newEnd = clip.getEndTime();
            boolean timeChanged = Math.abs(newStart - oldStartTime) > 0.0005f
                    || Math.abs(newEnd - oldEndTime) > 0.0005f;
            if (timeChanged) {
                com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                        .onClipTimeRangeChanged(clip, oldStartTime, oldEndTime, newStart, newEnd);

                var timelineModel = DirectorServices.get().containerTimeline()
                        .getTimelineModel();
                if (timelineModel != null) {
                    timelineModel.markDirty();
                }
            }
        } catch (Exception ignored) {
        }

        return modified;
    }
}
