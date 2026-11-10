package com.director.ui.timeline.render;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.actor.pose.ActorRootKeyframeEditing;
import imgui.ImGui;
import imgui.type.ImInt;
import java.util.List;
import org.joml.Vector3f;

/** Selected root keys are distinct from the playhead and from bone-local keys. */
public final class ActorRootKeyPropertyUI {
    public static void leave(){ActorRootTimelineRows.leaveInspection();}
    record Selection(TimelineTrack owner,EntityClip clip,List<EntityClipKeyframe> keys,int mask) {}
    private static EntityClipKeyframe editing,snapshot;
    private static final float[][] values=new float[3][3];
    private static int interpolation;
    private static String message="";
    public static boolean render(TimelineModel model) {
        if(model==null) return false;
        var selection=ActorRootTimelineRows.inspection(model);
        if(selection==null) return false;
        render(model,selection);return true;
    }
    public static void renderExisting(TimelineModel model,TimelineTrack owner,EntityClip clip,EntityClipKeyframe key) {
        render(model,new Selection(owner,clip,List.of(key),key.getChannels()));
    }
    private static void render(TimelineModel model,Selection s) {
        ImGui.text(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.root_title", s.keys().size()));ImGui.textWrapped(s.owner().getName()+" · "+s.clip().getName());
        ImGui.textWrapped(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.root_hint"));
        if(ImGui.button(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.back"))) {
            ActorRootTimelineRows.leaveInspection();model.selectItem(s.clip().getTimelineClip(),false);
            com.director.core.DirectorServices.get().uiSelection().setCurrentSelection(s.clip().getTimelineClip());return;
        }
        boolean writable=ActorRootKeyframeEditing.editable(model,s.owner(),s.clip())
                && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                && !com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        if(!writable) ImGui.textWrapped(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.root_readonly"));
        if(s.keys().size()==1) {
            var key=s.keys().getFirst();
            if(editing!=key || snapshot==null || !snapshot.getPosition().equals(key.getPosition()) || !snapshot.getRotation().equals(key.getRotation())
                    || !snapshot.getScale().equals(key.getScale()) || snapshot.getTime()!=key.getTime() || snapshot.getInterpolation()!=key.getInterpolation()) {
                editing=key;snapshot=key.copy();interpolation=key.getInterpolation().ordinal();
                var v=List.of(key.getPosition(),key.getRotation(),key.getScale());
                for(int i=0;i<3;i++){values[i][0]=v.get(i).x;values[i][1]=v.get(i).y;values[i][2]=v.get(i).z;}message="";
            }
            ImGui.text(String.format("所选键：%.4f 秒",key.getTime()));
            int mask=s.mask()&key.getChannels();
            ImGui.beginDisabled(!writable);
            String[] labels={"世界位置 XYZ","整体朝向（偏航、俯仰、翻滚）","整体缩放 XYZ"};
            boolean route=com.director.ui.actor.pose.ActorMovementSource.routeOwnsInterval(model,s.owner());
            for(int i=0;i<3;i++) if((mask&(1<<i))!=0) {ImGui.beginDisabled(route&&i<2);ImGui.inputFloat3(labels[i],values[i]);ImGui.endDisabled();}
            ImGui.beginDisabled(mask!=key.getChannels());
            var mode=new ImInt(interpolation);if(ImGui.combo(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.outgoing"),mode,java.util.Arrays.stream(TimelineInterpolation.values()).map(TimelineInterpolation::getDisplayName).toArray(String[]::new))) interpolation=mode.get();
            ImGui.endDisabled();
            if(ImGui.button(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.root_apply"))) {
                int allowed=route?mask&4:mask;
                message=ActorRootKeyframeEditing.updateExisting(model,s.owner(),s.clip(),key,snapshot,allowed,new Vector3f(values[0]),new Vector3f(values[1]),new Vector3f(values[2]),TimelineInterpolation.values()[interpolation])?"已应用，可撤销。":"目标已变化或通道不可编辑。";
            }
            ImGui.endDisabled();
        } else ImGui.textWrapped("整组时间调整可使用时间线拖动；数值编辑请明确选择单个键。");
        ImGui.beginDisabled(!writable);
        if(ImGui.button(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.key_inspector.root_delete"))) {
            ActorRootKeyframeEditing.deleteChannels(model,s.owner(),s.clip(),s.keys(),s.mask());ActorRootTimelineRows.leaveInspection();
        }
        ImGui.endDisabled();if(!message.isEmpty()) ImGui.textWrapped(message);
    }
}
