package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import java.util.UUID;

final class ActorBoneInfluenceUI {
    private ActorBoneInfluenceUI() {}
    static void render(TimelineModel model,UUID actorId,RigDefinition rig,String bone,boolean editing) {
        var context=ActorAnimationEditContext.resolve(model,actorId);
        if(context==null || bone==null) return;
        var settings=context.clip().getBlendSettings();
        if(context.track().isMuted()) ImGui.textWrapped("演员轨道已静音，以下配置不会参与当前播放。");
        ImGui.textDisabled("所选动画引用的配置影响（最终姿态还受约束影响）");
        for(int channel:new int[]{RigPose.TRANSLATION,RigPose.ROTATION,RigPose.SCALE}) {
            if(!ActorBoneInspector.supports(rig,bone,channel)) continue;
            var state=ActorBoneInfluence.evaluate(context.asset(),settings,bone,channel,
                    model.getCurrentTime()-context.clip().getStartTime(),context.clip().getLength());
            String reason=switch(state.reason()) {
                case ACTIVE -> "已有关键帧";
                case NO_KEYS -> "尚无关键帧；编辑后按此权重混合";
                case BONE_EXCLUDED -> "当前部位被混合范围排除";
                case CHANNEL_EXCLUDED -> "此通道被混合范围排除";
                case ZERO_WEIGHT -> "动作权重为 0";
                case FADE_ZERO -> "当前过渡权重为 0 或播放头在范围外";
            };
            ImGui.textWrapped(String.format(java.util.Locale.ROOT,"%s：配置 %.1f%% · %s",ActorBoneInspector.label(channel),state.configuredWeight()*100,reason));
        }
        var spawn=ActorEmbeddedAnimation.target(model,context.track());
        if(spawn!=null && spawn.getPreset()!=null) {
            ImGui.beginDisabled(editing || spawn.getTimelineClip()==null);
            if(ImGui.smallButton("查看本片段预设设置##locatePresetLayer")) model.selectItem(spawn.getTimelineClip(),false);
            ImGui.endDisabled();
            ImGui.textDisabled("预设配置与手工关键帧独立；这里的数值修改写入上述动画资产。");
        }
        var owner=ActorEmbeddedAnimation.visibleOwner(context.track(),context.clip());
        if(owner!=null) {
            ImGui.beginDisabled(editing || !model.getAllTracks().contains(context.track()));
            if(ImGui.smallButton("在时间线选中混合片段##locateBoneBlend")) model.selectItem(owner,false);
            ImGui.endDisabled();
            if(editing) ImGui.textDisabled("先应用或取消当前草稿，再定位片段。");
        }
    }
}
