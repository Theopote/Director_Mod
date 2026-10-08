package com.director.actor;

import com.director.actor.action.ActorActionType;
import com.director.actor.prop.ActorPropSlot;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import java.util.*;

/** Current implementation capabilities, independent of timeline binding and edit permissions. */
public record ActorCapabilities(boolean selectable, boolean movable, RigDefinition rig,
        Set<ActorActionType> actions, Set<ActorPropSlot> propSlots) {
    public ActorCapabilities { actions=Set.copyOf(actions); propSlots=Set.copyOf(propSlots); }
    public boolean editableBones() { return rig!=null; }
    public boolean supports(ActorActionType action) { return action!=null && actions.contains(action); }
    public boolean humanoidPose() { return HumanoidTopology.matches(rig); }
    public static ActorCapabilities of(Actor actor) {
        if(actor==null) return new ActorCapabilities(false,false,null,Set.of(),Set.of());
        var rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
        var actions=EnumSet.noneOf(ActorActionType.class);
        if(actor.getRigProfile()!=null) for(var action:ActorActionType.values())
            if(actor.getRigProfile().supports(action)) actions.add(action);
        Set<ActorPropSlot> slots= HumanoidTopology.matches(rig)
                ? EnumSet.allOf(ActorPropSlot.class) : Set.of();
        return new ActorCapabilities(true,true,rig,actions,slots);
    }
}
