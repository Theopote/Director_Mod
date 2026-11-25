package com.director.ui.actor.pose;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityActionEvent;
import com.director.actor.prop.*;
import imgui.ImGui;
import imgui.type.*;
import java.util.*;

/** Animation source events: explicit application, clock and parameter visibility. */
public final class ActorAnimationEventPanel {
    static String tr(String key) { return com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.event_editor." + key); }
    private TimelineModel sourceModel;
    private TimelineTrack sourceOwner;
    private EntityAnimationClip source;
    private List<AnimationEvent> before=List.of();
    private int index=-1;
    private final ImFloat time=new ImFloat(.5f);
    private AnimationEvent.Timing timing=AnimationEvent.Timing.CLIP;
    private EntityActionClip.ActionType type=EntityActionClip.ActionType.PLAY_SOUND;
    private Map<String,Object> params=new LinkedHashMap<>();
    private String message="";
    private final ActorAnimationEventTimeline timeline = new ActorAnimationEventTimeline();
    private void load(AnimationEvent event) {
        time.set(event.getNormalizedTime());timing=event.getTiming();type=event.getAction().type();params=new LinkedHashMap<>(PropEventParameters.copy(event.getAction().params(),false));
    }
    public void render(TimelineModel model,EntityAnimationClip clip) {
        if(!ImGui.collapsingHeader(tr("title"))) { timeline.cancel(); return; }
        var owner=clip.getTimelineClip()==null?null:clip.getTimelineClip().getParentTrack();
        render(model, owner, clip, true);
    }

    void renderTimeline(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip) {
        render(model, owner, clip, false);
    }

    void renderCanvasLane(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip, float duration,
                          float start, float end, float x, float y, float width, boolean hovered) {
        timeline.renderAssetLane(model, owner, clip, duration, start, end, x, y, width, hovered, this::beginDraft);
    }

    private void beginDraft(ActorAnimationEventTimeline.DraftSeed seed) {
        index = before.size();
        time.set(seed.phase());
        timing = seed.timing();
        type = EntityActionClip.ActionType.PLAY_SOUND;
        params = defaults(type);
        message = "";
    }

    private void syncSource(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip) {
        if (sourceModel == model && sourceOwner == owner && source == clip && before.equals(clip.getEvents())) return;
        sourceModel = model;
        sourceOwner = owner;
        source = clip;
        before = ActorAnimationEventEditing.copy(clip.getEvents());
        index = -1;
        message = "";
    }

    private void render(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip, boolean showList) {
        ImGui.pushID("eventEditorTimeline");
        try { renderContents(model, owner, clip, showList); }
        finally { ImGui.popID(); }
    }

    private void renderContents(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip, boolean showList) {
        syncSource(model, owner, clip);
        timeline.render(model, owner, clip, this::beginDraft);
        syncSource(model, owner, clip);
        boolean writable=ActorAnimationEventEditing.editable(model,owner,clip)&&ActorAnimationEventTimeline.canSeek(model);
        if (showList) ImGui.textWrapped(tr("clocks"));
        for(int i=0;showList && i<before.size();i++) {
            var event=before.get(i);
            if(ImGui.selectable(String.format("%.3f · %s · %s##event%d",event.getNormalizedTime(),tr(event.getTiming()==AnimationEvent.Timing.CLIP?"clip_clock":"asset_clock"),event.getAction().type().getDisplayName(),i),index==i)) {
                index=i;load(event);
                message=ActorAnimationEventTimeline.seek(model,owner,clip,event)?"":tr("seek_failed");
            }
        }
        ImGui.beginDisabled(!writable);
        if(showList && ImGui.button(tr("add"))) beginDraft(new ActorAnimationEventTimeline.DraftSeed(AnimationEvent.Timing.CLIP, .5f));
        if(index>=0) {
            ImGui.inputFloat(tr("time"),time);
            var clock=new ImInt(timing.ordinal());if(ImGui.combo(tr("clock"),clock,new String[]{tr("clip_clock"),tr("asset_clock")})) timing=AnimationEvent.Timing.values()[clock.get()];
            if(ImGui.beginCombo(tr("action"),type.getDisplayName())) {
                for(var candidate:AnimationEventParameters.supported())
                    if(ImGui.selectable(candidate.getDisplayName(),type==candidate)) {type=candidate;params=defaults(type);}
                ImGui.endCombo();
            }
            if(type==EntityActionClip.ActionType.PLAY_SOUND) {
                inputText("soundId","声音 ID");
                inputFloat("volume","音量",.8f);
                inputFloat("pitch","音调",1);
            } else if(type==EntityActionClip.ActionType.SPAWN_PARTICLE) {
                String selected=params.get("particle") instanceof String text?text:"POOF";
                if(ImGui.beginCombo("粒子类型",selected)) {
                    for(var candidate:AnimationEventParameters.PARTICLES)
                        if(ImGui.selectable(candidate,candidate.equals(selected))) params.put("particle",candidate);
                    ImGui.endCombo();
                }
                inputFloat("count","粒子数量",8);
                inputFloat("spread","扩散范围",.25f);
                inputFloat("speed","速度",.02f);
            } else if(type==EntityActionClip.ActionType.CAMERA_SHAKE) {
                inputFloat("intensity","抖动强度",.25f);
                inputFloat("duration","持续时间",.35f);
                inputFloat("frequency","抖动频率",14);
            } else if(type==EntityActionClip.ActionType.SET_VISIBILITY) {
                var visible=new imgui.type.ImBoolean(!Boolean.FALSE.equals(params.get("visible")));
                if(ImGui.checkbox("设为可见",visible)) params.put("visible",visible.get());
            } else {
            String[] slots=type==EntityActionClip.ActionType.SWITCH_PROP?new String[]{"newSlot","oldSlot"}:new String[]{"slot"};
            for(String key:slots) {
                String selected=params.get(key) instanceof String text?text:"";
                if(ImGui.beginCombo(tr(key)+"##"+key,selected)) {
                    if(key.equals("oldSlot")&&ImGui.selectable(tr("none"))) params.remove(key);
                    for(var slot:ActorPropSlot.values()) if(ImGui.selectable(slot.name(),slot.name().equals(selected))) params.put(key,slot.name());
                    ImGui.endCombo();
                }
            }
            if(type==EntityActionClip.ActionType.ATTACH_PROP || type==EntityActionClip.ActionType.SWITCH_PROP) {
                String key=type==EntityActionClip.ActionType.SWITCH_PROP?"newPropId":"propId";
                String selected=params.get(key) instanceof String text?text:"";
                if(ImGui.beginCombo(tr("prop"),selected)) {for(var prop:PropRegistry.getInstance().getAll())
                    if(ImGui.selectable(prop.getDisplayName()+"##"+prop.getId(),prop.getId().equals(selected))) params.put(key,prop.getId());ImGui.endCombo();}
            }
            if(type==EntityActionClip.ActionType.ATTACH_PROP || type==EntityActionClip.ActionType.SWITCH_PROP) {
                for(String key:List.of("offsetX","offsetY","offsetZ","rotationX","rotationY","rotationZ","propScale")) {
                    var value=new ImFloat(params.get(key) instanceof Number n?n.floatValue():key.equals("propScale")?1:0);
                    if(ImGui.inputFloat(tr(key)+"##"+key,value)) params.put(key,value.get());
                }
                String pose=params.get("itemPose") instanceof String text?text:"FIXED";
                if(ImGui.beginCombo(tr("orientation"),pose)) {for(String candidate:List.of("FIXED","HAND"))
                    if(ImGui.selectable(candidate,candidate.equals(pose))) params.put("itemPose",candidate);ImGui.endCombo();}
            }
            if(type==EntityActionClip.ActionType.THROW_PROP)
                for(String key:List.of("targetX","targetY","targetZ","arcHeight","flightTime")) {
                    float fallback=key.equals("flightTime")?1:key.equals("arcHeight")?1.5f:0;
                    var value=new ImFloat(params.get(key) instanceof Number n?n.floatValue():fallback);
                    if(ImGui.inputFloat(tr(key)+"##"+key,value)||!params.containsKey(key)) params.put(key,value.get());
                }
            }
            if(ImGui.collapsingHeader(tr("advanced"))) params.forEach((key,value)->ImGui.textWrapped(key+"："+value));
            if(ImGui.button(tr("apply"))) {
                try {
                    if(!Float.isFinite(time.get())||time.get()<0||time.get()>1) throw new IllegalArgumentException(tr("invalid_time"));
                    var next=new ArrayList<>(before);var event=new AnimationEvent(time.get(),new EntityActionEvent(type,params),timing);
                    if(index==next.size()) next.add(event);else next.set(index,event);
                    ActorAnimationEventEditing.replace(model,owner,clip,before,next);
                    before = ActorAnimationEventEditing.copy(clip.getEvents());
                    index=-1;message=tr("applied");
                } catch(RuntimeException failure){message=failure.getMessage();}
            }
            if(index>=0&&index<before.size()) {ImGui.sameLine();if(ImGui.button(tr("delete"))) {
                try {var next=new ArrayList<>(before);next.remove(index);ActorAnimationEventEditing.replace(model,owner,clip,before,next);index=-1;}
                catch(RuntimeException failure){message=failure.getMessage();}
            }}
        }
        ImGui.endDisabled();
        if(index>=0) { ImGui.sameLine(); if(ImGui.button(tr("cancel"))) { index=-1; message=""; } }
        if(!message.isEmpty()) ImGui.textWrapped(message);
    }

    private void inputText(String key,String label) {
        var value=new imgui.type.ImString(params.get(key) instanceof String text?text:"",256);
        if(ImGui.inputText(label+"##event:"+key,value)) params.put(key,value.get());
    }
    private void inputFloat(String key,String label,float fallback) {
        var value=new ImFloat(params.get(key) instanceof Number n?n.floatValue():fallback);
        if(ImGui.inputFloat(label+"##event:"+key,value) || !params.containsKey(key)) params.put(key,value.get());
    }
    private static Map<String,Object> defaults(EntityActionClip.ActionType type) {
        return switch(type) {
            case PLAY_SOUND -> new LinkedHashMap<>(Map.of("soundId","minecraft:entity.player.attack.sweep","volume",.8f,"pitch",1f));
            case SPAWN_PARTICLE -> new LinkedHashMap<>(Map.of("particle","POOF","count",8,"spread",.25f,"speed",.02f));
            case CAMERA_SHAKE -> new LinkedHashMap<>(Map.of("intensity",.25f,"duration",.35f,"frequency",14f));
            case SET_VISIBILITY -> new LinkedHashMap<>(Map.of("visible",true));
            case SWITCH_PROP -> new LinkedHashMap<>(Map.of("newSlot","MAIN_HAND"));
            default -> new LinkedHashMap<>(Map.of("slot","MAIN_HAND"));
        };
    }
}
