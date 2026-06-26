package com.director.ui.properties.adapters;

import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.EntityStateClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityStateClipPropertyAdapter v1.0
 *
 * <p>实体状态片段属性面板适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityStateClipPropertyAdapter implements IPropertyAdapter<EntityStateClip> {

    private final ImBoolean sneakingBoolean = new ImBoolean();
    private final ImBoolean sprintingBoolean = new ImBoolean();
    private final ImBoolean glowingBoolean = new ImBoolean();
    private final ImBoolean invulnerableBoolean = new ImBoolean();

    @Override
    public Class<EntityStateClip> getTargetType() {
        return EntityStateClip.class;
    }

    @Override
    public boolean renderProperties(EntityStateClip clip) {
        if (clip == null) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NO_CLIP));
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);

        UITheme.title(Icons.SMILE + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.ADAPTER_NAME));

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.TIME_RANGE));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
            UITheme.endGroup();
        }

        if (ImGui.collapsingHeader(Icons.SMILE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SECTION_BEHAVIOR),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            sneakingBoolean.set(clip.isSneaking());
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SNEAKING), sneakingBoolean)) {
                clip.setSneaking(sneakingBoolean.get());
                changed.set(true);
            }

            sprintingBoolean.set(clip.isSprinting());
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SPRINTING), sprintingBoolean)) {
                clip.setSprinting(sprintingBoolean.get());
                changed.set(true);
            }
        }

        if (ImGui.collapsingHeader(Icons.STAR + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SECTION_VISUAL))) {
            glowingBoolean.set(clip.isGlowing());
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.GLOWING), glowingBoolean)) {
                clip.setGlowing(glowingBoolean.get());
                changed.set(true);
            }

            float[] visibilityArray = {clip.getVisibility()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.VISIBILITY),
                    visibilityArray, 0.0f, 1.0f)) {
                clip.setVisibility(visibilityArray[0]);
                changed.set(true);
            }
        }

        if (ImGui.collapsingHeader(Icons.SHIELD + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.SECTION_COMBAT))) {
            invulnerableBoolean.set(clip.isInvulnerable());
            if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.State.INVULNERABLE), invulnerableBoolean)) {
                clip.setInvulnerable(invulnerableBoolean.get());
                changed.set(true);
            }
        }

        return changed.get();
    }
}
