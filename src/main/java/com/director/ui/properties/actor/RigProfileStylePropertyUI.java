package com.director.ui.properties.actor;

import com.director.actor.Actor;
import com.director.ui.actor.ActorAppearanceEditing;
import imgui.ImGui;
import java.util.Objects;

/** Explicit detached style form: one Apply is one undo command. */
public final class RigProfileStylePropertyUI {
    private static Actor owner;
    private static Object modelScope;
    private static ActorAppearanceEditing.Style before;
    private static float[] values;
    private static String message="";
    public static boolean render(Actor actor) {
        if(actor==null || actor.getRigProfile()==null) return false;
        var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        var current=ActorAppearanceEditing.Style.capture(actor.getRigProfile().getStyle());
        if(owner!=actor || modelScope!=model || !Objects.equals(before,current)) {
            owner=actor;modelScope=model;before=current;
            values=new float[]{current.motion(),current.tempo(),current.lean(),current.arms(),current.legs()};message="";
        }
        if(!ImGui.collapsingHeader("演员动作风格")) return false;
        ImGui.textWrapped("影响整个演员的基础动作风格，不改变骨骼动画资产。修改后点击应用，可撤销。");
        ImGui.beginDisabled(!ActorAppearanceEditing.editable(model));
        String[] labels={"动作幅度","动作节奏","身体前倾","手臂幅度","腿部幅度"};
        float[] min={.5f,.7f,.5f,.3f,.3f},max={1.5f,1.3f,1.5f,1.5f,1.5f};
        for(int i=0;i<5;i++) {float[] value={values[i]};if(ImGui.sliderFloat(labels[i],value,min[i],max[i])) values[i]=value[0];}
        if(ImGui.button("应用风格")) {
            try {ActorAppearanceEditing.style(model,actor,before,new ActorAppearanceEditing.Style(values[0],values[1],values[2],values[3],values[4]));message="已应用，可撤销。";}
            catch(RuntimeException error){message=error.getMessage();}
        }
        ImGui.sameLine();if(ImGui.button("取消风格修改")) before=null;
        ImGui.endDisabled();if(!message.isEmpty()) ImGui.textWrapped(message);
        return false;
    }
}
