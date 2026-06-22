package com.director.ui.properties.adapters;

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
        return Icons.PATH + " 演员路线片段";
    }

    @Override
    public boolean renderProperties(ActorRouteClip clip) {
        if (clip == null) {
            ImGui.textDisabled("无 RouteClip");
            return false;
        }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.TIME + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text(String.format("开始: %.2fs", clip.getStartTime()));
            ImGui.text(String.format("结束: %.2fs", clip.getEndTime()));
            ImGui.text(String.format("时长: %.2fs", clip.getLength()));
            ImGui.text(String.format("路点: %d", clip.getWaypoints().size()));
        }

        if (ImGui.collapsingHeader(Icons.SETTINGS + " 路线设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            float[] speed = { clip.getSpeed() };
            if (ImGui.sliderFloat("速度##routeSpeed", speed, 0.05f, 20.0f, "%.2f blocks/s")) {
                clip.setSpeed(speed[0]);
                changed = true;
            }

            ActorRouteClip.InterpolationMode[] interpolationModes = ActorRouteClip.InterpolationMode.values();
            int interpolationIdx = clip.getInterpolationMode().ordinal();
            ImInt interpolation = new ImInt(interpolationIdx);
            String[] interpolationLabels = new String[interpolationModes.length];
            for (int i = 0; i < interpolationModes.length; i++) {
                interpolationLabels[i] = interpolationModes[i].name();
            }
            if (ImGui.combo("插值##routeInterpolation", interpolation, interpolationLabels)) {
                clip.setInterpolationMode(interpolationModes[Math.max(0, Math.min(interpolation.get(), interpolationModes.length - 1))]);
                changed = true;
            }

            ActorRouteClip.LoopMode[] loopModes = ActorRouteClip.LoopMode.values();
            int loopIdx = clip.getLoopMode().ordinal();
            ImInt loop = new ImInt(loopIdx);
            String[] loopLabels = new String[loopModes.length];
            for (int i = 0; i < loopModes.length; i++) {
                loopLabels[i] = loopModes[i].name();
            }
            if (ImGui.combo("循环##routeLoop", loop, loopLabels)) {
                clip.setLoopMode(loopModes[Math.max(0, Math.min(loop.get(), loopModes.length - 1))]);
                changed = true;
            }

            boolean align = clip.isAlignRotationToRoute();
            if (ImGui.checkbox("沿路线调整朝向##routeAlign", align)) {
                clip.setAlignRotationToRoute(!align);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(Icons.PIN + " 路点", ImGuiTreeNodeFlags.DefaultOpen)) {
            for (int i = 0; i < clip.getWaypoints().size(); i++) {
                Vector3f point = clip.getWaypoints().get(i);
                float[] xyz = { point.x, point.y, point.z };
                if (ImGui.inputFloat3("路点 " + (i + 1) + "##routePoint" + i, xyz)) {
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
            if (ImGui.button(Icons.ADD + " 添加路点##addRoutePoint")) {
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
