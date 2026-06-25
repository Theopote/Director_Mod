package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.EntityLookAtClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class EntityLookAtClipPropertyAdapter implements IPropertyAdapter<EntityLookAtClip> {

    private final ImInt modeIndex = new ImInt();
    private final ImFloat worldPointX = new ImFloat();
    private final ImFloat worldPointY = new ImFloat();
    private final ImFloat worldPointZ = new ImFloat();

    @Override
    public Class<EntityLookAtClip> getTargetType() {
        return EntityLookAtClip.class;
    }

    @Override
    public boolean renderProperties(EntityLookAtClip clip) {
        if (clip == null) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NO_CLIP));
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);

        UITheme.title(Icons.SMILE + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.TITLE));

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.TIME_RANGE));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
            UITheme.endGroup();
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.SECTION_TARGET_MODE),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode[] modes = EntityLookAtClip.TargetMode.values();
            for (int i = 0; i < modes.length; i++) {
                if (modes[i] == clip.getMode()) {
                    modeIndex.set(i);
                    break;
                }
            }

            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.TARGET_MODE),
                    modeIndex, ActorPropertyAdapterI18n.lookAtTargetModeLabels())) {
                clip.setMode(modes[modeIndex.get()]);
                changed.set(true);
            }
        }

        if (ImGui.collapsingHeader(Icons.PIN + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.SECTION_TARGET),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode mode = clip.getMode();

            if (mode == EntityLookAtClip.TargetMode.WORLD_POINT) {
                Vector3f worldPoint = clip.getWorldPoint();
                worldPointX.set(worldPoint.x);
                worldPointY.set(worldPoint.y);
                worldPointZ.set(worldPoint.z);

                UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.WORLD_COORDS));
                if (ImGui.inputFloat("X", worldPointX)) {
                    clip.setWorldPoint(worldPointX.get(), worldPoint.y, worldPoint.z);
                    changed.set(true);
                }
                if (ImGui.inputFloat("Y", worldPointY)) {
                    clip.setWorldPoint(worldPoint.x, worldPointY.get(), worldPoint.z);
                    changed.set(true);
                }
                if (ImGui.inputFloat("Z", worldPointZ)) {
                    clip.setWorldPoint(worldPoint.x, worldPoint.y, worldPointZ.get());
                    changed.set(true);
                }
                UITheme.endGroup();
            } else if (mode == EntityLookAtClip.TargetMode.ENTITY_UUID) {
                UUID targetUuid = clip.getTargetEntityUuid();
                if (targetUuid != null) {
                    ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.TARGET_ENTITY_UUID_FMT, targetUuid));
                } else {
                    ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.NO_TARGET_ENTITY));
                }
            } else if (mode == EntityLookAtClip.TargetMode.CAMERA) {
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.LOOK_AT_CAMERA));
            }
        }

        if (ImGui.collapsingHeader(Icons.SCALE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.SECTION_STRENGTH))) {
            float[] strengthArray = {clip.getStrength()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY),
                    strengthArray, 0.0f, 1.0f)) {
                clip.setStrength(strengthArray[0]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.STRENGTH_TOOLTIP));
            }
        }

        return changed.get();
    }
}
