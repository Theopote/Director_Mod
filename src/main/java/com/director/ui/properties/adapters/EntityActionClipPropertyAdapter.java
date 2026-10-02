package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import com.director.actor.prop.*;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.ui.actor.edit.ActorClipPropertyEditSession;
import com.director.ui.properties.IPropertyAdapter;
import com.director.timeline.model.entity.EntityActionClip;
import imgui.ImGui;
import imgui.type.ImFloat;
import java.util.*;

/** Multi-field event forms commit atomically with an explicit Apply button. */
public class EntityActionClipPropertyAdapter implements IPropertyAdapter<EntityActionClip> {
    private ActorClipPropertyEditSession session;
    private int lastFrame = -1;
    private String error = "";
    private boolean cancelledUntilRelease;
    @Override public Class<EntityActionClip> getTargetType() { return EntityActionClip.class; }
    private String tr(String key) { return ActorPropertyAdapterI18n.tr("gui.director.actor.prop_event."+key); }
    @Override public boolean renderProperties(EntityActionClip source) {
        com.director.ui.properties.actor.ActorRuntimeDiagnosticsUI.render(source);
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        boolean competing = com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        int frame = ImGui.getFrameCount();
        if (session != null && (frame != lastFrame+1 || competing || !session.isCurrent(model,source))) {
            session.cancel(); session = null; error = tr("cancelled");
            cancelledUntilRelease = true;
        }
        lastFrame = frame;
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
            if (session != null) session.cancel(); session = null;
            cancelledUntilRelease = true;
            error = "";
        }
        if (cancelledUntilRelease) {
            if (ImGui.isMouseDown(0) || ImGui.isAnyItemActive()) { ImGui.textDisabled(tr("cancelled")); return false; }
            cancelledUntilRelease = false;
        }
        if (source == null || competing) return false;
        if (session == null) {
            try { session = new ActorClipPropertyEditSession(model,source); }
            catch (RuntimeException unavailable) { ImGui.textDisabled(tr("unavailable")); return false; }
        }
        var clip = (EntityActionClip)session.draft();
        Actor actor = ActorRegistryManager.getInstance().getActor(source.getTimelineClip().getParentTrack()
                .getActorTrackPayload().getActorRef().getActorId()).orElse(null);
        ImGui.text(tr("title")); ImGui.textWrapped(tr("preview_hint"));
        if (ImGui.beginCombo(tr("type")+"##propEventType",tr("type_"+clip.getActionType().name()))) {
            for (var type : EntityActionClip.ActionType.values()) {
                ImGui.beginDisabled(!PropEventParameters.supports(type));
                if (ImGui.selectable(tr("type_"+type.name()),type == clip.getActionType())) clip.setActionType(type);
                ImGui.endDisabled();
            }
            ImGui.endCombo();
        }
        ImGui.textWrapped(tr("trigger_hint"));
        if (!clip.isTriggerOnce() && ImGui.button(tr("normalize_trigger"))) clip.setTriggerOnce(true);
        var params = clip.getParams(); var type = clip.getActionType();
        if (PropEventParameters.supports(type)) {
            if (type == EntityActionClip.ActionType.SWITCH_PROP) {
                slot(actor,type,params,"oldSlot",true); slot(actor,type,params,"newSlot",false); prop(params,"newPropId");
            } else {
                slot(actor,type,params,"slot",false);
                if (type == EntityActionClip.ActionType.ATTACH_PROP) prop(params,"propId");
            }
            if (type == EntityActionClip.ActionType.ATTACH_PROP || type == EntityActionClip.ActionType.SWITCH_PROP) {
                vector(params,"offset",tr("offset"),0); vector(params,"rotation",tr("rotation"),0);
                number(params,"propScale",tr("scale"),1);
                String pose=params.get("itemPose") instanceof String value?value:"FIXED";
                if (ImGui.beginCombo(tr("item_pose")+"##propItemPose",tr("pose_"+pose))) {
                    for (String choice : List.of("FIXED","HAND"))
                        if (ImGui.selectable(tr("pose_"+choice),choice.equals(pose))) params.put("itemPose",choice);
                    ImGui.endCombo();
                }
                ImGui.textWrapped(tr("scale_hint"));
                if (ImGui.button(tr("reset_visual")+"##resetPropVisual")) {
                    for (String key : List.of("rotationX","rotationY","rotationZ","propScale","itemPose")) params.remove(key);
                }
            }
            if (type == EntityActionClip.ActionType.THROW_PROP) {
                vector(params,"target",tr("target"),0);
                number(params,"arcHeight",tr("arc"),1.5f); number(params,"flightTime",tr("flight"),1);
            }
        }
        if (ImGui.collapsingHeader(tr("extras"))) {
            var known = Set.of("slot","oldSlot","newSlot","propId","newPropId","offsetX","offsetY","offsetZ",
                    "rotationX","rotationY","rotationZ","propScale","itemPose","targetX","targetY","targetZ","arcHeight","flightTime");
            params.entrySet().stream().filter(entry -> !known.contains(entry.getKey())).sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> ImGui.text(entry.getKey()+": "+entry.getValue()));
        }
        if (ImGui.button(tr("apply")+"##propEventApply")) {
            var edit = session;
            try { error = edit.commit() ? "" : tr("cancelled"); session = null; }
            catch (RuntimeException invalid) { error = invalid.getMessage(); }
        }
        ImGui.sameLine();
        if (ImGui.button(tr("cancel")+"##propEventCancel")) { if (session != null) session.cancel(); session = null; error = ""; }
        if (error != null && !error.isEmpty()) ImGui.textWrapped(error);
        return false;
    }
    private void slot(Actor actor,EntityActionClip.ActionType type,Map<String,Object> params,String key,boolean optional) {
        String current = params.get(key) instanceof String s ? s : "";
        if (!optional && !params.containsKey(key)) { current = ActorPropSlot.MAIN_HAND.name(); params.put(key,current); }
        String label = current.isEmpty() ? tr("none") : current;
        try { label = tr("slot_"+ActorPropSlot.valueOf(current).name()); } catch (IllegalArgumentException ignored) {}
        if (ImGui.beginCombo(tr(key)+"##propEvent"+key,label)) {
            if (optional && ImGui.selectable(tr("none"),current.isEmpty())) params.remove(key);
            for (var slot : ActorPropSlot.values()) {
                ImGui.beginDisabled(!PropEventParameters.supportsSlot(actor,slot,type));
                if (ImGui.selectable(tr("slot_"+slot.name()),current.equals(slot.name()))) params.put(key,slot.name());
                ImGui.endDisabled();
            }
            ImGui.endCombo();
        }
    }
    private void prop(Map<String,Object> params,String key) {
        var entries = PropRegistry.getInstance().getAll().stream().sorted(Comparator.comparing(PropDescriptor::getId)).toList();
        if (!params.containsKey(key) && !entries.isEmpty()) params.put(key,entries.getFirst().getId());
        String current = params.get(key) instanceof String s ? s : "";
        String label = PropRegistry.getInstance().get(current).map(PropDescriptor::getDisplayName).orElse(current);
        if (ImGui.beginCombo(tr(key)+"##propEvent"+key,label)) {
            for (var item : entries) if (ImGui.selectable(item.getDisplayName()+"##"+item.getId(),item.getId().equals(current))) params.put(key,item.getId());
            ImGui.endCombo();
        }
    }
    private void vector(Map<String,Object> params,String prefix,String label,float fallback) {
        float[] values = new float[3]; String[] axes = {"X","Y","Z"};
        for (int i=0;i<3;i++) values[i] = value(params,prefix+axes[i],fallback);
        float[] before = values.clone();
        boolean changed = ImGui.inputFloat3(label+"##propEvent"+prefix,values);
        for (int i=0;i<3;i++) if (changed && Float.compare(before[i],values[i]) != 0) params.put(prefix+axes[i],values[i]);
        if (prefix.equals("target")) for (int i=0;i<3;i++) params.putIfAbsent(prefix+axes[i],values[i]);
    }
    private void number(Map<String,Object> params,String key,String label,float fallback) {
        var value = new ImFloat(value(params,key,fallback));
        if (ImGui.inputFloat(label+"##propEvent"+key,value)) params.put(key,value.get());
    }
    private float value(Map<String,Object> params,String key,float fallback) {
        return params.get(key) instanceof Number value ? value.floatValue() : fallback;
    }
}
