package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.entity.EntityTransformClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import org.joml.Vector3f;

/** Selected clip endpoints; keyed transforms are edited at the playhead instead. */
public class EntityTransformClipPropertyAdapter implements IPropertyAdapter<EntityTransformClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    @Override public Class<EntityTransformClip> getTargetType() { return EntityTransformClip.class; }
    @Override public boolean renderProperties(EntityTransformClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof EntityTransformClip clip)) { gesture.end(); return false; }
        UITheme.title(Icons.SMILE + " " + ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.title"));
        ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.range", source.getStartTime(), source.getEndTime()));
        boolean keyed = !source.getKeyframes().isEmpty();
        if (keyed) ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.keyed"));
        ImGui.beginDisabled(keyed);
        if (ImGui.collapsingHeader(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.interpolation_section"), ImGuiTreeNodeFlags.DefaultOpen)) {
            var modes = TimelineInterpolation.values();
            var index = new ImInt(clip.getInterpolation().ordinal());
            String[] labels = java.util.Arrays.stream(modes).map(TimelineInterpolation::getDisplayName).toArray(String[]::new);
            boolean changed = ImGui.combo(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.interpolation")
                    + "##transformInterpolation", index, labels);
            if (changed) clip.setInterpolation(modes[index.get()]);
            gesture.observe("interpolation", changed);
        }
        if (ImGui.collapsingHeader(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.from"), ImGuiTreeNodeFlags.DefaultOpen)) {
            vector(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.position") + "##transformFromPosition", clip.getFromPos());
            vector(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.rotation") + "##transformFromRotation", clip.getFromRot());
            vector(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.scale") + "##transformFromScale", clip.getFromScale());
        }
        if (ImGui.collapsingHeader(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.to"), ImGuiTreeNodeFlags.DefaultOpen)) {
            vector(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.position") + "##transformToPosition", clip.getToPos());
            vector(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.rotation") + "##transformToRotation", clip.getToRot());
            vector(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.scale") + "##transformToScale", clip.getToScale());
        }
        ImGui.endDisabled();
        // Legacy followPath metadata is not consumed by EntityClipSampler.
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.route_hint"));
        if (clip.isFollowPath() || clip.getPathId() != null)
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.legacy_path", clip.getPathId()));
        gesture.end();
        com.director.ui.properties.actor.ActorRootKeyframePropertyUI.render(source);
        return false; // The transaction marks the model dirty exactly once.
    }
    private void vector(String label, Vector3f target) {
        float[] values = {target.x, target.y, target.z};
        boolean changed = ImGui.inputFloat3(label, values);
        if (changed) target.set(values[0], values[1], values[2]);
        gesture.observe(label, changed);
    }
}
