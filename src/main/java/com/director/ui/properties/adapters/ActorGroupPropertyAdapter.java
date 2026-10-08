package com.director.ui.properties.adapters;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRegistryManager;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.UUID;

public final class ActorGroupPropertyAdapter implements IPropertyAdapter<ActorGroup> {
    @Override
    public Class<ActorGroup> getTargetType() {
        return ActorGroup.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.GROUP + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorGroup group) {
        if (group == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.NO_GROUP));
            return false;
        }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.INFO + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImString name = new ImString(group.getName(), 128);
            if (ImGui.inputText(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NAME) + "##actorGroupName", name)) {
                group.setName(name.get());
                changed = true;
            }
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.ID_FMT, group.getGroupId()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.MEMBER_COUNT_FMT, group.size()));
        }

        if (ImGui.collapsingHeader(Icons.DISTRIBUTE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.SECTION_FORMATION),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorGroup.FormationType[] formationTypes = ActorGroup.FormationType.values();
            ImInt formation = new ImInt(group.getFormationType().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.FORMATION_TYPE) + "##groupFormation",
                    formation, ActorPropertyAdapterI18n.formationTypeLabels())) {
                group.setFormationType(formationTypes[Math.max(0, Math.min(formation.get(), formationTypes.length - 1))]);
                changed = true;
            }

            float[] spacing = {group.getFormationSpacing()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.SPACING) + "##groupFormationSpacing",
                    spacing, 0.1f, 12.0f, "%.1f blocks")) {
                group.setFormationSpacing(spacing[0]);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.SECTION_DELAY),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorGroup.DelayPolicy[] delayPolicies = ActorGroup.DelayPolicy.values();
            ImInt delay = new ImInt(group.getDelayPolicy().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.DELAY_POLICY) + "##groupDelayPolicy",
                    delay, ActorPropertyAdapterI18n.delayPolicyLabels())) {
                group.setDelayPolicy(delayPolicies[Math.max(0, Math.min(delay.get(), delayPolicies.length - 1))]);
                changed = true;
            }

            float[] step = {group.getDelayStepSeconds()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.DELAY_STEP) + "##groupDelayStep",
                    step, 0.0f, 5.0f, "%.2fs")) {
                group.setDelayStepSeconds(step[0]);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(Icons.SMILE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.SECTION_MEMBERS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            var registry = ActorRegistryManager.getInstance();
            for (UUID actorId : group.getActorIds()) {
                String label = registry.getActor(actorId)
                        .map(Actor::getName)
                        .orElse(actorId.toString());
                ImGui.bulletText(label);
                ImGui.sameLine();
                if (ImGui.button(Icons.DELETE + "##removeGroupActor" + actorId)) {
                    group.removeActor(actorId);
                    changed = true;
                    break;
                }
            }
        }

        var projectAdapter=com.director.ui.properties.PropertyAdapterRegistry.getAdapterFor((Object)"ACTOR_PROJECT");
        if(projectAdapter instanceof ActorProjectPropertyAdapter project) project.renderGroupCommands(group);
        if(ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_GROUP))) {
            ActorRegistryManager.getInstance().removeGroup(group.getGroupId());
            com.director.core.DirectorServices.get().uiSelection().clearSelection();
            changed=true;
        }
        if (changed) {
            ActorRegistryManager.save();
        }
        return changed;
    }
}
