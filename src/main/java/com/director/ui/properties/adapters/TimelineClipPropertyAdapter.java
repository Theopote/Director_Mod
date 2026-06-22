package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.ui.properties.IPropertyAdapter;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.theme.Icons;
import com.director.timeline.model.TrackType;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * TimelineClip 属性面板适配器（容器片段 / 出现动画）
 */
public class TimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipPropertyAdapter.class);

    private final TimelineClipPropertyUi propertyUi = new TimelineClipPropertyUi();

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
            UUID containerId = clip.getBoundContainerId();
            if (containerId == null) {
                return false;
            }
            Container container = DirectorServices.get().containers().getContainer(containerId);
            if (container == null) {
                return false;
            }
            return container.getType() != com.director.core.container.ContainerType.DEMOLITION;
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

    private boolean renderPropertiesInternal(TimelineClip clip) {
        boolean modified = false;

        final float oldStartTime = clip.getStartTime();
        final float oldEndTime = clip.getEndTime();

        if (ImGui.collapsingHeader(Icons.SETTINGS + " 基础设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderBasicProperties(clip, TimelineClipUiVariant.CONTAINER);
            ImGui.unindent();
        }

        boolean sequenceModified = false;
        boolean animationModified = false;

        if (ImGui.collapsingHeader(Icons.LAYERS + " 序列行为", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            sequenceModified = propertyUi.renderSequenceBehavior(clip, TimelineClipUiVariant.CONTAINER);
            modified |= sequenceModified;
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动画设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            animationModified = propertyUi.renderAnimationSettings(clip, TimelineClipUiVariant.CONTAINER);
            modified |= animationModified;
            ImGui.unindent();
        }

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

        try {
            TimelineTrack parent = clip.getParentTrack();
            TrackType type = parent != null ? parent.getType() : null;
            boolean isBlockClip = type == TrackType.CONTAINER;
            if (isBlockClip && (sequenceModified || animationModified)) {
                if (!ImGui.isAnyItemActive()) {
                    com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                            .refreshAutoSoundEventsIfNeeded(clip);
                }
            }
        } catch (Exception ignored) {
        }

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

                    var timelineModel = DirectorServices.get().containerTimeline()
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
}
