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
    /** One current context per browser, not a global map retaining old worlds/actors. */
    public static final class Cache {
        private AnimationAssetLibrary project;
        private AnimationLibrary builtin;
        private long projectRevision=-1, builtinRevision=-1;
        private RigDefinition rig;
        private com.director.actor.ActorType type;
        private List<Entry> rows;
        private List<Entry> filteredInput, filtered;
        private String query, excluded;
        private Source source;
        private AnimationData.Purpose purpose;
        private boolean compatible;

        public List<Entry> entries(AnimationAssetLibrary library,AnimationLibrary builtins,Actor actor) {
            var nextRig=ActorRigCompatibility.resolveRig(actor).orElse(null);
            var nextType=actor==null?null:actor.getType();
            long nextBuiltinRevision=builtins==null?0:builtins.revision();
            if(rows==null || project!=library || builtin!=builtins || projectRevision!=library.revision()
                    || builtinRevision!=nextBuiltinRevision || rig!=nextRig || type!=nextType) {
                rows=ActorAnimationCatalog.entries(library,builtins==null?List.of():builtins.getAllAnimations(),actor);
                project=library; builtin=builtins; projectRevision=library.revision();
                builtinRevision=nextBuiltinRevision; rig=nextRig; type=nextType;
            }
            return rows;
        }
        public List<Entry> filter(List<Entry> entries,String query,Source source,AnimationData.Purpose purpose,
                                  boolean compatibleOnly,String excludedId) {
            if(filtered==null || filteredInput!=entries || !Objects.equals(this.query,query)
                    || this.source!=source || this.purpose!=purpose || compatible!=compatibleOnly
                    || !Objects.equals(excluded,excludedId)) {
                filtered=ActorAnimationCatalog.filter(entries,query,source,purpose,compatibleOnly,excludedId);
                filteredInput=entries; this.query=query; this.source=source; this.purpose=purpose;
                compatible=compatibleOnly; excluded=excludedId;
            }
            return filtered;
        }
    }
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
