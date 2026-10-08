package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.timeline.model.entity.EntitySpawnClip;
import imgui.ImGui;
import imgui.type.ImFloat;

public class EntitySpawnClipPropertyAdapter implements IPropertyAdapter<EntitySpawnClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    @Override public Class<EntitySpawnClip> getTargetType() { return EntitySpawnClip.class; }
    private String tr(String key) { return ActorPropertyAdapterI18n.tr("gui.director.actor.spawn_edit."+key); }
    @Override public boolean renderProperties(EntitySpawnClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof EntitySpawnClip clip)) { gesture.end(); return false; }
        ImGui.text(tr("title"));
        ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.range",clip.getStartTime(),clip.getEndTime()));
        var end = new ImFloat(clip.getEndTime());
        boolean changed = ImGui.inputFloat(tr("end")+"##spawnEnd",end);
        if (changed) {
            if (Float.isFinite(end.get()) && end.get()-clip.getStartTime() >= .1f-1e-5f) clip.setTimeRange(clip.getStartTime(),end.get());
            else gesture.report(tr("invalid_time"));
        }
        gesture.observe("end",changed);
        var duration = new ImFloat(clip.getLength());
        changed = ImGui.inputFloat(tr("duration")+"##spawnDuration",duration);
        if (changed) {
            if (Float.isFinite(duration.get()) && duration.get() >= .1f-1e-5f
                    && Float.isFinite(clip.getStartTime()+duration.get())) clip.setTimeRange(clip.getStartTime(),clip.getStartTime()+duration.get());
            else gesture.report(tr("invalid_time"));
        }
        gesture.observe("duration",changed);
        float[] pos = {clip.getPosition().x,clip.getPosition().y,clip.getPosition().z};
        changed = ImGui.inputFloat3(tr("position")+"##spawnPosition",pos);
        if (changed) clip.setPosition(pos[0],pos[1],pos[2]);
        gesture.observe("position",changed);
        var yaw = new ImFloat(clip.getYawDeg()); changed = ImGui.inputFloat(tr("yaw")+"##spawnYaw",yaw);
        if (changed) clip.setYawDeg(yaw.get()); gesture.observe("yaw",changed);
        float[] pitch = {clip.getPitchDeg()}; changed = ImGui.sliderFloat(tr("pitch")+"##spawnPitch",pitch,-90,90);
        if (changed) clip.setPitchDeg(pitch[0]); gesture.observe("pitch",changed);
        var roll = new ImFloat(clip.getRollDeg());
        changed = ImGui.inputFloat(ActorPropertyAdapterI18n.tr("gui.director.actor.spawn_edit.roll") + "##spawnRoll", roll);
        if (changed) clip.setRollDeg(roll.get()); gesture.observe("roll", changed);
        float[] scale = {clip.getScale().x, clip.getScale().y, clip.getScale().z};
        changed = ImGui.inputFloat3(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.scale") + "##spawnScale", scale);
        if (changed) {
            if (java.util.stream.IntStream.range(0, 3).allMatch(axis -> Float.isFinite(scale[axis]) && scale[axis] >= .01f && scale[axis] <= 100))
                clip.setScale(scale[0], scale[1], scale[2]);
            else gesture.report(ActorPropertyAdapterI18n.tr("gui.director.actor.spawn_edit.invalid_scale"));
        }
        gesture.observe("scale", changed);
        changed = ImGui.checkbox(tr("visible")+"##spawnVisible",clip.isVisibleOnSpawn());
        if (changed) clip.setVisibleOnSpawn(!clip.isVisibleOnSpawn()); gesture.observe("visible",changed);
        gesture.end(); return false;
    }
}
