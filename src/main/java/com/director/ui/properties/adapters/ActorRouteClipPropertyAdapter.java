package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.ActorRouteClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import org.joml.Vector3f;

public final class ActorRouteClipPropertyAdapter implements IPropertyAdapter<ActorRouteClip> {
    private com.director.ui.actor.path.ActorRoutePropertyEditSession session;
    private int lastFrame = -1;
    private String error = "";
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
        if (source == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NO_CLIP));
            return false;
        }

        var model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
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
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr("gui.director.actor.route.unavailable"));
                return false;
            }
        }
        ActorRouteClip clip = session.draft();
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            imgui.type.ImFloat duration = new imgui.type.ImFloat(clip.getLength());
            if (ImGui.inputFloat(ActorPropertyAdapterI18n.tr("gui.director.actor.route.duration") + "##routeDuration", duration)) {
                if (Float.isFinite(duration.get()) && duration.get() >= .1f
                        && Float.isFinite(clip.getStartTime() + duration.get())) {
                    clip.setTimeRange(clip.getStartTime(), clip.getStartTime() + duration.get());
                }
            }
            observe("duration");
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.route.duration_hint"));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.WAYPOINT_COUNT_FMT,
                    clip.getWaypoints().size()));
        }

        if (ImGui.collapsingHeader(Icons.SETTINGS + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SECTION_SETTINGS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("地面移动：路径高度自动贴合地形；陡坎、断路和空间不足会阻止前进。");
            ImGui.textWrapped("路径线表示规划方向，播放时会投影到可站立地面。");
            if (ImGui.button("重新检测地形##routeTerrain")) { source.refreshTerrain(); session.cancel(); session = null; return false; }
            if (!source.getTerrainWarning().isEmpty()) ImGui.textWrapped(source.getTerrainWarning());
            float[] jumpHeight = {clip.getJumpHeight()};
            if (ImGui.sliderFloat("跳跃高度##routeHopHeight", jumpHeight, 0f, 1.25f, "%.2f blocks")) {
                clip.setJumpHeight(jumpHeight[0]);
            }
            observe("jumpHeight");
            if (clip.getJumpHeight() > 0) {
                imgui.type.ImFloat jumpStart = new imgui.type.ImFloat(clip.getJumpStart());
                if (ImGui.inputFloat("起跳时间（片段内）##routeHopStart", jumpStart)) {
                    if (Float.isFinite(jumpStart.get())) clip.setJumpStart(jumpStart.get());
                }
                observe("jumpStart");
                float[] jumpDuration = {clip.getJumpDuration()};
                if (ImGui.sliderFloat("腾空时长##routeHopDuration", jumpDuration, 0.1f, 0.8f, "%.2f s")) {
                    clip.setJumpDuration(jumpDuration[0]);
                }
                observe("jumpDuration");
                ImGui.textWrapped("每个路径片段起跳一次；当前支持地面上的短跳，不跨越断路或高墙。");
            }

            float[] speed = {clip.getSpeed()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SPEED) + "##routeSpeed",
                    speed, 0.05f, 20.0f, "%.2f blocks/s")) {
                clip.setSpeed(speed[0]);
            }

            observe("speed");
            ActorRouteClip.InterpolationMode[] interpolationModes = ActorRouteClip.InterpolationMode.values();
            ImInt interpolation = new ImInt(clip.getInterpolationMode().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.INTERPOLATION) + "##routeInterpolation",
                    interpolation, ActorPropertyAdapterI18n.routeInterpolationLabels())) {
                clip.setInterpolationMode(interpolationModes[Math.max(0, Math.min(interpolation.get(), interpolationModes.length - 1))]);
            }

            observe("interpolation");
            ActorRouteClip.LoopMode[] loopModes = ActorRouteClip.LoopMode.values();
            ImInt loop = new ImInt(clip.getLoopMode().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.LOOP_MODE) + "##routeLoop",
                    loop, ActorPropertyAdapterI18n.routeLoopLabels())) {
                clip.setLoopMode(loopModes[Math.max(0, Math.min(loop.get(), loopModes.length - 1))]);
            }

            observe("loop");
            boolean align = clip.isAlignRotationToRoute();
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ALIGN_ROTATION) + "##routeAlign", align)) {
                clip.setAlignRotationToRoute(!align);
            }
            observe("align");
        }

        if (ImGui.collapsingHeader(Icons.PIN + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SECTION_WAYPOINTS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            for (int i = 0; i < clip.getWaypoints().size(); i++) {
                Vector3f point = clip.getWaypoints().get(i);
                float[] xyz = {point.x, point.y, point.z};
                if (ImGui.inputFloat3(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.WAYPOINT_FMT, i + 1)
                        + "##routePoint" + i, xyz)) {
                    if (new Vector3f(xyz[0], xyz[1], xyz[2]).isFinite()) clip.setWaypoint(i, xyz[0], xyz[1], xyz[2]);
                }
                observe("point" + i);
                ImGui.sameLine();
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
