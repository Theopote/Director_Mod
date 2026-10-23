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
    private ActorGroup draftOwner;
    private final ImString nameDraft = new ImString(128);
    private final float[] spacingDraft = {1};
    private final float[] stepDraft = {0};
    private boolean editingDraft;
    private String error = "";
    private void commit(ActorGroup group, com.director.ui.actor.ActorProjectEditing.GroupState next) {
        try {
            com.director.ui.actor.ActorProjectEditing.updateGroup(
                    com.director.core.DirectorServices.get().containerTimeline().getTimelineModel(),
                    ActorRegistryManager.getInstance(), group,
                    com.director.ui.actor.ActorProjectEditing.GroupState.capture(group), next);
            error = "";
        } catch (IllegalArgumentException ex) { error = ex.getMessage(); }
    }

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

        var model = com.director.core.DirectorServices.get().containerTimeline().getTimelineModel();
        boolean readOnly = model == null || model.isPlaying() || model.isScrubbing();
        if (draftOwner != group || !editingDraft || readOnly) {
            draftOwner = group;
            nameDraft.set(group.getName()); spacingDraft[0] = group.getFormationSpacing(); stepDraft[0] = group.getDelayStepSeconds();
        }
        editingDraft = false;
        boolean changed = false;
        ImGui.beginDisabled(readOnly);
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.INFO + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.inputText(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.NAME) + "##actorGroupName", nameDraft);
            editingDraft |= ImGui.isItemActive();
            if (ImGui.isItemDeactivatedAfterEdit()) {
                if (nameDraft.get().isBlank()) error = "分组名称不能为空";
                else { commit(group, com.director.ui.actor.ActorProjectEditing.GroupState.capture(group).withName(nameDraft.get())); changed = true; }
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
                commit(group, com.director.ui.actor.ActorProjectEditing.GroupState.capture(group).withFormation(formationTypes[Math.max(0, Math.min(formation.get(), formationTypes.length - 1))]));
                changed = true;
            }

            ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.SPACING) + "##groupFormationSpacing", spacingDraft, 0.1f, 12.0f, "%.1f blocks");
            editingDraft |= ImGui.isItemActive();
            if (ImGui.isItemDeactivatedAfterEdit()) { commit(group, com.director.ui.actor.ActorProjectEditing.GroupState.capture(group).withSpacing(spacingDraft[0])); changed = true; }

        }

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.SECTION_DELAY),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorGroup.DelayPolicy[] delayPolicies = ActorGroup.DelayPolicy.values();
            ImInt delay = new ImInt(group.getDelayPolicy().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.DELAY_POLICY) + "##groupDelayPolicy",
                    delay, ActorPropertyAdapterI18n.delayPolicyLabels())) {
                commit(group, com.director.ui.actor.ActorProjectEditing.GroupState.capture(group).withDelay(delayPolicies[Math.max(0, Math.min(delay.get(), delayPolicies.length - 1))]));
                changed = true;
            }

            ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Group.DELAY_STEP) + "##groupDelayStep", stepDraft, 0.0f, 5.0f, "%.2fs");
            editingDraft |= ImGui.isItemActive();
            if (ImGui.isItemDeactivatedAfterEdit()) { commit(group, com.director.ui.actor.ActorProjectEditing.GroupState.capture(group).withStep(stepDraft[0])); changed = true; }

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
                    com.director.ui.actor.ActorProjectEditing.setMembership(model, registry, group, actorId, false);
                    changed = true;
                    break;
                }
            }
        }

        var projectAdapter=com.director.ui.properties.PropertyAdapterRegistry.getAdapterFor((Object)"ACTOR_PROJECT");
        if(projectAdapter instanceof ActorProjectPropertyAdapter project) project.renderGroupCommands(group);
        if(ImGui.button(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Project.DELETE_GROUP))) {
            com.director.ui.actor.ActorProjectEditing.deleteGroup(model, ActorRegistryManager.getInstance(), group, () -> {
                com.director.core.DirectorServices.get().uiSelection().clearSelection();
                com.director.ui.properties.PropertyContextService.get().clear();
            });
            changed=true;
        }
        ImGui.endDisabled();
        if (!error.isEmpty()) ImGui.textWrapped(error);
        return changed;
    }
}
