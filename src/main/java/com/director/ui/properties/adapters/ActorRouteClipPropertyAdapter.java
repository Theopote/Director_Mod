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
    @Override
    public Class<ActorRouteClip> getTargetType() {
        return ActorRouteClip.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.PATH + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorRouteClip clip) {
        if (clip == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NO_CLIP));
            return false;
        }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.WAYPOINT_COUNT_FMT,
                    clip.getWaypoints().size()));
        }

        if (ImGui.collapsingHeader(Icons.SETTINGS + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SECTION_SETTINGS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("地面移动：路径高度自动贴合地形；陡坎、断路和空间不足会阻止前进。");
            ImGui.textWrapped("路径线表示规划方向，播放时会投影到可站立地面。");
            if (ImGui.button("重新检测地形##routeTerrain")) clip.refreshTerrain();
            if (!clip.getTerrainWarning().isEmpty()) ImGui.textWrapped(clip.getTerrainWarning());
            float[] jumpHeight = {clip.getJumpHeight()};
            if (ImGui.sliderFloat("跳跃高度##routeHopHeight", jumpHeight, 0f, 1.25f, "%.2f blocks")) {
                clip.setJumpHeight(jumpHeight[0]);
                changed = true;
            }
            if (clip.getJumpHeight() > 0) {
                imgui.type.ImFloat jumpStart = new imgui.type.ImFloat(clip.getJumpStart());
                if (ImGui.inputFloat("起跳时间（片段内）##routeHopStart", jumpStart)) {
                    clip.setJumpStart(jumpStart.get());
                    changed = true;
                }
                float[] jumpDuration = {clip.getJumpDuration()};
                if (ImGui.sliderFloat("腾空时长##routeHopDuration", jumpDuration, 0.1f, 0.8f, "%.2f s")) {
                    clip.setJumpDuration(jumpDuration[0]);
                    changed = true;
                }
                ImGui.textWrapped("每个路径片段起跳一次；当前支持地面上的短跳，不跨越断路或高墙。");
            }

            float[] speed = {clip.getSpeed()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SPEED) + "##routeSpeed",
                    speed, 0.05f, 20.0f, "%.2f blocks/s")) {
                clip.setSpeed(speed[0]);
                changed = true;
            }

            ActorRouteClip.InterpolationMode[] interpolationModes = ActorRouteClip.InterpolationMode.values();
            ImInt interpolation = new ImInt(clip.getInterpolationMode().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.INTERPOLATION) + "##routeInterpolation",
                    interpolation, ActorPropertyAdapterI18n.routeInterpolationLabels())) {
                clip.setInterpolationMode(interpolationModes[Math.max(0, Math.min(interpolation.get(), interpolationModes.length - 1))]);
                changed = true;
            }

            ActorRouteClip.LoopMode[] loopModes = ActorRouteClip.LoopMode.values();
            ImInt loop = new ImInt(clip.getLoopMode().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.LOOP_MODE) + "##routeLoop",
                    loop, ActorPropertyAdapterI18n.routeLoopLabels())) {
                clip.setLoopMode(loopModes[Math.max(0, Math.min(loop.get(), loopModes.length - 1))]);
                changed = true;
            }

            boolean align = clip.isAlignRotationToRoute();
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ALIGN_ROTATION) + "##routeAlign", align)) {
                clip.setAlignRotationToRoute(!align);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(Icons.PIN + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.SECTION_WAYPOINTS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            for (int i = 0; i < clip.getWaypoints().size(); i++) {
                Vector3f point = clip.getWaypoints().get(i);
                float[] xyz = {point.x, point.y, point.z};
                if (ImGui.inputFloat3(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.WAYPOINT_FMT, i + 1)
                        + "##routePoint" + i, xyz)) {
                    clip.setWaypoint(i, xyz[0], xyz[1], xyz[2]);
                    changed = true;
                }
                ImGui.sameLine();
                if (ImGui.button(Icons.DELETE + "##deleteRoutePoint" + i)) {
                    clip.removeWaypoint(i);
                    changed = true;
                    break;
                }
            }
            if (ImGui.button(Icons.ADD + " "
                    + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Route.ADD_WAYPOINT) + "##addRoutePoint")) {
                Vector3f last = clip.getWaypoints().isEmpty()
                        ? new Vector3f()
                        : clip.getWaypoints().getLast();
                clip.addWaypoint(last.x, last.y, last.z);
                changed = true;
            }
        }

        return changed;
    }
}
