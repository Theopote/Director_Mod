package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineModel;
import com.director.actor.animation.asset.*;
import imgui.ImGui;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Inspects authored keys, separately from the evaluated pose at the playhead. */
public final class ActorKeySelectionPropertyUI {
    private static final ActorKeyframeTimePanel timePanel=new ActorKeyframeTimePanel();
    private static final ActorKeyframeInterpolationPanel interpolationPanel = new ActorKeyframeInterpolationPanel();
    private static ActorAnimationEditContext editing;
    private static ActorAnimationAuthoringService.Key editingKey;
    private static Object original;
    private static final float[] value=new float[3];
    private static String message="";
    private ActorKeySelectionPropertyUI() {}
    public static boolean render(TimelineModel model) {
        if(model==null) return false;
        var state=ActorKeyframeSelection.getInstance().snapshot(model);
        if(state==null || !state.focused() || state.keys().isEmpty()) {editing=null;return false;}
        var context=ActorAnimationEditContext.capture(model,state.owner(),state.clip());
        ImGui.text("骨骼关键帧 · "+state.keys().size()+" 个");
        ImGui.textWrapped(state.owner().getName()+" · "+state.asset().name());
        ImGui.textWrapped("这里显示动画资产中的原始键值，场景中的混合结果可能不同。时间单位为资产秒。");
        if(ImGui.button("返回所属片段")) {
            ActorKeyframeSelection.getInstance().replace(model,state.owner(),state.clip(),state.asset(),state.keys(),false);
            editing=null;return true;
        }
        boolean writable=context!=null && context.isCurrent() && !ActorBonePropertiesPanel.hasNumericDraft()
                && !ActorBoneGizmoController.getInstance().isDragging();
        if(!writable) ImGui.textWrapped("只读：播放、锁定、禁用或当前草稿阻止修改。");
        Float delta=timePanel.render(context,state.keys(),writable);
        if(delta!=null) {
            var moved=state.keys().stream().map(k->new ActorAnimationAuthoringService.Key(k.boneId(),k.channel(),k.time()+delta)).toList();
            ActorKeyframeSelection.getInstance().replace(model,state.owner(),state.clip(),state.asset(),moved,true);
            return true;
        }
        interpolationPanel.render(context,state.keys(),writable);
        if(state.keys().size()==1) {
            var key=state.keys().getFirst();var track=state.asset().findBoneTrack(key.boneId()).orElseThrow();
            ChannelKeyframe<?> frame=switch(key.channel()) {
                case TRANSLATION->track.translation().findAt(key.time()).orElseThrow();
                case ROTATION->track.rotation().findAt(key.time()).orElseThrow();
                case SCALE->track.scale().findAt(key.time()).orElseThrow();
            };
            Object current=frame.value();
            if(!Objects.equals(editing,context)||!Objects.equals(editingKey,key)||!Objects.equals(original,current)) {
                editing=context;editingKey=key;original=current;
                var v=current instanceof Quaternionf q?q.getEulerAnglesXYZ(new Vector3f()).mul((float)(180/Math.PI)):new Vector3f((Vector3f)current);
                value[0]=v.x;value[1]=v.y;value[2]=v.z;message="";
            }
            ImGui.text(key.boneId()+" · "+key.channel());
            ImGui.beginDisabled(!writable);
            ImGui.inputFloat3(key.channel()==ActorAnimationAuthoringService.Channel.ROTATION?"局部旋转 XYZ（度）":"局部 XYZ",value);
            if(ImGui.button("应用关键帧数值")) {
                Object next=key.channel()==ActorAnimationAuthoringService.Channel.ROTATION
                    ?new Quaternionf().rotationXYZ((float)Math.toRadians(value[0]),(float)Math.toRadians(value[1]),(float)Math.toRadians(value[2]))
                    :new Vector3f(value);
                message=ActorAnimationAuthoringService.setValue(context,key,next)?"已应用，可撤销。":"数值无效或目标已变化。";
            }
            ImGui.endDisabled();
        } else ImGui.textWrapped("多选键可整组调整时间或插值；不同部位的局部值不作隐式合并。");
        ImGui.beginDisabled(!writable);
        if(ImGui.button("删除所选骨骼关键帧")) {
            if(ActorAnimationAuthoringService.delete(context,state.keys()))
                ActorKeyframeSelection.getInstance().replace(model,state.owner(),state.clip(),state.asset(),List.of(),false);
        }
        ImGui.endDisabled();if(!message.isEmpty()) ImGui.textWrapped(message);
        return true;
    }
}
