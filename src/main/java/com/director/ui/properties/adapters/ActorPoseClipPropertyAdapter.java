package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.Section;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.ActorPoseClip;
import imgui.ImGui;

public final class ActorPoseClipPropertyAdapter implements IPropertyAdapter<ActorPoseClip> {
    private final com.director.ui.properties.actor.ActorClipInspectorGesture gesture = new com.director.ui.properties.actor.ActorClipInspectorGesture();
    @Override
    public Class<ActorPoseClip> getTargetType() {
        return ActorPoseClip.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.ANIMATION + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorPoseClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof ActorPoseClip clip)) { gesture.end(); return false; }
        var owner = source.getTimelineClip().getParentTrack().getActorTrackPayload();
        var actor = com.director.actor.ActorRegistryManager.getInstance().getActor(owner.getActorRef().getActorId()).orElse(null);
        boolean supported = com.director.actor.ActorBehaviorCapabilities.supportsLegacyPose(actor);
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.pose_edit.sampling_hint"));
        if (!supported) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.pose_edit.unsupported"));
        if (!source.getKeyframes().isEmpty()) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.pose_edit.keys_hint"));
        ImGui.beginDisabled(!supported);
        UITheme.title(getAdapterName());

        if (Section.header("actor_pose_clip.section_basic", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC))) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
        }

        if (Section.header("actor_pose_clip.section_override", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_OVERRIDE))) {
            boolean body = clip.isOverrideBody();
            PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY));
            boolean toggleBody = ImGui.checkbox("##poseBodyEnabled", body);
            if (toggleBody) clip.setOverrideBody(!clip.isOverrideBody());
            gesture.observe("overrideBody",toggleBody);
            boolean head = clip.isOverrideHead();
            PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD));
            boolean toggleHead = ImGui.checkbox("##poseHeadEnabled", head);
            if (toggleHead) clip.setOverrideHead(!clip.isOverrideHead());
            gesture.observe("overrideHead",toggleHead);
            boolean arms = clip.isOverrideArms();
            PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.ARMS));
            boolean toggleArms = ImGui.checkbox("##poseArmsEnabled", arms);
            if (toggleArms) clip.setOverrideArms(!clip.isOverrideArms());
            gesture.observe("overrideArms",toggleArms);
            boolean legs = clip.isOverrideLegs();
            PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEGS));
            boolean toggleLegs = ImGui.checkbox("##poseLegsEnabled", legs);
            if (toggleLegs) clip.setOverrideLegs(!clip.isOverrideLegs());
            gesture.observe("overrideLegs",toggleLegs);
        }

        if (clip.isOverrideHead() && Section.header("actor_pose_clip.section_head", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_HEAD))) {
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD_PITCH) + "##poseHeadPitch",
                    clip.getHeadPitch(), clip::setHeadPitch);
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD_YAW) + "##poseHeadYaw",
                    clip.getHeadYaw(), clip::setHeadYaw);
        }

        if (clip.isOverrideBody() && Section.header("actor_pose_clip.section_body", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_BODY))) {
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_PITCH) + "##poseBodyPitch",
                    clip.getBodyPitch(), clip::setBodyPitch);
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_YAW) + "##poseBodyYaw",
                    clip.getBodyYaw(), clip::setBodyYaw);
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_ROLL) + "##poseBodyRoll",
                    clip.getBodyRoll(), clip::setBodyRoll);
        }

        if (clip.isOverrideArms() && Section.header("actor_pose_clip.section_arms", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_ARMS))) {
            String leftArm = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEFT_ARM);
            String rightArm = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.RIGHT_ARM);
            radSlider(leftArm + " Pitch##poseLeftArmPitch",
                    clip.getLeftArmPitch(), clip::setLeftArmPitch);
            radSlider(leftArm + " Yaw##poseLeftArmYaw",
                    clip.getLeftArmYaw(), clip::setLeftArmYaw);
            radSlider(leftArm + " Roll##poseLeftArmRoll",
                    clip.getLeftArmRoll(), clip::setLeftArmRoll);
            radSlider(rightArm + " Pitch##poseRightArmPitch",
                    clip.getRightArmPitch(), clip::setRightArmPitch);
            radSlider(rightArm + " Yaw##poseRightArmYaw",
                    clip.getRightArmYaw(), clip::setRightArmYaw);
            radSlider(rightArm + " Roll##poseRightArmRoll",
                    clip.getRightArmRoll(), clip::setRightArmRoll);
        }

        if (clip.isOverrideLegs() && Section.header("actor_pose_clip.section_legs", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.SECTION_LEGS))) {
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEFT_LEG) + "##poseLeftLeg",
                    clip.getLeftLegPitch(), clip::setLeftLegPitch);
            radSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.RIGHT_LEG) + "##poseRightLeg",
                    clip.getRightLegPitch(), clip::setRightLegPitch);
        }

        ImGui.endDisabled();
        gesture.end();
        return false;
    }

    private boolean radSlider(String label, float radians, FloatSetter setter) {
        float[] degrees = {(float) Math.toDegrees(radians)};
        int id = label.indexOf("##");
        PropertyRow.field(label.substring(0, id));
        boolean changed = ImGui.sliderFloat(label.substring(id), degrees, -90.0f, 90.0f,
                "%.1f deg");
        if (changed) setter.set((float) Math.toRadians(degrees[0]));
        gesture.observe(label,changed);
        return changed;
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
