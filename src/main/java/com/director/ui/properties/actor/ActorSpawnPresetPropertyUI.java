package com.director.ui.properties.actor;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.AnimationLibrary;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.*;
import imgui.ImGui;
import imgui.type.ImInt;
import java.util.*;

/** Edits the Spawn inspector draft; the existing gesture commits one undoable change. */
public final class ActorSpawnPresetPropertyUI {
    private ActorSpawnPresetPropertyUI() {}
    private static final ActorPresetFocusRequest<EntitySpawnClip> focusRequest=new ActorPresetFocusRequest<>();
    public static void requestFocus(EntitySpawnClip source) {focusRequest.request(source);}
    private static final com.director.ui.actor.pose.ActorAnimationCatalog.Cache catalogCache=new com.director.ui.actor.pose.ActorAnimationCatalog.Cache();
    private static final com.director.ui.actor.pose.ActorInspectorPlayback playbackQuery=new com.director.ui.actor.pose.ActorInspectorPlayback();
    /** Called after the inspector draft commits, so duplication never races a pending property edit. */
    private static String independentError = "";
    private static EntitySpawnClip independentErrorOwner;

    public static void renderIndependentAction(EntitySpawnClip source) {
        if (independentErrorOwner != source) {
            independentError = "";
            independentErrorOwner = source;
        }
        if(source == null || source.getPreset()==null) return;
        var library=AnimationAssetLibrary.getInstance();
        var asset=library.find(source.getPreset().animationId()).orElse(null);
        if(asset==null) { independentError = ""; return; } // Built-in fixed Pose actions are read-only references, not editable rig assets.
        var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        var wrapper=source.getTimelineClip();var track=wrapper==null?null:wrapper.getParentTrack();
        boolean blocked=track==null || track.isLocked() || track.isMuted() || source.isMuted() || model.isPlaying()
                || model.isScrubbing() || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging();
        ImGui.beginDisabled(blocked);
        if(ImGui.button(presetText("independent") + "##spawnPresetIndependent")) {
            try {
                com.director.ui.actor.pose.ActorAnimationAssetEditing.makePresetIndependent(model,library,track,source,
                        "project:preset_"+UUID.randomUUID(),presetText("copy_name", asset.name()));
                independentError = "";
            } catch(IllegalArgumentException failure) {
                independentError = failure.getMessage() == null ? "" : failure.getMessage();
            }
        }
        ImGui.endDisabled();
        if (!independentError.isEmpty()) ImGui.textWrapped(independentError);
        else if(ImGui.isItemHovered()) ImGui.setTooltip(presetText("independent_hint"));
    }
    private static ActorPresetBinding renderScopeButtons(ActorPresetBinding binding,RigDefinition rig,ActorClipInspectorGesture gesture) {
        var result=binding;
        float available=Math.max(1,ImGui.getContentRegionAvailX());
        float rowEnd=ImGui.getCursorScreenPosX()+available;
        boolean first=true;
        for(var scope:ActorPresetScope.values()) {
            String scopeLabel = presetText("scope_" + scope.name().toLowerCase(Locale.ROOT));
            float width=Math.min(available,ImGui.calcTextSize(scopeLabel).x+ImGui.getStyle().getFramePaddingX()*2);
            if(!first && ImGui.getItemRectMaxX()+ImGui.getStyle().getItemSpacingX()+width<=rowEnd) ImGui.sameLine();
            first=false;
            boolean selected=scope.matches(result,rig);
            if(selected) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,ImGui.getColorU32(imgui.flag.ImGuiCol.ButtonActive));
            ImGui.beginDisabled(!scope.supported(rig));
            if(ImGui.button(scopeLabel+"##presetScope"+scope.name(),width,0)) {
                result=scope.apply(result,rig);gesture.requestCommit();
            }
            ImGui.endDisabled();
            if(selected) ImGui.popStyleColor();
            if(!scope.supported(rig) && ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled))
                ImGui.setTooltip(presetText("scope_unavailable"));
        }
        var resolved = result;
        if (Arrays.stream(ActorPresetScope.values()).noneMatch(scope -> scope.matches(resolved, rig)))
            ImGui.textDisabled(presetText("scope_custom_hint"));
        return result;
    }

    public static void render(EntitySpawnClip source,EntitySpawnClip draft,ActorClipInspectorGesture gesture) {
        boolean focus=focusRequest.consume(source);
        if(focus) ImGui.setNextItemOpen(true,imgui.flag.ImGuiCond.Always);
        if(!ImGui.collapsingHeader(presetText("title"))) {playbackQuery.clear();return;}
        if(focus) ImGui.setScrollHereY(0);
        ImGui.pushID("spawnPreset");
        try {
            var wrapper=source.getTimelineClip();
            var payload=wrapper==null || wrapper.getParentTrack()==null?null:wrapper.getParentTrack().getActorTrackPayload();
            var actor=payload==null || payload.getActorRef()==null?null:
                    ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
            var catalog=catalogCache.entries(AnimationAssetLibrary.getInstance(),AnimationLibrary.getInstance(),actor);
            var rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
            var binding=draft.getPreset();
            String current = presetText("none");
            if(binding!=null) {
                String id=binding.animationId();
                current = catalog.stream().filter(e -> e.id().equals(id))
                        .map(e -> e.name() + " · " + e.purpose().label() + (e.compatible() ? "" : " · " + presetText("incompatible")))
                        .findFirst().orElseGet(() -> presetText("missing", id));
            }
            if (ImGui.beginCombo(presetText("primary") + "##primaryAsset", current)) {
                try {
                    if (ImGui.selectable(presetText("none") + "##clearPrimary", binding == null) && binding != null) {
                        binding = null;
                        gesture.requestCommit();
                    }
                    var secondary = draft.getSecondaryPreset();
                    for (var entry : catalog) {
                        if (entry.isPose() || !entry.compatible() || entry.id().equals(draft.getBoneAnimationId())) continue;
                        boolean selected = binding != null && entry.id().equals(binding.animationId());
                        boolean blocked = secondary != null && secondary.fullyOverridesSameAction(entry.id());
                        ImGui.beginDisabled(blocked);
                        if (ImGui.selectable(entry.name() + " · " + entry.rigName() + "##" + entry.id(), selected) && !selected) {
                            binding = ActorPresetBinding.select(entry.id(), binding);
                            gesture.requestCommit();
                        }
                        ImGui.endDisabled();
                        if (blocked && ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled))
                            ImGui.setTooltip(presetText("primary_blocked"));
                    }
                } finally {
                    ImGui.endCombo();
                }
            }
            draft.setPreset(binding);
            renderSecondary(draft, gesture, catalog, rig);
            if(binding==null) {playbackQuery.clear();return;}
            boolean changed;
            var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
            var playback=playbackQuery.sample(payload,model.getCurrentTime());
            boolean distanceClock= com.director.actor.runtime.ActorPresetClock.followsDistance(binding.animationId(), playback);
            if(distanceClock) ImGui.textWrapped(presetText("distance_clock"));
            else if(com.director.actor.runtime.ActorPresetClock.purpose(binding.animationId())==com.director.actor.animation.AnimationData.Purpose.LOCOMOTION)
                ImGui.textWrapped(presetText("local_gait"));
            else ImGui.textWrapped(presetText("local_action"));
            ImGui.beginDisabled(distanceClock);
            boolean loop=binding.loop();changed=ImGui.checkbox(presetText("loop"),loop);
            if(changed) loop=!loop;gesture.observe("presetLoop",changed);
            float[] speed={binding.speed()};changed=ImGui.sliderFloat(presetText("speed"),speed,.01f,4);gesture.observe("presetSpeed",changed);
            ImGui.endDisabled();
            var blend=binding.blend();float[] weight={blend.weight()};
            changed=ImGui.sliderFloat(presetText("primary_weight"),weight,0,1);gesture.observe("presetWeight",changed);
            var mode=new ImInt(blend.mode().ordinal());
            float[] fades={blend.fadeIn(),blend.fadeOut()};
            int channels=blend.channels();
            if(ImGui.treeNode(presetText("advanced") + "##presetAdvancedBlend")) {
                changed=ImGui.combo(presetText("blend"),mode,new String[]{presetText("override"),presetText("additive")});
                gesture.observe("presetMode",changed);
                changed=ImGui.inputFloat2(presetText("fades"),fades);
                if(!Float.isFinite(fades[0]) || !Float.isFinite(fades[1]) || fades[0]<0 || fades[1]<0) {
                    fades[0]=blend.fadeIn();fades[1]=blend.fadeOut();
                }
                gesture.observe("presetFades",changed);
                ImGui.textDisabled(presetText("fade_hint"));
                int[] bits={RigPose.TRANSLATION,RigPose.ROTATION,RigPose.SCALE};
                String[] labels={presetText("translation"),presetText("rotation"),presetText("scale")};
                for(int i=0;i<bits.length;i++) {
                    changed=ImGui.checkbox(labels[i],(channels&bits[i])!=0);
                    if(changed) channels^=bits[i];gesture.observe("presetChannel"+i,changed);
                }
                ImGui.treePop();
            }
            ImGui.text(presetText("scope"));
            var scopeInput=new ActorPresetBinding(binding.animationId(),loop,speed[0],
                    new PoseBlendSettings(PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,
                            blend.allBones(),blend.boneIds(),fades[0],fades[1]));
            blend=renderScopeButtons(scopeInput,rig,gesture).blend();
            ImGui.textDisabled(presetText("scope_help"));
            if(ImGui.isItemHovered()) ImGui.setTooltip(presetText("scope_hint"));
            boolean all=blend.allBones();
            var bones=new LinkedHashSet<>(blend.boneIds());
            if(ImGui.treeNode(presetText("custom_bones") + "##presetCustomBones")) {
                changed=ImGui.checkbox(presetText("all_bones"),all);
                if(changed) all=!all;gesture.observe("presetAllBones",changed);
                if(!all && rig!=null) for(var bone:rig.bones()) {
                    changed=ImGui.checkbox(bone.displayName()+"##presetBone"+bone.id(),bones.contains(bone.id()));
                    if(changed) {if(!bones.remove(bone.id())) bones.add(bone.id());}
                    gesture.observe("presetBone"+bone.id(),changed);
                }
                ImGui.treePop();
            }
            var next=new ActorPresetBinding(binding.animationId(),loop,speed[0],new PoseBlendSettings(
                    PoseLayer.BlendMode.values()[mode.get()],weight[0],channels,all,bones,fades[0],fades[1]));
            draft.setPreset(next);
            float time=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel().getCurrentTime();
            var feedback=ActorPresetFeedback.resolve(next,rig,time-draft.getStartTime(),draft.getLength(),playback,actor==null?null:actor.getRigProfile());
            boolean muted=source.isMuted() || wrapper!=null && wrapper.getParentTrack()!=null && wrapper.getParentTrack().isMuted();
            ImGui.text(presetText("effective_weight", String.format(Locale.ROOT, "%.1f%%", 100 * (muted ? 0 : feedback.weight()))));
            if(!feedback.available()) ImGui.textWrapped(presetText("unavailable"));
            else if(!feedback.authoredData()) ImGui.textWrapped(presetText("empty_asset"));
            else if(feedback.bones().isEmpty()) {
                ImGui.textWrapped(presetText("empty_scope"));
                if(ImGui.button(presetText("restore_scope") + "##restorePresetMask")) {
                    draft.setPreset(ActorPresetScope.restoreChannelsAndBones(next));gesture.requestCommit();
                }
            }
            else if(rig!=null) ImGui.textWrapped(presetText("affected_bones", feedback.bones().stream()
                    .map(id->rig.bone(rig.requireBoneIndex(id)).displayName()).collect(java.util.stream.Collectors.joining(", "))));
            var routeFeedback=ActorPresetRouteFeedback.resolve(feedback,playback,muted);
            if(routeFeedback==ActorPresetRouteFeedback.LOCAL_CLOCK) {
                ImGui.textWrapped(presetText("leg_local"));
                ImGui.beginDisabled(!ActorPresetScope.UPPER.supported(rig));
                if(ImGui.button(presetText("use_upper") + "##fixPresetLegScope")) {
                    draft.setPreset(ActorPresetScope.UPPER.apply(next,rig));
                    gesture.requestCommit();
                }
                ImGui.endDisabled();
                ImGui.textWrapped(presetText("gait_usage"));
            } else if(routeFeedback==ActorPresetRouteFeedback.DISTANCE_CLOCK) {
                ImGui.textWrapped(presetText("leg_distance"));
            }
            if(ImGui.treeNode(presetText("composition"))) {
                ImGui.textWrapped(presetText("composition_route"));
                ImGui.textWrapped(presetText("composition_manual"));
                ImGui.textWrapped(presetText("composition_blend"));
                ImGui.textWrapped(presetText("composition_debug"));
                ImGui.treePop();
            }


        } finally { ImGui.popID(); }
    }

    private static String presetText(String key, Object... args) {
        return com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.preset." + key, args);
    }

    private static void renderSecondary(EntitySpawnClip draft, ActorClipInspectorGesture gesture,
            List<com.director.ui.actor.pose.ActorAnimationCatalog.Entry> catalog, RigDefinition rig) {
        if (!ImGui.treeNode(presetText("secondary") + "##secondaryPreset")) return;
        try {
            var binding = draft.getSecondaryPreset();
            String id = binding == null ? null : binding.animationId();
            String label = id == null ? presetText("none") : catalog.stream().filter(entry -> entry.id().equals(id))
                    .map(entry -> entry.name() + (entry.compatible() ? "" : " · " + presetText("incompatible")))
                    .findFirst().orElseGet(() -> presetText("missing", id));
            if (ImGui.treeNode(presetText("layer_help"))) {
                ImGui.textWrapped(presetText("secondary_order"));
                ImGui.textWrapped(presetText("secondary_overlap"));
                ImGui.treePop();
            }
            if (ImGui.beginCombo(presetText("action") + "##secondaryAsset", label)) {
                try {
                    if (ImGui.selectable(presetText("none") + "##clearSecondary", binding == null) && binding != null) {
                        binding = null;
                        gesture.requestCommit();
                    }
                    for (var entry : catalog) {
                        if (entry.isPose() || !entry.compatible() || entry.id().equals(draft.getBoneAnimationId())) continue;
                        if (ImGui.selectable(entry.name() + "##" + entry.id(), entry.id().equals(id))
                                && !entry.id().equals(id)) {
                            boolean first = binding == null;
                            binding = ActorPresetBinding.select(entry.id(), binding);
                            var primary = draft.getPreset();
                            if (ActorPresetScope.UPPER.supported(rig) && (first
                                    || primary != null && binding.fullyOverridesSameAction(primary.animationId())))
                                binding = ActorPresetScope.UPPER.apply(binding, rig);
                            gesture.requestCommit();
                        }
                    }
                } finally { ImGui.endCombo(); }
            }
            if (binding == null) {
                draft.setSecondaryPreset(null);
                return;
            }
            boolean loop = binding.loop();
            boolean changed = ImGui.checkbox(presetText("loop") + "##secondaryLoop", loop);
            if (changed) loop = !loop;
            gesture.observe("secondaryLoop", changed);
            float[] speed = {binding.speed()};
            changed = ImGui.sliderFloat(presetText("speed") + "##secondarySpeed", speed, .01f, 4f);
            gesture.observe("secondarySpeed", changed);
            var blend = binding.blend();
            float[] weight = {blend.weight()};
            changed = ImGui.sliderFloat(presetText("weight") + "##secondaryWeight", weight, 0f, 1f);
            gesture.observe("secondaryWeight", changed);
            var updated = new ActorPresetBinding(binding.animationId(), loop, speed[0],
                    new PoseBlendSettings(blend.mode(), weight[0], blend.channels(), blend.allBones(), blend.boneIds(),
                            blend.fadeIn(), blend.fadeOut()));
            draft.setSecondaryPreset(renderScopeButtons(updated, rig, gesture));
        } finally { ImGui.treePop(); }
    }
}
