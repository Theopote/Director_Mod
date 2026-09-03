package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
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
        return Icons.ANIMATION + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorPoseClip clip) {
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
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_OVERRIDE),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            boolean body = clip.isOverrideBody();
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY) + "##poseBodyEnabled", body)) {
                clip.setOverrideBody(!body);
                changed = true;
            }
            ImGui.sameLine();
            boolean head = clip.isOverrideHead();
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD) + "##poseHeadEnabled", head)) {
                clip.setOverrideHead(!head);
                changed = true;
            }
            boolean arms = clip.isOverrideArms();
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.ARMS) + "##poseArmsEnabled", arms)) {
                clip.setOverrideArms(!arms);
                changed = true;
            }
            ImGui.sameLine();
            boolean legs = clip.isOverrideLegs();
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEGS) + "##poseLegsEnabled", legs)) {
                clip.setOverrideLegs(!legs);
                changed = true;
            }
        }

        if (clip.isOverrideHead() && ImGui.collapsingHeader(Icons.TARGET + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_HEAD),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD_PITCH) + "##poseHeadPitch",
                    clip.getHeadPitch(), clip::setHeadPitch);
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD_YAW) + "##poseHeadYaw",
                    clip.getHeadYaw(), clip::setHeadYaw);
        }

        if (clip.isOverrideBody() && ImGui.collapsingHeader(Icons.TRANSFORM + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_BODY),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_PITCH) + "##poseBodyPitch",
                    clip.getBodyPitch(), clip::setBodyPitch);
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_YAW) + "##poseBodyYaw",
                    clip.getBodyYaw(), clip::setBodyYaw);
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_ROLL) + "##poseBodyRoll",
                    clip.getBodyRoll(), clip::setBodyRoll);
        }

        if (clip.isOverrideArms() && ImGui.collapsingHeader(Icons.HAND + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_ARMS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            String leftArm = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEFT_ARM);
            String rightArm = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.RIGHT_ARM);
            changed |= radSlider(leftArm + " Pitch##poseLeftArmPitch",
                    clip.getLeftArmPitch(), clip::setLeftArmPitch);
            changed |= radSlider(leftArm + " Yaw##poseLeftArmYaw",
                    clip.getLeftArmYaw(), clip::setLeftArmYaw);
            changed |= radSlider(leftArm + " Roll##poseLeftArmRoll",
                    clip.getLeftArmRoll(), clip::setLeftArmRoll);
            changed |= radSlider(rightArm + " Pitch##poseRightArmPitch",
                    clip.getRightArmPitch(), clip::setRightArmPitch);
            changed |= radSlider(rightArm + " Yaw##poseRightArmYaw",
                    clip.getRightArmYaw(), clip::setRightArmYaw);
            changed |= radSlider(rightArm + " Roll##poseRightArmRoll",
                    clip.getRightArmRoll(), clip::setRightArmRoll);
        }

        if (clip.isOverrideLegs() && ImGui.collapsingHeader(Icons.MOVE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_LEGS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEFT_LEG) + "##poseLeftLeg",
                    clip.getLeftLegPitch(), clip::setLeftLegPitch);
            changed |= radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.RIGHT_LEG) + "##poseRightLeg",
                    clip.getRightLegPitch(), clip::setRightLegPitch);
        }

        return changed;
    }

    private boolean radSlider(String label, float radians, FloatSetter setter) {
        float[] degrees = {(float) Math.toDegrees(radians)};
        if (ImGui.sliderFloat(label, degrees, -90.0f, 90.0f,
                ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DEGREE_FMT))) {
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
