package com.director.ui.properties.adapters;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRegistryManager;
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
        return Icons.GROUP + " 演员剧团";
    }

    @Override
    public boolean renderProperties(ActorGroup group) {
        if (group == null) {
            ImGui.textDisabled("无 ActorGroup");
            return false;
        }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImString name = new ImString(group.getName(), 128);
            if (ImGui.inputText("名称##actorGroupName", name)) {
                group.setName(name.get());
                changed = true;
            }
            ImGui.text("ID: " + group.getGroupId());
            ImGui.text("成员数: " + group.size());
        }

        if (ImGui.collapsingHeader(Icons.DISTRIBUTE + " 编队", ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorGroup.FormationType[] formationTypes = ActorGroup.FormationType.values();
            ImInt formation = new ImInt(group.getFormationType().ordinal());
            String[] labels = new String[formationTypes.length];
            for (int i = 0; i < formationTypes.length; i++) {
                labels[i] = formationTypes[i].name();
            }
            if (ImGui.combo("编队类型##groupFormation", formation, labels)) {
                group.setFormationType(formationTypes[Math.max(0, Math.min(formation.get(), formationTypes.length - 1))]);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(Icons.TIME + " 延迟策略", ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorGroup.DelayPolicy[] delayPolicies = ActorGroup.DelayPolicy.values();
            ImInt delay = new ImInt(group.getDelayPolicy().ordinal());
            String[] labels = new String[delayPolicies.length];
            for (int i = 0; i < delayPolicies.length; i++) {
                labels[i] = delayPolicies[i].name();
            }
            if (ImGui.combo("策略##groupDelayPolicy", delay, labels)) {
                group.setDelayPolicy(delayPolicies[Math.max(0, Math.min(delay.get(), delayPolicies.length - 1))]);
                changed = true;
            }

            float[] step = { group.getDelayStepSeconds() };
            if (ImGui.sliderFloat("间隔##groupDelayStep", step, 0.0f, 5.0f, "%.2fs")) {
                group.setDelayStepSeconds(step[0]);
                changed = true;
            }
        }

        if (ImGui.collapsingHeader(Icons.SMILE + " 成员", ImGuiTreeNodeFlags.DefaultOpen)) {
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

        if (changed) {
            ActorRegistryManager.save();
        }
        return changed;
    }
}
