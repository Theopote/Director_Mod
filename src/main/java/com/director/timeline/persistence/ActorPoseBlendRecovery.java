package com.director.timeline.persistence;

import com.director.actor.rig.skeleton.*;
import com.google.gson.*;
import java.util.*;

/** Recover only the optional blend settings; never remove the owning actor clip. */
final class ActorPoseBlendRecovery {
    record Result(PoseBlendSettings settings,List<String> issues) {
        Result { issues=List.copyOf(issues); }
    }
    private final JsonObject data;
    private final List<String> issues=new ArrayList<>();
    private ActorPoseBlendRecovery(JsonObject data) { this.data=data; }
    static Result read(JsonObject data) {
        if(data.size()==0) return new Result(PoseBlendSettings.DEFAULT,List.of());
        return new ActorPoseBlendRecovery(data).read();
    }
    private Result read() {
        PoseLayer.BlendMode mode=PoseLayer.BlendMode.OVERRIDE; boolean invalidMode=false;
        var raw=data.get("poseBlendMode");
        if(raw!=null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()) {
            try { mode=PoseLayer.BlendMode.valueOf(raw.getAsString()); }
            catch(IllegalArgumentException bad) { invalidMode=true; }
        } else invalidMode=raw!=null && !raw.isJsonNull();
        if(invalidMode) issue("poseBlendMode","无效模式，已暂停此动作层（权重 0）");
        else if(raw==null || raw.isJsonNull()) issue("poseBlendMode","缺失，使用覆盖模式");
        float weight=number("poseBlendWeight",1,0,0,1);
        int channels=channels();
        boolean allBones=allBones();
        Set<String> bones=bones();
        float in=number("poseBlendFadeIn",0,0,0,Float.MAX_VALUE);
        float out=number("poseBlendFadeOut",0,0,0,Float.MAX_VALUE);
        return new Result(new PoseBlendSettings(mode,invalidMode?0:weight,channels,allBones,bones,in,out),issues);
    }
    private float number(String key,float missing,float invalid,float min,float max) {
        var raw=data.get(key);
        if(raw==null || raw.isJsonNull()) { issue(key,"缺失，使用默认值 "+missing);return missing; }
        if(raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            float value=raw.getAsFloat();
            if(Float.isFinite(value)) {
                float limited=Math.clamp(value,min,max);
                if(value!=limited) issue(key,"超出范围，已调整为 "+limited);
                return limited;
            }
        }
        issue(key,"无效数值，已恢复为 "+invalid);return invalid;
    }
    private int channels() {
        String key="poseBlendChannels";var raw=data.get(key);
        if(raw==null || raw.isJsonNull()) { issue(key,"缺失，使用全部通道");return RigPose.ALL_CHANNELS; }
        try {
            if(raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                int value=raw.getAsBigDecimal().intValueExact();
                if(value>=0) {
                    int known=value & RigPose.ALL_CHANNELS;
                    if(known!=value) issue(key,"已移除未知通道位");
                    return known;
                }
            }
        } catch(ArithmeticException | NumberFormatException ignored) {}
        issue(key,"无效通道范围，使用空通道，避免扩大动作影响");return 0;
    }
    private boolean allBones() {
        String key="poseBlendAllBones";var raw=data.get(key);
        if(raw==null || raw.isJsonNull()) {
            // An existing custom list must not silently become an all-bones mask.
            boolean all=!data.has("poseBlendBones");
            issue(key,all?"缺失，使用全部部位":"缺失，保留自定义部位范围");return all;
        }
        if(raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()) return raw.getAsBoolean();
        issue(key,"无效布尔值，保留自定义部位范围");return false;
    }
    private Set<String> bones() {
        String key="poseBlendBones";var raw=data.get(key);var result=new LinkedHashSet<String>();
        if(raw==null || raw.isJsonNull()) { issue(key,"缺失，使用空自定义部位范围");return result; }
        if(!raw.isJsonArray()) { issue(key,"无效部位列表，使用空自定义范围");return result; }
        boolean invalid=false;
        for(var id:raw.getAsJsonArray()) {
            if(id.isJsonPrimitive() && id.getAsJsonPrimitive().isString() && !id.getAsString().isBlank()) result.add(id.getAsString());
            else invalid=true;
        }
        if(invalid) issue(key,"已移除空白或类型无效的部位标识");
        return result;
    }
    private void issue(String field,String message) { issues.add(field+"："+message); }
}
