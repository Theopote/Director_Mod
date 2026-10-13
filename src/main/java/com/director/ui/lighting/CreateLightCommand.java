package com.director.ui.lighting;
import com.director.lighting.*;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.BaseTimelineCommand;
/** Scene geometry and its initial schedule are one undoable operation. */
public final class CreateLightCommand extends BaseTimelineCommand {
    private final PointLight light;
    private final TimelineTrack track;
    private final LightTimelineClip clip;
    public CreateLightCommand(TimelineModel model,PointLight light) {
        super(model,"light_created");this.light=light;
        track=new TimelineTrack(light.name(),TrackType.LIGHT);
        float start=Math.max(0,model.getCurrentTime());
        clip=new LightTimelineClip(light.name(),start,Math.max(start+5,model.getDuration()),light.id());
    }
    @Override protected void doExecute() {
        model.replacePointLight(light.id(),light);track.addClip(clip);model.addTrack(track);
    }
    @Override protected void doUndo() {
        model.removeTrack(track);track.removeClip(clip);model.replacePointLight(light.id(),null);
    }
    @Override public String getDescription() { return "Create light and clip"; }
    public LightTimelineClip clip() { return clip; }
}
