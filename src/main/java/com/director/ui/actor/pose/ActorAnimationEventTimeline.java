package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.AnimationEvent;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.playback.entity.AnimationEventClock;
import com.director.ui.timeline.render.TimelineColors;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Scene-time event strip: source-clock events display every occurrence, including loops. */
public final class ActorAnimationEventTimeline {
    private ActorAnimationEventTimeline() {}
    record Occurrence(AnimationEvent event,float time) {}

    static List<Occurrence> occurrences(TimelineTrack owner,EntityAnimationClip clip,float from,float to) {
        if(owner==null || owner.getActorTrackPayload()==null || clip==null)return List.of();
        var result=new ArrayList<Occurrence>();
        var clock=AnimationEventClock.projection(owner.getActorTrackPayload(),clip,from,to);
        for(var event:clip.getEvents()) for(float time:clock.times(event))
            result.add(new Occurrence(event,time));
        result.sort(Comparator.comparingDouble(Occurrence::time));
        return List.copyOf(result);
    }

    public static boolean seek(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip,AnimationEvent event) {
        if(!canSeek(model))return false;
        if(owner==null || owner.getActorTrackPayload()==null)return false;
        var time=AnimationEventClock.times(owner.getActorTrackPayload(),clip,event,clip.getStartTime(),clip.getEndTime()).stream()
                .min(Comparator.comparingDouble(t->Math.abs(t-model.getCurrentTime())));
        time.ifPresent(model::setCurrentTime);
        return time.isPresent();
    }

    private static boolean canSeek(TimelineModel model) {
        return model!=null && !model.isPlaying() && !model.isScrubbing()
                && !ActorBonePropertiesPanel.hasNumericDraft() && !ActorBoneGizmoController.getInstance().hasPendingDraft()
                && !ActorBoneGizmoController.getInstance().isDragging()
                && !ActorAnimationDopeSheet.isKeyInteractionActive()
                && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                && !com.director.export.video.VideoExportManager.getInstance().isExporting();
    }

    public static void render(TimelineModel model,TimelineTrack owner,EntityAnimationClip clip) {
        if(clip.getEvents().isEmpty()) { ImGui.textDisabled("事件：尚无事件，可在动画事件面板添加"); return; }
        float start=clip.getStartTime(),length=clip.getLength();
        if(!Float.isFinite(length) || length<=0)return;
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY();
        float width=Math.max(140,ImGui.getContentRegionAvailX()),timeX=x+72,timeWidth=Math.max(1,width-80);
        ImGui.invisibleButton("##actorEventTimeline",width,58);
        boolean hovered=ImGui.isItemHovered();
        var draw=ImGui.getWindowDrawList();
        draw.addRectFilled(x,y,x+width,y+58,TimelineColors.rgba(24,27,32,255),3);
        draw.addText(x+4,y+3,TimelineColors.COLOR_TEXT_LIGHT,"事件 / 秒");
        draw.addText(x+4,y+23,TimelineColors.COLOR_TEXT_LIGHT,"片段");
        draw.addText(x+4,y+41,TimelineColors.COLOR_TEXT_LIGHT,"动画源");
        for(int tick=0;tick<=4;tick++) {
            float tx=timeX+timeWidth*tick/4;
            draw.addLine(tx,y+17,tx,y+58,TimelineColors.rgba(65,70,80,120));
            draw.addText(tick==4?tx-32:tx,y,TimelineColors.COLOR_TEXT_LIGHT,String.format("%.2f",start+length*tick/4));
        }
        Occurrence hit=null;float hitDistance=7;
        for(var occurrence:occurrences(owner,clip,start,clip.getEndTime())) {
            float px=timeX+(occurrence.time()-start)/length*timeWidth;
            float cy=y+(occurrence.event().getTiming()==AnimationEvent.Timing.CLIP?29:47);
            draw.addTriangleFilled(px,cy-5,px-5,cy+4,px+5,cy+4,TimelineColors.rgba(255,190,70,255));
            float distance=Math.abs(ImGui.getMousePosX()-px);
            if(hovered && Math.abs(ImGui.getMousePosY()-cy)<=7 && distance<hitDistance) {
                hit=occurrence;hitDistance=distance;
            }
        }
        float playhead=timeX+(model.getCurrentTime()-start)/length*timeWidth;
        if(playhead>=timeX && playhead<=timeX+timeWidth)
            draw.addLine(playhead,y+17,playhead,y+58,TimelineColors.rgba(255,90,80,230),1.5f);
        if(hit!=null) {
            ImGui.setTooltip(hit.event().getAction().type().getDisplayName()+String.format(" · %.3fs\n点击定位",hit.time()));
            if(canSeek(model) && ImGui.isMouseClicked(ImGuiMouseButton.Left)) model.setCurrentTime(hit.time());
        }
    }
}
