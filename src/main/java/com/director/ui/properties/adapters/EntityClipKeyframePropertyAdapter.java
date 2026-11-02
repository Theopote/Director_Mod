package com.director.ui.properties.adapters;
import com.director.ui.properties.IPropertyAdapter;
import com.director.timeline.model.entity.EntityClipKeyframe;
import imgui.ImGui;
/** Legacy selection payloads use the same checked root-key editor. */
public class EntityClipKeyframePropertyAdapter implements IPropertyAdapter<EntityClipKeyframe> {
    @Override public Class<EntityClipKeyframe> getTargetType(){return EntityClipKeyframe.class;}
    @Override public boolean renderProperties(EntityClipKeyframe key) {
        var model=com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        if(model!=null) for(var owner:model.getAllTracks()) if(owner.getActorTrackPayload()!=null)
            for(var clip:owner.getActorTrackPayload().getClips()) if(clip.getKeyframes().contains(key)) {
                if(clip instanceof com.director.timeline.model.entity.EntitySpawnClip || clip instanceof com.director.timeline.model.entity.EntityTransformClip)
                    com.director.ui.timeline.render.ActorRootKeyPropertyUI.renderExisting(model,owner,clip,key);
                else ImGui.textWrapped("此关键帧不属于整体变换，请在所属动作片段中编辑。");
                return false;
            }
        ImGui.textWrapped("关键帧已移除或不属于当前工程。");return false;
    }
}
