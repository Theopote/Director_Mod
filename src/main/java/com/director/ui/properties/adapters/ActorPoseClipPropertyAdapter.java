package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.ActorPoseClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;

public final class ActorPoseClipPropertyAdapter implements IPropertyAdapter<ActorPoseClip> {
    @Override
    public Class<ActorPoseClip> getTargetType() {
        return ActorPoseClip.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.ANIMATION + " 局部姿态片段";
    }

    @Override
    public boolean renderProperties(ActorPoseClip clip) {
        if (clip == null) {
            ImGui.textDisabled("无 PoseClip");
            return false;
        }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.TIME + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text(String.format("开始: %.2fs", clip.getStartTime()));
            ImGui.text(String.format("结束: %.2fs", clip.getEndTime()));
            ImGui.text(String.format("时长: %.2fs", clip.getLength()));
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " 覆盖部位", ImGuiTreeNodeFlags.DefaultOpen)) {
            boolean body = clip.isOverrideBody();
            if (ImGui.checkbox("身体##poseBodyEnabled", body)) {
                clip.setOverrideBody(!body);
                changed = true;
            }
            ImGui.sameLine();
            boolean head = clip.isOverrideHead();
            if (ImGui.checkbox("头部##poseHeadEnabled", head)) {
                clip.setOverrideHead(!head);
                changed = true;
            }
            boolean arms = clip.isOverrideArms();
            if (ImGui.checkbox("手臂##poseArmsEnabled", arms)) {
                clip.setOverrideArms(!arms);
                changed = true;
            }
            ImGui.sameLine();
            boolean legs = clip.isOverrideLegs();
            if (ImGui.checkbox("腿部##poseLegsEnabled", legs)) {
                clip.setOverrideLegs(!legs);
                changed = true;
            }
        }

        if (clip.isOverrideHead() && ImGui.collapsingHeader(Icons.TARGET + " 头部", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider("抬头/低头##poseHeadPitch", clip.getHeadPitch(), clip::setHeadPitch);
            changed |= radSlider("左右转头##poseHeadYaw", clip.getHeadYaw(), clip::setHeadYaw);
        }

        if (clip.isOverrideBody() && ImGui.collapsingHeader(Icons.TRANSFORM + " 身体", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider("俯仰##poseBodyPitch", clip.getBodyPitch(), clip::setBodyPitch);
            changed |= radSlider("扭转##poseBodyYaw", clip.getBodyYaw(), clip::setBodyYaw);
            changed |= radSlider("侧倾##poseBodyRoll", clip.getBodyRoll(), clip::setBodyRoll);
        }

        if (clip.isOverrideArms() && ImGui.collapsingHeader(Icons.HAND + " 手臂", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider("左臂俯仰##poseLeftArm", clip.getLeftArmPitch(), clip::setLeftArmPitch);
            changed |= radSlider("右臂俯仰##poseRightArm", clip.getRightArmPitch(), clip::setRightArmPitch);
        }

        if (clip.isOverrideLegs() && ImGui.collapsingHeader(Icons.MOVE + " 腿部", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider("左腿俯仰##poseLeftLeg", clip.getLeftLegPitch(), clip::setLeftLegPitch);
            changed |= radSlider("右腿俯仰##poseRightLeg", clip.getRightLegPitch(), clip::setRightLegPitch);
        }

        return changed;
    }

    private boolean radSlider(String label, float radians, FloatSetter setter) {
        float[] degrees = { (float) Math.toDegrees(radians) };
        if (ImGui.sliderFloat(label, degrees, -90.0f, 90.0f, "%.1f deg")) {
            setter.set((float) Math.toRadians(degrees[0]));
            return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
