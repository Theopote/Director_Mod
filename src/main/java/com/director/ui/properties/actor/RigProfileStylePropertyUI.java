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
        if(!ImGui.collapsingHeader(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.title"))) return false;
        ImGui.textWrapped(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.hint"));
        ImGui.beginDisabled(!ActorAppearanceEditing.editable(model));
        String[] labels={
                com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.motion"),
                com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.tempo"),
                com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.lean"),
                com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.arms"),
                com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.legs")};
        float[] min={.5f,.7f,.5f,.3f,.3f},max={1.5f,1.3f,1.5f,1.5f,1.5f};
        for(int i=0;i<5;i++) {float[] value={values[i]};if(ImGui.sliderFloat(labels[i]+"##style"+i,value,min[i],max[i])) values[i]=value[0];}
        if(ImGui.button(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.apply"))) {
            try {ActorAppearanceEditing.style(model,actor,before,new ActorAppearanceEditing.Style(values[0],values[1],values[2],values[3],values[4]));message=com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.applied");}
            catch(RuntimeException error){message=error.getMessage();}
        }
        ImGui.sameLine();if(ImGui.button(com.director.i18n.ActorPropertyAdapterI18n.tr("gui.director.actor.style.cancel"))) before=null;
        ImGui.endDisabled();if(!message.isEmpty()) ImGui.textWrapped(message);
        return false;
    }
}
