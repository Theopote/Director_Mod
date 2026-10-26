package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.*;
import java.util.*;

/** Read-only metadata browsing. Does not sample poses or mutate assets/selection. */
public final class ActorAnimationCatalog {
    public enum Source { PROJECT, BUILTIN }
    public record Entry(String id,String name,String rigName,AnimationData.Purpose purpose,Source source,boolean compatible) {
        public boolean matches(String query) {
            String text=query==null?"":query.strip().toLowerCase(Locale.ROOT);
            return text.isEmpty() || (name+" "+id+" "+rigName+" "+purpose.label()).toLowerCase(Locale.ROOT).contains(text);
        }
    }
    private ActorAnimationCatalog() {}
    public static List<Entry> entries(AnimationAssetLibrary library,Collection<AnimationData> builtins,Actor actor) {
        RigDefinition rig=ActorRigCompatibility.resolveRig(actor).orElse(null);
        var entries=new LinkedHashMap<String,Entry>();
        for(var asset:library.all()) {
            boolean compatible=rig!=null && ActorRigCompatibility.accepts(actor,asset.rigId()) && RigPoseComposer.sameLayout(rig,asset.rig());
            entries.put(asset.id(),new Entry(asset.id(),asset.name(),asset.rig().displayName(),asset.purpose(),Source.PROJECT,compatible));
        }
        for(var animation:builtins) entries.putIfAbsent(animation.getId(),new Entry(animation.getId(),animation.getName(),
                "标准人形",animation.purpose(),Source.BUILTIN,HumanoidTopology.matches(rig)));
        return List.copyOf(entries.values());
    }
    public static List<Entry> filter(List<Entry> entries,String query,Source source,AnimationData.Purpose purpose,
                                     boolean compatibleOnly,String excludedId) {
        return entries.stream().filter(e->e.matches(query) && (source==null || e.source()==source)
                && (purpose==null || e.purpose()==purpose) && (!compatibleOnly || e.compatible())
                && !Objects.equals(e.id(),excludedId)).toList();
    }
}
