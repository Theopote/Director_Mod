package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.Section;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.EntityLookAtClip;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector3f;
import java.util.Objects;
import java.util.UUID;

public class EntityLookAtClipPropertyAdapter implements IPropertyAdapter<EntityLookAtClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    private final ImString entityUuid = new ImString(64);
    private EntityLookAtClip uuidSource;
    private UUID cachedTarget;
    private int uuidFrame = -1;
    @Override public Class<EntityLookAtClip> getTargetType() { return EntityLookAtClip.class; }
    @Override public boolean renderProperties(EntityLookAtClip source) {
        if (source != null && ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape))
            entityUuid.set(source.getTargetEntityUuid() == null ? "" : source.getTargetEntityUuid().toString());
        var draft = gesture.begin(source);
        if (!(draft instanceof EntityLookAtClip clip)) { gesture.end(); return false; }
        if (source != null && (uuidFrame != ImGui.getFrameCount() - 1 || uuidSource != source || !Objects.equals(cachedTarget, source.getTargetEntityUuid()))) {
            uuidSource = source;
            cachedTarget = source.getTargetEntityUuid();
            entityUuid.set(cachedTarget == null ? "" : cachedTarget.toString());
        }
        uuidFrame = ImGui.getFrameCount();
        UITheme.title(Icons.SMILE + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.TITLE));
        if (source != null) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, source.getStartTime()));
        }
        if (source != null) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, source.getEndTime()));
        }
        if (Section.header("entity_look_at_clip.section_target_mode", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.SECTION_TARGET_MODE))) {
            var modes = EntityLookAtClip.TargetMode.values(); var index = new ImInt(clip.getMode().ordinal());
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.TARGET_MODE));
            boolean changed = ImGui.combo("##lookClipMode", index, ActorPropertyAdapterI18n.lookAtTargetModeLabels());
            if (changed) clip.setMode(modes[index.get()]);
            gesture.observe("mode", changed);
        }
        if (Section.header("entity_look_at_clip.section_target", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.SECTION_TARGET))) {
            if (clip.getMode() == EntityLookAtClip.TargetMode.WORLD_POINT) {
                Vector3f point = clip.getWorldPoint(); float[] xyz = {point.x, point.y, point.z};
                PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.WORLD_COORDS));
                boolean changed = ImGui.inputFloat3("##lookClipWorldPoint", xyz);
                if (changed) clip.setWorldPoint(xyz[0], xyz[1], xyz[2]);
                gesture.observe("point", changed);
            } else if (clip.getMode() == EntityLookAtClip.TargetMode.CAMERA) {
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.LOOK_AT_CAMERA));
            } else if (clip.getTargetEntityUuid() != null) {
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.TARGET_ENTITY_UUID_FMT, clip.getTargetEntityUuid()));
            } else {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.NO_TARGET_ENTITY));
            }
            if (clip.getMode() == EntityLookAtClip.TargetMode.ENTITY_UUID) {
                var registry = com.director.actor.ActorRegistryManager.getInstance();
                String label = registry.getActor(clip.getTargetEntityUuid()).map(com.director.actor.Actor::getName)
                        .orElse(ActorPropertyAdapterI18n.tr("gui.director.actor.look_at.choose_actor"));
                PropertyRow.field(ActorPropertyAdapterI18n.tr("gui.director.actor.look_at.target_actor"));
                if (ImGui.beginCombo("##entity_look_at_clip_target_actor", label)) {
                    for (var actor : registry.getAllActors()) {
                        if (ImGui.selectable(actor.getName() + "##gazeActor" + actor.getActorId(),
                                actor.getActorId().equals(clip.getTargetEntityUuid()))) {
                            clip.setTargetEntityUuid(actor.getActorId());
                            entityUuid.set(actor.getActorId().toString());
                            gesture.requestCommit();
                        }
                    }
                    ImGui.endCombo();
                }
            }
            PropertyRow.field(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.entity_uuid"));
            ImGui.inputText("##lookClipEntityUuid", entityUuid);
            if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.apply_entity") + "##lookClipApplyEntity")) {
                try {
                    String text = entityUuid.get().trim(); UUID parsed = UUID.fromString(text);
                    if (!parsed.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException();
                    clip.setTargetEntityUuid(parsed); clip.setMode(EntityLookAtClip.TargetMode.ENTITY_UUID); gesture.requestCommit();
                } catch (IllegalArgumentException invalid) { gesture.report(ActorPropertyAdapterI18n.tr("gui.director.actor.clip_inspector.invalid_uuid")); }
            }
        }
        if (Section.header("entity_look_at_clip.section_strength", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.SECTION_STRENGTH))) {
            float[] strength = {clip.getStrength()};
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY));
            boolean changed = ImGui.sliderFloat("##lookClipStrength", strength, 0, 1);
            if (changed) clip.setStrength(strength[0]);
            gesture.observe("strength", changed);
            if (ImGui.isItemHovered()) ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.STRENGTH_TOOLTIP));
        }
        gesture.end();
        return false;
    }
}
