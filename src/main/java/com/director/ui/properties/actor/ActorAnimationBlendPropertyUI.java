package com.director.ui.properties.actor;

import com.director.actor.ActorRegistryManager;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.Card;

import imgui.ImGui;
import imgui.type.ImInt;
import java.util.*;
import java.util.function.Consumer;

/** Controls edit the inspector draft; the gesture owns commit and undo. */
public final class ActorAnimationBlendPropertyUI {
    private ActorAnimationBlendPropertyUI() {}

    private static String tr(String key, Object... args) {
        return ActorPropertyAdapterI18n.tr(key, args);
    }

    public static void render(EntityClip source, PoseBlendSettings initial, Consumer<PoseBlendSettings> save,
                              ActorClipInspectorGesture gesture) {
        if (!Card.begin("actor.animation_blend", tr(DirectorLang.Properties.AdapterActorBlend.TITLE), false)) { Card.end(); return; }
        try {
            if (source instanceof com.director.timeline.model.entity.EntityAnimationClip animation
                    && com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).isEmpty()
                    && com.director.actor.animation.AnimationLibrary.getInstance().get(animation.getAnimationId()).isEmpty()) {
                ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.INVALID_REF));
                return;
            }
            if (source instanceof com.director.timeline.model.entity.EntityAnimationClip animation
                    && com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).isEmpty()) {
                var wrapper = source.getTimelineClip();
                var payload = wrapper == null || wrapper.getParentTrack() == null ? null : wrapper.getParentTrack().getActorTrackPayload();
                var actor = payload == null || payload.getActorRef() == null ? null :
                        ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
                var rig = ActorRigCompatibility.resolveRig(actor).orElse(null);
                if (!ActorRigCompatibility.acceptsBuiltin(rig, animation.getAnimationId())) {
                    ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.INCOMPATIBLE_RIG));
                    return;
                }
                var data = com.director.actor.animation.AnimationLibrary.getInstance().get(animation.getAnimationId()).orElseThrow();
                String parts = rig.bones().stream().filter(b -> data.controlledBones().contains(b.id()))
                        .map(ActorAnimationBlendPropertyUI::boneLabel).collect(java.util.stream.Collectors.joining(", "));
                ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.DEFAULT_BONES_FMT, parts.isEmpty() ? tr(DirectorLang.Properties.AdapterActorBlend.NO_BONES) : parts));
            }
            if (source instanceof com.director.timeline.model.entity.EntitySpawnClip spawn && spawn.getBoneAnimationId() == null)
                ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.NO_KEYFRAMES_HINT));
            var mode = new ImInt(initial.mode().ordinal());
            PropertyRow.field(tr(DirectorLang.Properties.AdapterActorBlend.MODE));
            boolean changed = ImGui.combo("##blendMode", mode, new String[]{
                    tr(DirectorLang.Properties.AdapterActorBlend.MODE_OVERRIDE),
                    tr(DirectorLang.Properties.AdapterActorBlend.MODE_ADDITIVE)});
            gesture.observe("blendMode", changed);
            float[] weight = {initial.weight()};
            PropertyRow.field(tr(DirectorLang.Properties.AdapterActorBlend.WEIGHT));
            changed = ImGui.sliderFloat("##blendWeight", weight, 0, 1); gesture.observe("blendWeight", changed);
            float fadeIn = durationInput(tr(DirectorLang.Properties.AdapterActorBlend.FADE_IN), "blendFadeIn", initial.fadeIn(), gesture);
            float fadeOut = durationInput(tr(DirectorLang.Properties.AdapterActorBlend.FADE_OUT), "blendFadeOut", initial.fadeOut(), gesture);
            ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.FADE_HINT));
            int channels = initial.channels();
            int[] bits = {RigPose.TRANSLATION, RigPose.ROTATION, RigPose.SCALE};
            String[] labels = {
                    tr(DirectorLang.Properties.AdapterActorBlend.CHANNEL_TRANSLATION),
                    tr(DirectorLang.Properties.AdapterActorBlend.CHANNEL_ROTATION),
                    tr(DirectorLang.Properties.AdapterActorBlend.CHANNEL_SCALE)};
            for (int i = 0; i < bits.length; i++) {
                PropertyRow.label(labels[i]);
                changed = ImGui.checkbox("##blendChannel" + i, (channels & bits[i]) != 0);
                if (changed) channels ^= bits[i]; gesture.observe("blendChannel" + i, changed);
            }
            boolean all = initial.allBones();
            PropertyRow.label(tr(DirectorLang.Properties.AdapterActorBlend.ALL_BONES));
            changed = ImGui.checkbox("##blendAllBones", all); if (changed) all = !all;
            gesture.observe("blendAllBones", changed);
            Set<String> bones = new LinkedHashSet<>(initial.boneIds());
            var wrapper = source.getTimelineClip();
            var payload = wrapper == null || wrapper.getParentTrack() == null ? null : wrapper.getParentTrack().getActorTrackPayload();
            var actor = payload == null || payload.getActorRef() == null ? null :
                    ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
            if (!all) {
                var rig = ActorRigCompatibility.resolveRig(actor).orElse(null);
                if (rig != null) for (var bone : rig.bones()) {
                    PropertyRow.label(boneLabel(bone));
                    changed = ImGui.checkbox("##blendBone" + bone.id(), bones.contains(bone.id()));
                    if (changed) { if (!bones.remove(bone.id())) bones.add(bone.id()); }
                    gesture.observe("blendBone" + bone.id(), changed);
                }
                else ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.NO_RIG));
                if (bones.isEmpty()) ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.NO_BONES));
            }
            ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.CHANNEL_HINT));
            var settings = new PoseBlendSettings(PoseLayer.BlendMode.values()[mode.get()], weight[0], channels, all, bones, fadeIn, fadeOut);
            save.accept(settings);
            var timeline = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
            float time = timeline.getCurrentTime();
            boolean muted = source.isMuted() || wrapper != null && wrapper.getParentTrack() != null && wrapper.getParentTrack().isMuted();
            float current = muted ? 0 : com.director.ui.timeline.render.ActorBlendEnvelope.configuredWeight(settings, time - source.getStartTime(), source.getLength());
            ImGui.text(tr(DirectorLang.Properties.AdapterActorBlend.CURRENT_WEIGHT_FMT,
                    String.format(Locale.ROOT, "%.2f", time), String.format(Locale.ROOT, "%.1f", current * 100)));
            if (muted) ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.MUTED));
            else if (time < source.getStartTime() || time > source.getEndTime()) ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.OUT_OF_RANGE));
            if (channels == 0 || !all && bones.isEmpty()) ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.EMPTY_CHANNELS));
            ImGui.textWrapped(tr(DirectorLang.Properties.AdapterActorBlend.ENVELOPE_HINT));
        } finally {
            Card.end();
        }
    }

    private static String boneLabel(BoneDefinition bone) {
        String key = "gui.director.actor.blend.bone." + bone.id();
        String label = tr(key);
        return key.equals(label) ? bone.displayName() : label;
    }

    private static float durationInput(String label, String id, float value, ActorClipInspectorGesture gesture) {
        var input = new imgui.type.ImFloat(value);
        PropertyRow.field(label);
        boolean changed = ImGui.inputFloat("##" + id, input, .05f, .2f, "%.3f s");
        if (changed && (!Float.isFinite(input.get()) || input.get() < 0)) {
            gesture.report(tr(DirectorLang.Properties.AdapterActorBlend.FADE_INVALID));
            gesture.observe(id, false);
            return value;
        }
        gesture.observe(id, changed);
        return input.get();
    }
}
