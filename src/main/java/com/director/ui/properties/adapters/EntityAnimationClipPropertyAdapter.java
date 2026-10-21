package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.*;
import com.director.timeline.model.entity.EntityAnimationClip;
import imgui.ImGui;
import imgui.type.*;

public class EntityAnimationClipPropertyAdapter implements IPropertyAdapter<EntityAnimationClip> {
    private final ActorClipInspectorGesture gesture=new ActorClipInspectorGesture();
    @Override public Class<EntityAnimationClip> getTargetType() { return EntityAnimationClip.class; }
    @Override public boolean renderProperties(EntityAnimationClip source) {
        ActorRuntimeDiagnosticsUI.render(source);
        var draft=gesture.begin(source);
        if(!(draft instanceof EntityAnimationClip clip)) { gesture.end(); return false; }
        ImGui.text("骨骼动画片段");
        ImGui.text(String.format("时间范围：%.2f – %.2f 秒",clip.getStartTime(),clip.getEndTime()));
        var sources=new EntityAnimationClip.AnimationSource[]{EntityAnimationClip.AnimationSource.BUILT_IN,EntityAnimationClip.AnimationSource.CUSTOM};
        var index=new ImInt(clip.getSource()==sources[1]?1:0);
        boolean changed=ImGui.combo("动画源",index,new String[]{"内建","自定义"});
        if(changed) clip.setSource(sources[index.get()]); gesture.observe("source",changed);
        var id=new ImString(clip.getAnimationId(),256);
        changed=ImGui.inputText("动画 ID",id); if(changed) clip.setAnimationId(id.get()); gesture.observe("id",changed);
        changed=ImGui.checkbox("循环播放",clip.isLoop()); if(changed) clip.setLoop(!clip.isLoop()); gesture.observe("loop",changed);
        float[] speed={clip.getSpeed()}; changed=ImGui.sliderFloat("播放速度",speed,0,4);
        if(changed) clip.setSpeed(speed[0]); gesture.observe("speed",changed);
        ActorAnimationBlendPropertyUI.render(source,clip.getBlendSettings(),clip::setBlendSettings,gesture);
        gesture.end(); return false;
    }
}
