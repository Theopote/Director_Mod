package com.director.ui.properties.actor;
import com.director.core.DirectorServices;
import com.director.actor.runtime.ActorRuntimeController;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.timeline.model.entity.EntityClip;
import imgui.ImGui;
import java.util.UUID;

/** Read-only runtime feedback shared by actor and clip property inspectors. */
public final class ActorRuntimeDiagnosticsUI {
    private ActorRuntimeDiagnosticsUI() {}
    public static void render(EntityClip clip) {
        if(clip==null) return;
        if(!clip.getLoadWarnings().isEmpty()) {
            ImGui.textWrapped("加载时已恢复此片段的部分混合参数：");
            for(var warning:clip.getLoadWarnings()) ImGui.textWrapped(warning);
        }
        if(clip.getTimelineClip()==null || clip.getTimelineClip().getParentTrack()==null) return;
        var payload=clip.getTimelineClip().getParentTrack().getActorTrackPayload();
        if(payload!=null && payload.getActorRef()!=null) render(payload.getActorRef().getActorId());
    }
    public static void render(UUID actor) {
        var model=DirectorServices.get().containerTimeline().getTimelineModel();
        if(model==null || actor==null) return;
        var tracks=model.getAllTracks().stream().filter(t->t.getActorTrackPayload()!=null
                && t.getActorTrackPayload().getActorRef()!=null
                && actor.equals(t.getActorTrackPayload().getActorRef().getActorId()) && !t.isMuted()).toList();
        var issues=ActorRuntimeController.getInstance().diagnostics().current(actor,model.getCurrentTime(),tracks);
        if(issues.isEmpty()) return;
        ImGui.textWrapped(tr("title"));
        for(var issue:issues) {
            String status=tr(issue.hidden()?"hidden":"limited");
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.diagnostics.issue",status,tr(issue.stage()),issue.sampleTime(),issue.reason()==null?issue.message():ActorPropertyAdapterI18n.tr(issue.reason().translationKey())));
        }
    }
    private static String tr(String key) { return ActorPropertyAdapterI18n.tr("gui.director.actor.diagnostics."+key); }
}
