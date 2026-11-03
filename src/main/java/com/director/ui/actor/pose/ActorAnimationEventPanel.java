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
    private static String tr(String key) { return com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.event_editor." + key); }
    private EntityAnimationClip source;
    private List<AnimationEvent> before=List.of();
    private int index=-1;
    private final ImFloat time=new ImFloat(.5f);
    private AnimationEvent.Timing timing=AnimationEvent.Timing.CLIP;
    private EntityActionClip.ActionType type=EntityActionClip.ActionType.DETACH_PROP;
    private Map<String,Object> params=new LinkedHashMap<>();
    private String message="";
    private void load(AnimationEvent event) {
        time.set(event.getNormalizedTime());timing=event.getTiming();type=event.getAction().type();params=new LinkedHashMap<>(PropEventParameters.copy(event.getAction().params(),false));
    }
    public void render(TimelineModel model,EntityAnimationClip clip) {
        if(!ImGui.collapsingHeader(tr("title"))) return;
        if(source!=clip || !before.equals(clip.getEvents())) {source=clip;before=ActorAnimationEventEditing.copy(clip.getEvents());index=-1;message="";}
        var owner=clip.getTimelineClip()==null?null:clip.getTimelineClip().getParentTrack();
        ImGui.pushID("eventEditorTimeline");
        try { ActorAnimationEventTimeline.render(model,owner,clip); }
        finally { ImGui.popID(); }
        boolean writable=ActorAnimationEventEditing.editable(model,owner,clip)&&!ActorBoneGizmoController.getInstance().isDragging();
        ImGui.textWrapped("片段进度只按片段触发；动画源进度跟随速度、循环与路径距离时钟，每次经过该相位都会触发。");
        for(int i=0;i<before.size();i++) {
            var event=before.get(i);
            if(ImGui.selectable(String.format("%.3f · %s · %s##event%d",event.getNormalizedTime(),tr(event.getTiming()==AnimationEvent.Timing.CLIP?"clip_clock":"asset_clock"),event.getAction().type().getDisplayName(),i),index==i)) {
                index=i;load(event);
                message=ActorAnimationEventTimeline.seek(model,owner,clip,event)?"":"当前无法定位此事件；请先暂停、结束草稿编辑，并检查片段中的事件时刻。";
            }
        }
        ImGui.beginDisabled(!writable);
        if(ImGui.button(tr("add"))) {index=before.size();time.set(.5f);timing=AnimationEvent.Timing.CLIP;type=EntityActionClip.ActionType.DETACH_PROP;params=new LinkedHashMap<>(Map.of("slot","MAIN_HAND"));}
        if(index>=0) {
            ImGui.inputFloat(tr("time"),time);
            var clock=new ImInt(timing.ordinal());if(ImGui.combo(tr("clock"),clock,new String[]{tr("clip_clock"),tr("asset_clock")})) timing=AnimationEvent.Timing.values()[clock.get()];
            if(ImGui.beginCombo(tr("action"),type.getDisplayName())) {
                for(var candidate:EntityActionClip.ActionType.values()) if(PropEventParameters.supports(candidate))
                    if(ImGui.selectable(candidate.getDisplayName(),type==candidate)) {type=candidate;params.putIfAbsent(type==EntityActionClip.ActionType.SWITCH_PROP?"newSlot":"slot","MAIN_HAND");}
                ImGui.endCombo();
            }
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
            if(ImGui.collapsingHeader(tr("advanced"))) params.forEach((key,value)->ImGui.textWrapped(key+"："+value));
            if(ImGui.button(tr("apply"))) {
                try {
                    if(!Float.isFinite(time.get())||time.get()<0||time.get()>1) throw new IllegalArgumentException("事件时间必须为 0–1");
                    var next=new ArrayList<>(before);var event=new AnimationEvent(time.get(),new EntityActionEvent(type,params),timing);
                    if(index==next.size()) next.add(event);else next.set(index,event);
                    next.sort(Comparator.comparing(AnimationEvent::getNormalizedTime));ActorAnimationEventEditing.replace(model,owner,clip,before,next);index=-1;message="已应用，可撤销。";
                } catch(RuntimeException failure){message=failure.getMessage();}
            }
            if(index>=0&&index<before.size()) {ImGui.sameLine();if(ImGui.button(tr("delete"))) {
                try {var next=new ArrayList<>(before);next.remove(index);ActorAnimationEventEditing.replace(model,owner,clip,before,next);index=-1;}
                catch(RuntimeException failure){message=failure.getMessage();}
            }}
            ImGui.sameLine();if(ImGui.button(tr("cancel"))) index=-1;
        }
        ImGui.endDisabled();if(!message.isEmpty()) ImGui.textWrapped(message);
    }
}
