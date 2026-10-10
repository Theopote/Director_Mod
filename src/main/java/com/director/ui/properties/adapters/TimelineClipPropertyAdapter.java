package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.container.Container;
import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
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
        return TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.ADAPTER_NAME);
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
            ImGui.textDisabled(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.NO_SELECTION));
            return false;
        }

        try {
            return renderPropertiesInternal(clip);
        } catch (Exception e) {
            LOGGER.error("渲染片段属性时发生异常: {}", e.getMessage(), e);
            ImGui.textColored(1.0f, 0.0f, 0.0f, 1.0f,
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.RENDER_ERROR, e.getMessage()));
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.VIEW_DETAILS))) {
                LOGGER.error("详细堆栈:", e);
            }
            return false;
        }
    }

    private boolean renderPropertiesInternal(TimelineClip clip) {
        boolean modified = false;

        final float oldStartTime = clip.getStartTime();
        final float oldEndTime = clip.getEndTime();

        if (ImGui.collapsingHeader(Icons.SETTINGS + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderBasicProperties(clip, TimelineClipUiVariant.CONTAINER);
            ImGui.unindent();
        }

        boolean sequenceModified = false;
        boolean animationModified = false;

        if (!clip.getAnimationConfig().isWholeContainer() && ImGui.collapsingHeader(Icons.LAYERS + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.SECTION_SEQUENCE),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            sequenceModified = propertyUi.renderSequenceBehavior(clip, TimelineClipUiVariant.CONTAINER);
            modified |= sequenceModified;
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.ANIMATION + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.SECTION_ANIMATION),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            animationModified = propertyUi.renderAnimationSettings(clip, TimelineClipUiVariant.CONTAINER);
            modified |= animationModified;
            ImGui.unindent();
        }

        List<EffectInstance> effects = clip.getAnimationConfig() != null ? clip.getAnimationConfig().getEffects()
                : java.util.Collections.emptyList();
        String effectsHeader = Icons.PARTICLE + " "
                + (clip.getAnimationConfig().isWholeContainer() || effects.isEmpty()
                ? TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.EFFECTS_HEADER)
                : TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineClip.EFFECTS_HEADER_COUNT, effects.size()));
        if (ImGui.collapsingHeader(effectsHeader, ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            modified |= propertyUi.renderEffectsSection(clip);
            ImGui.unindent();
        }

        TimelineTrack parent = clip.getParentTrack();
        TrackType type = parent != null ? parent.getType() : null;
        boolean isBlockClip = type == TrackType.CONTAINER;
        if (isBlockClip && (sequenceModified || animationModified)) {
            if (!ImGui.isAnyItemActive()) {
                com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                        .refreshAutoSoundEventsIfNeeded(clip);
            }
        }

        parent = clip.getParentTrack();
        type = parent != null ? parent.getType() : null;
        isBlockClip = type == TrackType.CONTAINER;
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

        propertyUi.finishAnimationEditIfIdle(clip);

        return modified;
    }
}
