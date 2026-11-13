package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.Section;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.ActorRouteClip;
import imgui.ImGui;
import imgui.type.ImInt;
import org.joml.Vector3f;

public final class ActorRouteClipPropertyAdapter implements IPropertyAdapter<ActorRouteClip> {
    private com.director.ui.actor.path.ActorRoutePropertyEditSession session;
    private int lastFrame = -1;
    private String error = "";
    private String durationError = "";
    private ActorRouteClip durationErrorClip;
    private String activeWidget;
    private boolean activeWidgetRendered, released;
    private boolean cancelledUntilRelease;
    private void observe(String widget) {
        if (widget.equals(activeWidget)) {
            activeWidgetRendered = true;
            released |= ImGui.isItemDeactivated();
        }
        if (ImGui.isItemActive()) { activeWidget = widget; activeWidgetRendered = true; }
    }
    @Override
    public Class<ActorRouteClip> getTargetType() {
        return ActorRouteClip.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.PATH + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorRouteClip source) {
        com.director.ui.properties.actor.ActorRuntimeDiagnosticsUI.render(source);
        if (source == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NO_CLIP));
            return false;
        }
        if (durationErrorClip != source) {
            durationError = "";
            durationErrorClip = source;
        }

        var routeOwner = source.getTimelineClip() == null ? null : source.getTimelineClip().getParentTrack();
        if (routeOwner != null && routeOwner.getActorTrackPayload() != null && routeOwner.getActorTrackPayload().getActorRef() != null)
            com.director.ui.properties.actor.ActorPathMovementPropertyUI.render(routeOwner.getActorTrackPayload().getActorRef().getActorId());
        var model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        var owner = source.getTimelineClip() == null ? null : source.getTimelineClip().getParentTrack();
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.movement.route_source"));
        if (model != null && com.director.ui.actor.pose.ActorMovementSource.transformOverridesRoute(owner, model.getCurrentTime()))
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.movement.route_conflict"));
        int frame = ImGui.getFrameCount();
        if (session != null && (frame != lastFrame + 1 || !session.isCurrent(model, source))) {
            session.cancel(); session = null;
            cancelledUntilRelease = true;
        }
        lastFrame = frame;
        activeWidgetRendered = false; released = false;
        if (cancelledUntilRelease) {
            if (ImGui.isMouseDown(0) || ImGui.isAnyItemActive()) {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr("gui.director.actor.route.cancelled"));
                return false;
            }
            cancelledUntilRelease = false;
        }
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
            if (session != null) session.cancel();
            session = null;
            cancelledUntilRelease = true;
            return false;
        }
        if (session == null) {
            activeWidget = null;
            try {
                session = new com.director.ui.actor.path.ActorRoutePropertyEditSession(model,
                        source.getTimelineClip() == null ? null : source.getTimelineClip().getParentTrack(), source);
            } catch (RuntimeException failure) {
                ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.unavailable"));
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.range", source.getStartTime(), source.getEndTime()));
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.route.readonly_speed", source.getSpeed()));
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.route.readonly_loop",
                        ActorPropertyAdapterI18n.routeLoopLabel(source.getLoopMode())));
                ImGui.text(ActorPropertyAdapterI18n.tr(source.isAlignRotationToRoute()
                        ? "gui.director.actor.route.align_on" : "gui.director.actor.route.align_off"));
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.route.readonly_smooth",
                        ActorPropertyAdapterI18n.tr(source.isSmoothTravel()
                                ? "gui.director.actor.state_edit.on" : "gui.director.actor.state_edit.off")));
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.route.readonly_turn", source.getEndpointTurnDuration()));
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.route.readonly_jump",
                        source.getJumpStart(), source.getJumpDuration(), source.getJumpHeight()));
                int index = 0;
                for (var point : source.getWaypoints())
                    ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.WAYPOINT_FMT, ++index) + " · " + point);
                return false;
            }
        }
        ActorRouteClip clip = session.draft();
        UITheme.title(getAdapterName());

        if (Section.header("actor_route_clip.section_basic", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC))) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            imgui.type.ImFloat duration = new imgui.type.ImFloat(clip.getLength());
            PropertyRow.field(ActorPropertyAdapterI18n.tr("gui.director.actor.route.duration"));
            if (ImGui.inputFloat("##routeDuration", duration)) {
                if (Float.isFinite(duration.get()) && duration.get() >= .1f
                        && Float.isFinite(clip.getStartTime() + duration.get())) {
                    clip.setTimeRange(clip.getStartTime(), clip.getStartTime() + duration.get());
                    durationError = "";
                } else {
                    durationError = ActorPropertyAdapterI18n.tr("gui.director.actor.route.invalid_duration");
                }
            }
            observe("duration");
            if (!durationError.isEmpty()) ImGui.textWrapped(durationError);
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.duration_hint"));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.WAYPOINT_COUNT_FMT,
                    clip.getWaypoints().size()));
        }

        if (Section.header("actor_route_clip.section_settings", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SECTION_SETTINGS))) {
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.exact_curve_hint"));
            boolean fitDuration = clip.getLoopMode() == ActorRouteClip.LoopMode.ONCE;
            ImGui.beginDisabled(!fitDuration);
            PropertyRow.label(ActorPropertyAdapterI18n.tr("gui.director.actor.route.smooth_travel"));
            if (ImGui.checkbox("##actor_route_clip_smooth_travel", clip.isSmoothTravel()))
                clip.setSmoothTravel(!clip.isSmoothTravel());
            observe("smoothTravel"); ImGui.endDisabled();
            if (fitDuration) {
                float effectiveSpeed = com.director.timeline.playback.entity.ActorRouteSampler.computeCurveLength(
                        clip.getWaypoints(), clip.getInterpolationMode()) / Math.max(.0001f, clip.getLength());
                ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.route.effective_speed", effectiveSpeed));
            }
            ImGui.beginDisabled(fitDuration);
            float[] speed = {clip.getSpeed()};
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SPEED));
            if (ImGui.sliderFloat("##routeSpeed",
                    speed, 0.05f, 20.0f, "%.2f blocks/s")) {
                clip.setSpeed(speed[0]);
            }

            observe("speed"); ImGui.endDisabled();
            ImGui.beginDisabled(source.getSourcePathId() != null);
            ActorRouteClip.InterpolationMode[] interpolationModes = ActorRouteClip.InterpolationMode.values();
            ImInt interpolation = new ImInt(clip.getInterpolationMode().ordinal());
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.INTERPOLATION));
            if (ImGui.combo("##routeInterpolation",
                    interpolation, ActorPropertyAdapterI18n.routeInterpolationLabels())) {
                clip.setInterpolationMode(interpolationModes[Math.max(0, Math.min(interpolation.get(), interpolationModes.length - 1))]);
            }

            observe("interpolation"); ImGui.endDisabled();
            ActorRouteClip.LoopMode[] loopModes = ActorRouteClip.LoopMode.values();
            ImInt loop = new ImInt(clip.getLoopMode().ordinal());
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.LOOP_MODE));
            if (ImGui.combo("##routeLoop",
                    loop, ActorPropertyAdapterI18n.routeLoopLabels())) {
                clip.setLoopMode(loopModes[Math.max(0, Math.min(loop.get(), loopModes.length - 1))]);
            }

            observe("loop");
            if(clip.getLoopMode()==ActorRouteClip.LoopMode.PING_PONG) {
                boolean stopTurn=clip.getEndpointTurnDuration()>0;
                PropertyRow.label(ActorPropertyAdapterI18n.tr("gui.director.actor.route.stop_turn"));
                if(ImGui.checkbox("##routeStopTurn",stopTurn)) clip.setEndpointTurnDuration(stopTurn?0:.35f);
                observe("stopTurn");
                if(clip.getEndpointTurnDuration()>0) {
                    float[] seconds={clip.getEndpointTurnDuration()};
                    PropertyRow.field(ActorPropertyAdapterI18n.tr("gui.director.actor.route.turn_duration"));
                    if (ImGui.sliderFloat("##routeTurnDuration", seconds, .1f, 2f, "%.2f s")) clip.setEndpointTurnDuration(seconds[0]);
                    observe("turnDuration");
                    ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.turn_motion"));
                    ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.turn_actor"));
                    if(!clip.isAlignRotationToRoute()) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.turn_align_off"));
                }
            }
            boolean align = clip.isAlignRotationToRoute();
            PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ALIGN_ROTATION));
            if (ImGui.checkbox("##routeAlign", align)) {
                clip.setAlignRotationToRoute(!align);
            }
            observe("align");

        }

        if (Section.header("actor_route_clip.section_waypoints", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SECTION_WAYPOINTS))) {
            if (source.getSourcePathId() != null) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.linked_hint"));
            ImGui.beginDisabled(source.getSourcePathId() != null);
            for (int i = 0; i < clip.getWaypoints().size(); i++) {
                Vector3f point = clip.getWaypoints().get(i);
                float[] xyz = {point.x, point.y, point.z};
                PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.WAYPOINT_FMT, i + 1));
                float buttonWidth = ImGui.calcTextSize(Icons.DELETE).x + 2f * ImGui.getStyle().getFramePadding().x;
                float available = ImGui.getContentRegionAvailX();
                float fieldWidth = available - buttonWidth - ImGui.getStyle().getItemSpacing().x;
                boolean inline = fieldWidth >= 72f;
                ImGui.setNextItemWidth(inline ? fieldWidth : -1f);
                if (ImGui.inputFloat3("##routePoint" + i, xyz)) {
                    if (new Vector3f(xyz[0], xyz[1], xyz[2]).isFinite()) clip.setWaypoint(i, xyz[0], xyz[1], xyz[2]);
                }
                observe("point" + i);
                if (inline) ImGui.sameLine();
                ImGui.beginDisabled(clip.getWaypoints().size() <= 2);
                if (ImGui.button(Icons.DELETE + "##deleteRoutePoint" + i)) {
                    clip.removeWaypoint(i);
                    ImGui.endDisabled();
                    break;
                }
                ImGui.endDisabled();
            }
            ImGui.beginDisabled(clip.getWaypoints().size() >= 2048);
            if (ImGui.button(Icons.ADD + " "
                    + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ADD_WAYPOINT) + "##addRoutePoint")) {
                Vector3f last = clip.getWaypoints().isEmpty()
                        ? new Vector3f()
                        : clip.getWaypoints().getLast();
                clip.addWaypoint(last.x, last.y, last.z);
            }
            ImGui.endDisabled();
            ImGui.endDisabled();
        }

        if (session != null && activeWidget != null && !activeWidgetRendered) {
            session.cancel(); session = null;
        }
        if (session != null && (released || !ImGui.isAnyItemActive())) {
            try {
                if (!session.commit()) error = ActorPropertyAdapterI18n.tr("gui.director.actor.route.cancelled");
                else error = "";
            } catch (RuntimeException failure) { error = failure.getMessage(); session.cancel(); }
            session = null;
        }
        if (!error.isEmpty()) ImGui.textWrapped(error);
        // Commands mark the model dirty; do not trigger per-frame inspector persistence.
        return false;
    }
}
