package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationLoopMode;
import imgui.ImGui;
import imgui.type.ImFloat;
import java.util.*;

/** Explicit Apply/Cancel, independently scoped to each editor view. */
public final class ActorKeyframeTimePanel {
    private ActorKeyframeTimeDraft draft;
    private final ImFloat value=new ImFloat();
    private List<ActorAnimationAuthoringService.Key> shown=List.of();
    private ActorAnimationEditContext shownContext;
    private boolean rejected;
    public void cancel() { draft=null; shownContext=null; shown=List.of(); rejected=false; }
    private static String tr(String key) { return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys."+key); }
    public Float render(ActorAnimationEditContext context,Collection<ActorAnimationAuthoringService.Key> selection,boolean enabled) {
        var keys=new ArrayList<>(new LinkedHashSet<>(selection));
        if(!Objects.equals(shownContext,context) || !new LinkedHashSet<>(shown).equals(new LinkedHashSet<>(keys))
                || (draft!=null && !draft.matches(context,keys))) {
            draft=null; rejected=false; shown=List.copyOf(keys); shownContext=context;
            value.set(keys.size()==1?keys.getFirst().time():0);
        }
        boolean valid=enabled && context!=null && context.isCurrent() && !keys.isEmpty()
                && (!context.loop() || context.loopMode()==AnimationLoopMode.ONCE);
        ImGui.beginDisabled(!valid);
        Float moved=null;
        try {
            if(ImGui.inputFloat(tr(keys.size()==1?"absolute_time":"time_offset"),value,.01f,.1f,"%.4f")) {
                if(draft==null) draft=new ActorKeyframeTimeDraft(context,keys);
                rejected=false;
            }
            ImGui.beginDisabled(draft==null || Math.abs(draft.delta(value.get()))<=1.0e-5f);
            boolean apply=ImGui.button(tr("apply_time")); ImGui.endDisabled(); ImGui.sameLine();
            ImGui.beginDisabled(draft==null);
            boolean cancel=ImGui.button(tr("cancel_time")); ImGui.endDisabled();
            if(apply && draft!=null) {
                float delta=draft.delta(value.get());
                if(draft.apply(value.get())) { moved=delta; draft=null; rejected=false; }
                else rejected=true;
            }
            if(cancel) { cancel(); value.set(keys.size()==1?keys.getFirst().time():0); }
        } finally { ImGui.endDisabled(); }
        ImGui.textWrapped(tr("time_hint"));
        if(rejected) ImGui.textWrapped(tr("rejected"));
        return moved;
    }
}
