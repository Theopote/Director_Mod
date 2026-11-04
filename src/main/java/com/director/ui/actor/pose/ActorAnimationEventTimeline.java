package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.AnimationEvent;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.playback.entity.AnimationEventClock;
import com.director.ui.timeline.render.TimelineColors;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Scene-time event strip: source-clock events display every occurrence, including loops. */
public final class ActorAnimationEventTimeline {
    private Drag drag;
    private String message = "";
    private EntityAnimationClip shownClip;
    record Occurrence(AnimationEvent event,float time) {}
    record DraftSeed(AnimationEvent.Timing timing, float phase) {}
    private record Viewport(float start, float end, float x, float y, float width) {}
    private record Drag(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip,
                        long revision, Viewport viewport, List<AnimationEvent> before,
                        int index, float occurrence, float mouseX) {
        boolean matches(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip, Viewport viewport) {
            return this.model == model && this.owner == owner && this.clip == clip
                    && revision == model.getActorEditContextRevision() && this.viewport.equals(viewport)
                    && before.equals(clip.getEvents());
        }
    }

    void cancel() {
        drag = null;
    }

    static List<Occurrence> occurrences(TimelineTrack owner,EntityAnimationClip clip,float from,float to) {
        if(owner==null || owner.getActorTrackPayload()==null || clip==null)return List.of();
        var clock=AnimationEventClock.projection(owner.getActorTrackPayload(),clip,from,to);
        return occurrences(clip, clock);
    }

    private static List<Occurrence> occurrences(EntityAnimationClip clip, AnimationEventClock.Projection clock) {
        var result=new ArrayList<Occurrence>();
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

    static boolean canSeek(TimelineModel model) {
        return model!=null && !model.isPlaying() && !model.isScrubbing()
                && !ActorBonePropertiesPanel.hasNumericDraft() && !ActorBoneGizmoController.getInstance().hasPendingDraft()
                && !ActorBoneGizmoController.getInstance().isDragging()
                && !ActorAnimationDopeSheet.isKeyInteractionActive()
                && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                && !com.director.export.video.VideoExportManager.getInstance().isExporting();
    }

    static Optional<DraftSeed> draftAt(AnimationEventClock.Projection clock, float time, float laneY) {
        if (laneY < 20 || laneY >= 56) return Optional.empty();
        var timing = laneY < 38 ? AnimationEvent.Timing.CLIP : AnimationEvent.Timing.ANIMATION;
        return clock.phaseAt(timing, time).map(phase -> new DraftSeed(timing, phase));
    }

    void render(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip, Consumer<DraftSeed> openDraft) {
        if (shownClip != clip) {
            cancel();
            message = "";
            shownClip = clip;
        }
        float start=clip.getStartTime(),length=clip.getLength();
        if(!Float.isFinite(length) || length<=0) { cancel(); return; }
        float x=ImGui.getCursorScreenPosX(),y=ImGui.getCursorScreenPosY();
        float width=Math.max(140,ImGui.getContentRegionAvailX()),timeX=x+72,timeWidth=Math.max(1,width-80);
        var viewport = new Viewport(start, clip.getEndTime(), timeX, y, timeWidth);
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
        var payload = owner == null ? null : owner.getActorTrackPayload();
        var clock = payload == null ? null : AnimationEventClock.projection(payload, clip, start, clip.getEndTime());
        List<Occurrence> markers = clock == null ? List.of() : occurrences(clip, clock);
        Occurrence hit=null;float hitDistance=7;
        for(var occurrence:markers) {
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
            boolean editable = ActorAnimationEventEditing.editable(model, owner, clip);
            ImGui.setTooltip(hit.event().getAction().type().getDisplayName()
                    + String.format(" · %.3fs\n点击定位%s", hit.time(), editable ? "，拖动调整时间" : ""));
            if (canSeek(model) && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                message = "";
                if (editable) {
                    var events = clip.getEvents();
                    for (int index = 0; index < events.size(); index++) {
                        if (events.get(index) != hit.event()) continue;
                        drag = new Drag(model, owner, clip, model.getActorEditContextRevision(), viewport,
                                ActorAnimationEventEditing.copy(events), index, hit.time(), ImGui.getMousePosX());
                        break;
                    }
                } else model.setCurrentTime(hit.time());
            }
        }
        if (clock == null) cancel();
        else if (drag != null) updateDrag(model, owner, clip, viewport, hovered, draw, clock);
        else if (hit == null && hovered && canSeek(model) && ActorAnimationEventEditing.editable(model, owner, clip)) {
            float localX = ImGui.getMousePosX() - timeX;
            float laneY = ImGui.getMousePosY() - y;
            if (localX >= 0 && localX <= timeWidth && laneY >= 20 && laneY < 56) {
                ImGui.setTooltip(ActorAnimationEventPanel.tr("double_click_add"));
                if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                    float target = Math.clamp(start + localX / timeWidth * length, start, clip.getEndTime());
                    var seed = draftAt(clock, target, laneY);
                    message = seed.isPresent() ? "" : ActorAnimationEventPanel.tr("unreachable");
                    seed.ifPresent(openDraft);
                }
            }
        }
        if (!message.isEmpty()) ImGui.textWrapped(message);
        else if (markers.isEmpty() && ActorAnimationEventEditing.editable(model, owner, clip))
            ImGui.textWrapped(ActorAnimationEventPanel.tr("double_click_add"));
    }

    private void updateDrag(TimelineModel model, TimelineTrack owner, EntityAnimationClip clip,
                            Viewport viewport, boolean hovered, imgui.ImDrawList draw,
                            AnimationEventClock.Projection clock) {
        boolean released = ImGui.isMouseReleased(ImGuiMouseButton.Left);
        if (!drag.matches(model, owner, clip, viewport) || !canSeek(model)
                || !ActorAnimationEventEditing.editable(model, owner, clip) || ImGui.isKeyPressed(ImGuiKey.Escape)
                || (!released && (!ImGui.isMouseDown(ImGuiMouseButton.Left) || !ImGui.isItemActive()))) {
            cancel();
            return;
        }

        var gesture = drag;
        float dx = ImGui.getMousePosX() - gesture.mouseX();
        boolean moved = ImGui.isMouseDragging(ImGuiMouseButton.Left, 4f) || Math.abs(dx) >= 4f;
        if (released) {
            cancel();
            if (!hovered) return;
            if (!moved) {
                model.setCurrentTime(gesture.occurrence());
                return;
            }
        }
        if (!moved) return;

        float length = viewport.end() - viewport.start();
        float target = Math.clamp(gesture.occurrence() + dx / viewport.width() * length,
                viewport.start(), viewport.end());
        var event = gesture.before().get(gesture.index());
        var phase = clock.phaseAt(event.getTiming(), target);
        var nearest = phase.flatMap(value -> clock.times(new AnimationEvent(value, event.getAction(), event.getTiming()))
                .stream().min(Comparator.comparingDouble(time -> Math.abs(time - target))));
        if (nearest.isEmpty()) {
            ImGui.setTooltip("该位置没有对应的事件时刻");
            if (released) message = "该位置没有对应的事件时刻";
            return;
        }

        if (released) {
            try {
                if (ActorAnimationEventEditing.move(model, owner, clip, gesture.before(), gesture.index(), phase.orElseThrow()))
                    model.setCurrentTime(nearest.orElseThrow());
            } catch (IllegalArgumentException failure) {
                message = failure.getMessage();
            }
            return;
        }
        float markerX = viewport.x() + (nearest.orElseThrow() - viewport.start()) / length * viewport.width();
        float markerY = viewport.y() + (event.getTiming() == AnimationEvent.Timing.CLIP ? 29 : 47);
        draw.addTriangleFilled(markerX, markerY - 6, markerX - 6, markerY + 5, markerX + 6, markerY + 5,
                TimelineColors.rgba(100, 220, 255, 255));
        ImGui.setTooltip(String.format("%.3fs · %.3f%s", nearest.orElseThrow(), phase.orElseThrow(),
                event.getTiming() == AnimationEvent.Timing.ANIMATION ? "\n同一事件的各次触发会一起移动" : ""));
    }
}
