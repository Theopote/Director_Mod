package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.selection.SelectionManager;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorTrack;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;

import java.util.concurrent.atomic.AtomicBoolean;

public class ActorActionClipPropertyAdapter implements IPropertyAdapter<ActorActionClip> {

    private final ImFloat intensityFloat = new ImFloat();
    private final ImBoolean loopBoolean = new ImBoolean();
    private final ImFloat startTimeFloat = new ImFloat();
    private final ImFloat endTimeFloat = new ImFloat();

    @Override
    public Class<ActorActionClip> getTargetType() {
        return ActorActionClip.class;
    }

    @Override
    public boolean renderProperties(ActorActionClip clip) {
        if (clip == null) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NO_CLIP));
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);

        UITheme.title(Icons.SMILE + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.TITLE));

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.TIME_RANGE));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
            UITheme.endGroup();
        }

        if (ImGui.collapsingHeader(Icons.SMILE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_SEMANTICS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            renderActionSelector(clip, changed);
        }

        renderRigWarningIfNeeded(clip);

        if (ImGui.collapsingHeader(Icons.SCALE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_INTENSITY),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            intensityFloat.set(clip.getIntensity());
            float[] intensityArray = {intensityFloat.get()};

            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY),
                    intensityArray, 0.0f, 1.0f, "%.2f")) {
                clip.setIntensity(intensityArray[0]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.INTENSITY_TOOLTIP));
            }

            ImGui.sameLine();
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY_STYLE_HINT));
        }

        if (ImGui.collapsingHeader(Icons.LOOP + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_LOOP))) {
            loopBoolean.set(clip.isLoop());
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.LOOP), loopBoolean)) {
                clip.setLoop(loopBoolean.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.LOOP_TOOLTIP));
            }
        }

        if (ImGui.collapsingHeader(Icons.CLOCK + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.SECTION_TIME_EDIT))) {
            float start = clip.getStartTime();
            float end = clip.getEndTime();
            float duration = end - start;

            startTimeFloat.set(start);
            endTimeFloat.set(end);

            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.TIME_RANGE));
            if (ImGui.inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_TIME), startTimeFloat)) {
                if (startTimeFloat.get() < end) {
                    clip.setTimeRange(startTimeFloat.get(), end);
                    changed.set(true);
                }
            }

            if (ImGui.inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_TIME), endTimeFloat)) {
                if (endTimeFloat.get() > start) {
                    clip.setTimeRange(start, endTimeFloat.get());
                    changed.set(true);
                }
            }

            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, duration));
            UITheme.endGroup();
        }

        return changed.get();
    }

    private Actor resolveActorForClip() {
        SelectionManager sel = DirectorServices.get().uiSelection();
        TimelineTrack track = sel.getSelectedTrack();

        if (track == null || track.getType() != TrackType.ACTOR) {
            return null;
        }

        ActorTrack actorTrack = track.getActorTrackPayload();
        if (actorTrack == null) {
            return null;
        }

        var actorRef = actorTrack.getActorRef();
        if (actorRef == null) {
            return null;
        }

        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();
        return actorRegistry.getActor(actorRef.getActorId()).orElse(null);
    }

    private void renderActionSelector(ActorActionClip clip, AtomicBoolean changed) {
        ActorActionType current = clip.getActionType();
        Actor actor = resolveActorForClip();
        ActorRigProfile rig = actor != null ? actor.getRigProfile() : null;

        String currentLabel = current.name();
        if (rig != null && !rig.supports(current)) {
            currentLabel = current.name() + " ⚠";
        }

        if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.ACTION_TYPE), currentLabel)) {
            for (ActorActionType type : ActorActionType.values()) {
                boolean supported = rig == null || rig.supports(type);
                boolean selected = (type == current);

                String label = type.name();
                if (!supported) {
                    label = label + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.NOT_SUPPORTED_SUFFIX);
                }

                if (!supported) {
                    ImGui.beginDisabled();
                }

                if (ImGui.selectable(label, selected)) {
                    if (supported) {
                        clip.setActionType(type);
                        changed.set(true);
                    }
                }

                if (!supported && ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.NOT_SUPPORTED_TOOLTIP));
                    ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.NOT_SUPPORTED_RIG_FMT,
                            actor.getName(), rig.getClass().getSimpleName()));
                    ImGui.endTooltip();
                }

                if (selected) {
                    ImGui.setItemDefaultFocus();
                }

                if (!supported) {
                    ImGui.endDisabled();
                }
            }

            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.ACTION_TYPE_TOOLTIP));
        }
    }

    private void renderRigWarningIfNeeded(ActorActionClip clip) {
        Actor actor = resolveActorForClip();
        if (actor == null) {
            return;
        }

        ActorRigProfile rig = actor.getRigProfile();
        if (rig == null) {
            return;
        }

        ActorActionType currentAction = clip.getActionType();
        if (!rig.supports(currentAction)) {
            ImGui.spacing();
            ImGui.textColored(1.0f, 0.6f, 0.2f, 1.0f,
                    ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.RIG_WARNING));
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.RIG_WARNING_FMT,
                    actor.getName(), currentAction.name(), rig.getClass().getSimpleName()));
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Action.RIG_WARNING_HINT));
        }
    }
}
