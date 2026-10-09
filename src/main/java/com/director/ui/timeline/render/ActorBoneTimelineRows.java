package com.director.ui.timeline.render;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.asset.*;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.actor.pose.ActorBoneSelectionController;
import com.director.ui.actor.pose.ActorAnimationEditContext;
import com.director.ui.actor.pose.ActorAnimationAuthoringService;
import com.director.ui.actor.pose.ActorBonePropertiesPanel;
import com.director.ui.actor.pose.ActorBoneGizmoController;
import imgui.flag.ImGuiKey;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiHoveredFlags;
import java.util.*;

/** View-only expansion state, scoped to one timeline renderer. */
public final class ActorBoneTimelineRows {
    private static final Set<ActorBoneTimelineRows> instances=Collections.newSetFromMap(new WeakHashMap<>());
    public ActorBoneTimelineRows() { instances.add(this); }
    public static void cancelAllKeyInteractions() { for(var rows:new ArrayList<>(instances)) rows.cancelKeyInteraction(); }
    public static void closeAllTransientEditors() {
        for(var rows:new ArrayList<>(instances)) {
            rows.cancelKeyInteraction(); rows.timePanel.cancel();
            rows.actionMenu=false; rows.pendingKeys=List.of(); rows.pendingModel=null;
        }
    }
    public static final float CHILD_HEIGHT = 24f;
    private final ActorRootTimelineRows rootRows = new ActorRootTimelineRows();
    private TimelineModel renderModel;
    private final Set<TimelineTrack> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TimelineTrack> supported = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TimelineTrack, com.director.actor.rig.skeleton.RigDefinition> trackRigs = new IdentityHashMap<>();
    private record KeyHit(TimelineTrack track, String bone, float x, float y, float time,
                          AnimationAsset asset, EntityAnimationClip clip, BoneTrack boneTrack, int channel, float assetTime) {}
    private final Map<TimelineTrack,Set<String>> expandedBones=new IdentityHashMap<>();
    private List<ActorBoneChannelLayout.Row> boneRows(TimelineTrack track,com.director.actor.rig.skeleton.RigDefinition rig) {
        return ActorBoneChannelLayout.rows(rig,expandedBones.getOrDefault(track,Set.of()));
    }
    private final List<KeyHit> keyHits = new ArrayList<>();
    private KeyHit dragging;
    private float dragMouseX;
    private float dragPixelsPerSecond;
    private float dragStart, dragEnd, dragSpeed;
    private Object dragSource;
    private final Map<ActorAnimationAuthoringService.Key, KeyHit> selectedKeys = new LinkedHashMap<>();
    private final com.director.ui.actor.pose.ActorKeyframeSelection sharedSelection=com.director.ui.actor.pose.ActorKeyframeSelection.getInstance();
    private void importSelection(TimelineModel model) {
        var state=sharedSelection.snapshot(model);
        if(state==null) { selectedKeys.clear(); keyFocus=false; return; }
        selectedOwner=state.owner(); selectedReference=state.clip(); selectedAsset=state.asset(); keyFocus=state.focused();
        selectedKeys.clear();
        for(var k:state.keys()) {
            var hit=new KeyHit(selectedOwner,k.boneId(),0,0,selectedReference.getStartTime()+k.time()/Math.max(.01f,selectedReference.getSpeed()),
                    selectedAsset,selectedReference,selectedAsset.findBoneTrack(k.boneId()).orElseThrow(),k.channel().ordinal(),k.time());
            selectedKeys.put(k,hit);
        }
    }
    private void publishSelection(TimelineModel model) {
        if(selectedOwner!=null && selectedReference!=null && selectedAsset!=null
                && model.getSelectedClip()==selectedReference.getTimelineClip())
            sharedSelection.replace(model,selectedOwner,selectedReference,selectedAsset,selectedKeys.keySet(),keyFocus);
    }
    private boolean keyFocus;
    private TimelineTrack selectedOwner;
    private EntityAnimationClip selectedReference;
    private AnimationAsset selectedAsset;
    private Map<ActorAnimationAuthoringService.Key, PendingKey> dragKeys = Map.of();
    private boolean pendingAdditive;
    private static ActorAnimationAuthoringService.Key key(String bone, int channel, float time) {
        return new ActorAnimationAuthoringService.Key(bone, ActorAnimationAuthoringService.Channel.values()[channel], time);
    }
    private void selectKey(KeyHit hit, boolean additive) {
        if(selectedOwner!=hit.track() || selectedReference!=hit.clip() || selectedAsset!=hit.asset()) selectedKeys.clear();
        focusReference(hit.track(),hit.clip(),hit.asset());
        var key=key(hit.bone(),hit.channel(),hit.assetTime());
        if(additive && selectedKeys.containsKey(key)) selectedKeys.remove(key);
        else { if(!additive && !selectedKeys.containsKey(key)) selectedKeys.clear(); selectedKeys.put(key,hit); }
    }
    void focusReference(TimelineTrack owner,EntityAnimationClip reference,AnimationAsset asset) {
        keyFocus=true; selectedOwner=owner; selectedReference=reference; selectedAsset=asset;
    }
    private float dragDelta() {
        return ActorKeyframeDrag.groupDelta(dragKeys.keySet().stream().map(ActorAnimationAuthoringService.Key::time).toList(),
                ImGui.getMousePosX()-dragMouseX,dragPixelsPerSecond,dragSpeed,dragging.asset().duration(),dragEnd-dragStart);
    }
    private record Marquee(ActorAnimationEditContext context, long selectionRevision, long settingsRevision, long sharedRevision,
            float x, float y, float left, float right, float top, float bottom, boolean additive,
            List<Float> viewport, List<TimelineTrack> owners, List<PendingKey> candidates) {}
    private Marquee marquee;
    private static List<Float> viewport(float left,float right,float top,float height,float scrollY,TimelineRenderMath math) {
        return List.of(left,right,top,height,scrollY,math.getPixelsPerSecond(),math.timeToX(0),math.getRowLayout().totalHeight());
    }
    public boolean updateMarquee(TimelineModel model,float left,float right,float top,float height,float scrollY,TimelineRenderMath math) {
        try { return updateMarqueeInternal(model,left,right,top,height,scrollY,math); } finally { publishSelection(model); }
    }
    private boolean updateMarqueeInternal(TimelineModel model,float left,float right,float top,float height,float scrollY,TimelineRenderMath math) {
        if (rootRows.updateMarquee(model,left,right,top,height,scrollY,math)) return true;
        if(marquee==null) return false;
        var box=marquee;
        if(box.context().model()!=model || !box.context().isBound()
                || box.context().contextRevision()!=model.getActorEditContextRevision()
                || box.sharedRevision()!=sharedSelection.revision()
                || box.selectionRevision()!=ActorBoneSelectionController.getInstance().getRevision()
                || box.settingsRevision()!=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision()
                || !box.owners().equals(model.getAllTracks())
                || !expanded.contains(box.context().track())
                || !box.viewport().equals(viewport(left,right,top,height,scrollY,math))
                || ActorBonePropertiesPanel.hasNumericDraft() || ActorBoneGizmoController.getInstance().isDragging()
                || ImGui.isKeyPressed(ImGuiKey.Escape) || !isCanvasWindowHovered()) {
            marquee=null; return true;
        }
        float mx=Math.clamp(ImGui.getMousePosX(),box.left(),box.right());
        float my=Math.clamp(ImGui.getMousePosY(),box.top(),box.bottom());
        boolean active=Math.hypot(mx-box.x(),my-box.y())>=4;
        var points=new ArrayList<ActorKeyframeHitCandidates.Point<PendingKey>>();
        for(var candidate:box.candidates()) points.add(new ActorKeyframeHitCandidates.Point<>(candidate,candidate.hit().x(),candidate.hit().y()));
        var inside=active?ActorKeyframeHitCandidates.rectangle(points,box.x(),box.y(),mx,my):List.<PendingKey>of();
        if(active) {
            var draw=ImGui.getWindowDrawList();
            draw.pushClipRect(box.left(),box.top(),box.right(),box.bottom(),true);
            try {
                draw.addRectFilled(Math.min(mx,box.x()),Math.min(my,box.y()),Math.max(mx,box.x()),Math.max(my,box.y()),TimelineColors.rgba(80,160,230,40));
                draw.addRect(Math.min(mx,box.x()),Math.min(my,box.y()),Math.max(mx,box.x()),Math.max(my,box.y()),TimelineColors.rgba(100,190,255,220));
                for(var candidate:inside) draw.addCircle(candidate.hit().x(),candidate.hit().y(),5,TimelineColors.COLOR_TEXT_LIGHT);
            } finally { draw.popClipRect(); }
        }
        ImGui.setTooltip(tr("marquee_hint"));
        if(ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            marquee=null;
            // Validate the whole rectangle before changing the transient selection.
            if(inside.stream().anyMatch(k -> k.hit().asset().findBoneTrack(k.hit().bone()).orElse(null)!=k.hit().boneTrack()
                    || !Objects.equals(source(k.hit()),k.source()))) return true;
            if(!box.additive()) selectedKeys.clear();
            for(var candidate:inside) {
                var hit=candidate.hit();
                selectedOwner=hit.track(); selectedReference=hit.clip(); selectedAsset=hit.asset();
                selectedKeys.put(key(hit.bone(),hit.channel(),hit.assetTime()),hit);
            }
        } else if(!ImGui.isMouseDown(ImGuiMouseButton.Left)) marquee=null;
        return true;
    }
    private ActorAnimationEditContext dragContext;
    private long dragSelectionRevision, dragSettingsRevision, dragSharedRevision;
    private static final String KEY_POPUP = "ActorKeyCandidates";
    private record PendingKey(KeyHit hit, ActorAnimationEditContext context, Object source) {}
    private List<PendingKey> pendingKeys = List.of();
    private TimelineModel pendingModel;
    private long pendingRevision, pendingSelectionRevision, pendingSettingsRevision, pendingSharedRevision;

    private boolean actionMenu;
    private final com.director.ui.actor.pose.ActorKeyframeTimePanel timePanel=new com.director.ui.actor.pose.ActorKeyframeTimePanel();
    public boolean handleKeyCommand(TimelineModel model,String action) {
        if (rootRows.handleKeyCommand(model, action)) return true;
        importSelection(model);
        // Once keys own focus, rejection must never fall through to deleting their clip.
        if(!keyFocus || selectedReference==null || model.getSelectedClip()!=selectedReference.getTimelineClip()) return false;
        try { executeKeyCommand(model,action); return true; } finally { publishSelection(model); }
    }
    private boolean executeKeyCommand(TimelineModel model,String action) {
        var wrapper=model.getSelectedClip();
        var context=wrapper instanceof com.director.timeline.model.entity.EntityTimelineClip entity
                && entity.getEntityClip() instanceof EntityAnimationClip animation
                ? ActorAnimationEditContext.capture(model,entity.getParentTrack(),animation):null;
        if(context==null || !context.isCurrent() || dragging!=null || marquee!=null
                || com.director.ui.actor.pose.ActorAnimationDopeSheet.isKeyInteractionActive()
                || ActorBonePropertiesPanel.hasNumericDraft() || ActorBoneGizmoController.getInstance().isDragging()) return false;
        boolean same=selectedOwner==context.track() && selectedReference==context.clip() && selectedAsset==context.asset();
        var clipboard=com.director.ui.actor.pose.ActorKeyframeClipboard.getInstance();
        if(action.equals("copy")) return same && clipboard.copy(context.asset(),selectedKeys.keySet());
        if(action.equals("cut") && (!same || !clipboard.copy(context.asset(),selectedKeys.keySet()))) return false;
        if(action.equals("delete") || action.equals("cut")) {
            if(!same || !ActorAnimationAuthoringService.delete(context,selectedKeys.keySet())) return false;
            selectedKeys.clear(); return true;
        }
        if(!action.equals("paste")) return false;
        var pasted=clipboard.at(context.asset(),context.assetTime(),(context.end()-context.start())*Math.max(.01f,context.speed()));
        if(!ActorAnimationAuthoringService.paste(context,pasted)) return false;
        selectedKeys.clear(); keyFocus=true; selectedOwner=context.track(); selectedReference=context.clip(); selectedAsset=context.asset();
        for(var key:pasted) {
            var hit=new KeyHit(context.track(),key.boneId(),0,0,context.start()+key.time()/Math.max(.01f,context.speed()),
                    context.asset(),context.clip(),context.asset().findBoneTrack(key.boneId()).orElseThrow(),key.channel().ordinal(),key.time());
            selectedKeys.put(key(key.boneId(),key.channel().ordinal(),key.time()),hit);
        }
        return true;
    }
    public boolean isKeyInteractionActive() { return dragging!=null || marquee!=null || rootRows.isDragging(); }
    public void cancelKeyInteraction() { dragging=null; marquee=null; rootRows.cancel(); }
    private void renderSelectedKeyProperties() {
        if(selectedKeys.isEmpty()) return;
        float first=Float.POSITIVE_INFINITY,last=Float.NEGATIVE_INFINITY;
        var modes=new LinkedHashSet<InterpolationMode>();
        for(var entry:selectedKeys.entrySet()) {
            first=Math.min(first,entry.getKey().time()); last=Math.max(last,entry.getKey().time());
            if(source(entry.getValue()) instanceof ChannelKeyframe<?> frame) modes.add(frame.interpolation());
        }
        ImGui.text(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys.time_range",first,last));
        ImGui.text(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys.interpolation",
                modes.size()==1?tr("mode."+ modes.getFirst().name().toLowerCase(Locale.ROOT)):tr("mixed")));
        if(selectedKeys.size()==1) {
            var hit=selectedKeys.values().iterator().next();
            ImGui.text(hit.bone()+" · "+tr("channel."+hit.channel()));
        }
    }
    private boolean renderKeyActions(TimelineModel model) {
        var wrapper=model.getSelectedClip();
        var context=wrapper instanceof com.director.timeline.model.entity.EntityTimelineClip entity
                && entity.getEntityClip() instanceof EntityAnimationClip animation
                ? ActorAnimationEditContext.capture(model,entity.getParentTrack(),animation):null;
        boolean target=context!=null && context.isCurrent() && !ActorBonePropertiesPanel.hasNumericDraft()
                && !ActorBoneGizmoController.getInstance().isDragging();
        boolean hasSelection=target && selectedOwner==context.track() && selectedReference==context.clip()
                && selectedAsset==context.asset() && !selectedKeys.isEmpty();
        ImGui.text(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys.selected_count",selectedKeys.size()));
        renderSelectedKeyProperties();
        ImGui.beginDisabled(!hasSelection);
        boolean copy=ImGui.button(tr("copy")); ImGui.sameLine();
        boolean delete=ImGui.button(tr("delete")); ImGui.endDisabled();
        var clipboard=com.director.ui.actor.pose.ActorKeyframeClipboard.getInstance();
        var pasted=target?clipboard.at(context.asset(),context.assetTime(),
                (context.end()-context.start())*Math.max(.01f,context.speed())):List.<ActorAnimationAuthoringService.Paste>of();
        ImGui.beginDisabled(!target || pasted.isEmpty());
        boolean paste=ImGui.button(tr("paste")); ImGui.endDisabled();
        ImGui.textWrapped(tr("paste_hint"));
        Float numericDelta=timePanel.render(context,selectedKeys.keySet(),hasSelection);
        if(numericDelta!=null) {
            var previous=new ArrayList<>(selectedKeys.values()); selectedKeys.clear();
            for(var old:previous) {
                KeyHit hit= null;
                if (context != null) {
                    hit = new KeyHit(old.track(),old.bone(),old.x(),old.y(),old.time()+numericDelta/Math.max(.01f,context.speed()),
                            old.asset(),old.clip(),old.boneTrack(),old.channel(),old.assetTime()+numericDelta);
                }
                if (hit != null) {
                    selectedKeys.put(key(hit.bone(),hit.channel(),hit.assetTime()),hit);
                }
            }
            timePanel.cancel(); actionMenu=false; pendingKeys=List.of(); ImGui.closeCurrentPopup(); return true;
        }
        boolean interpolationChanged=com.director.ui.actor.pose.ActorKeyframeInterpolationPanel.render(context,selectedKeys.keySet(),hasSelection);
        boolean changed=interpolationChanged || (copy?executeKeyCommand(model,"copy"):delete?executeKeyCommand(model,"delete"):
                paste && executeKeyCommand(model, "paste"));
        if(changed) { actionMenu=false; pendingKeys=List.of(); ImGui.closeCurrentPopup(); }
        else if(delete || paste || copy) ImGui.textWrapped(tr("rejected"));
        ImGui.separator(); return changed;
    }
    private boolean renderCandidates(TimelineModel model) {
        if (pendingKeys.isEmpty() && !actionMenu) { timePanel.cancel(); return false; }
        boolean current = pendingModel == model && pendingRevision == model.getActorEditContextRevision()
                && pendingSelectionRevision == ActorBoneSelectionController.getInstance().getRevision()
                && pendingSettingsRevision == com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision()
                && pendingSharedRevision==sharedSelection.revision();
        if (ImGui.beginPopup(KEY_POPUP)) {
            try {
                if (!current || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                    ImGui.closeCurrentPopup(); actionMenu=false; pendingKeys = List.of(); return true;
                }
                if(renderKeyActions(model)) return true;
                ImGui.textWrapped(tr("choose"));
                int index=0;
                for (var candidate : pendingKeys) {
                    var hit=candidate.hit();
                    boolean valid=candidate.context()!=null && candidate.context().isBound()
                            && hit.asset().findBoneTrack(hit.bone()).orElse(null)==hit.boneTrack()
                            && Objects.equals(source(hit),candidate.source());
                    ImGui.beginDisabled(!valid);
                    String label=hit.clip().getName()+" · "+hit.asset().name()+" · "+tr("channel."+hit.channel())
                            +String.format(" · %.3fs / %.3fs###key%d",hit.time(),hit.assetTime(),index++);
                    boolean chosen=ImGui.selectable(label);
                    ImGui.endDisabled();
                    if (chosen) {
                        selectKey(hit,pendingAdditive);
                        model.setSelectedClip(hit.clip().getTimelineClip()); model.setCurrentTime(hit.time());
                        pendingKeys=List.of(); actionMenu=false; ImGui.closeCurrentPopup(); break;
                    }
                }
                ImGui.textWrapped(tr("choose_hint"));
            } finally { ImGui.endPopup(); }
            return true;
        }
        timePanel.cancel(); actionMenu=false; pendingKeys=List.of(); return false;
    }
    private static String tr(String key) {
        return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys."+key);
    }

    private static Object source(KeyHit hit) {
        return switch (hit.channel()) {
            case 0 -> hit.boneTrack().translation().findAt(hit.assetTime()).orElse(null);
            case 1 -> hit.boneTrack().rotation().findAt(hit.assetTime()).orElse(null);
            default -> hit.boneTrack().scale().findAt(hit.assetTime()).orElse(null);
        };
    }

    /** Preview only while held; the underlying asset changes once, on release. */
    public boolean updateDrag(TimelineModel model) {
        try { if (rootRows.renderMenu(model)) return true; if (rootRows.updateDrag(model)) return true; return updateDragInternal(model); } finally { publishSelection(model); }
    }
    private boolean updateDragInternal(TimelineModel model) {
        if (renderCandidates(model)) return true;
        if (dragging == null) return false;
        var hit = dragging;
        if (dragContext == null || dragContext.model() != model || !dragContext.isCurrent()
                || dragSharedRevision!=sharedSelection.revision()
                || dragSelectionRevision != ActorBoneSelectionController.getInstance().getRevision()
                || dragSettingsRevision != com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision()
                || dragKeys.values().stream().anyMatch(k -> k.context()==null || !k.context().isBound()
                        || k.hit().asset().findBoneTrack(k.hit().bone()).orElse(null)!=k.hit().boneTrack()
                        || !Objects.equals(source(k.hit()),k.source()))
                || ActorBonePropertiesPanel.hasNumericDraft() || ActorBoneGizmoController.getInstance().isDragging()
                || !hit.track().getActorTrackPayload().getActorRef().getActorId().equals(ActorBoneSelectionController.getInstance().getActorId())
                || !Objects.equals(hit.bone(), ActorBoneSelectionController.getInstance().getSelectedBoneId())
                || ImGui.isKeyPressed(ImGuiKey.Escape) || hit.track().isLocked()
                || hit.clip().getStartTime() != dragStart || hit.clip().getEndTime() != dragEnd
                || hit.clip().getSpeed() != dragSpeed || !Objects.equals(source(hit), dragSource)
                || (hit.clip().isLoop() && hit.asset().loopMode() != AnimationLoopMode.ONCE)
                || !model.getAllTracks().contains(hit.track())
                || !hit.track().getActorTrackPayload().getClips().contains(hit.clip())
                || AnimationAssetLibrary.getInstance().find(hit.asset().id()).orElse(null) != hit.asset()
                || !hit.clip().getAnimationId().equals(hit.asset().id()) || hit.clip().isMuted()) {
            dragging = null;
            return true;
        }
        float delta = dragDelta();
        float target = hit.assetTime()+delta;
        ImGui.setTooltip(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.keys.drag_group",dragKeys.size(),delta));
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            dragging = null;
            if (isCanvasWindowHovered() && Math.abs(ImGui.getMousePosX() - dragMouseX) >= 3) {

                boolean moved = ActorAnimationAuthoringService.move(dragContext,dragKeys.keySet(),delta);
                if (moved) {
                    selectedKeys.clear();
                    for (var frozen : dragKeys.values()) {
                        var old=frozen.hit();
                        var movedHit=new KeyHit(old.track(),old.bone(),old.x(),old.y(),
                                old.time()+delta/Math.max(.01f,dragSpeed),old.asset(),old.clip(),old.boneTrack(),old.channel(),old.assetTime()+delta);
                        selectedKeys.put(key(old.bone(),old.channel(),movedHit.assetTime()),movedHit);
                    }
                }
                if (moved) model.setCurrentTime(hit.clip().getStartTime() + target / Math.max(0.01f, hit.clip().getSpeed()));
            }
        } else if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) dragging = null;
        return true;
    }

    public TimelineRowLayout prepare(TimelineModel model, float height, float gap) {
        renderModel = model;
        importSelection(model);
        keyHits.clear();
        var tracks = model.getAllTracks();
        if(selectedOwner!=null && (model.getSelectedClip()!=selectedReference.getTimelineClip()
                || !tracks.contains(selectedOwner) || AnimationAssetLibrary.getInstance().find(selectedAsset.id()).orElse(null)!=selectedAsset)) selectedKeys.clear();
        selectedKeys.entrySet().removeIf(e -> source(e.getValue())==null
                || e.getValue().asset().findBoneTrack(e.getValue().bone()).orElse(null)!=e.getValue().boneTrack());
        expanded.retainAll(tracks);
        supported.clear();
        trackRigs.keySet().retainAll(tracks);
        int[] counts = new int[model.getTrackCount()];
        for (int i = 0; i < counts.length; i++) {
            var track = model.getTrackAt(i);
            var payload = track.getActorTrackPayload();
            if (payload == null || payload.getActorRef() == null) continue;
            var registry = ActorRegistryManager.getInstance();
            var actor = registry.getActor(payload.getActorRef().getActorId()).orElse(null);
            var rig = actor == null ? Optional.<com.director.actor.rig.skeleton.RigDefinition>empty()
                    : com.director.actor.rig.ActorRigCompatibility.resolveRig(actor);
            if (actor == null || track.getType() != com.director.timeline.model.TrackType.ACTOR) continue;
            supported.add(track);
            if (rig.isPresent()) trackRigs.put(track, rig.get());
            else trackRigs.remove(track);
            if (expanded.contains(track)) counts[i] = ActorRootTimelineRows.COUNT + rig.map(r -> boneRows(track,r).size()).orElse(0);
        }
        return new TimelineRowLayout(height, gap, CHILD_HEIGHT, counts);
    }

    public boolean supports(TimelineTrack track) { return supported.contains(track); }
    public void setExpanded(TimelineTrack track, boolean value) {
        if (value) expanded.add(track); else { expanded.remove(track); rootRows.cancel(); }
    }

    private com.director.actor.rig.skeleton.RigDefinition rigFor(TimelineTrack track) {
        return trackRigs.getOrDefault(track, HumanoidRig.DEFINITION);
    }

    /** The timeline's invisible canvas button owns ActiveId on mouse-down.
     * Keep popup/window occlusion checks, but do not let that button block its own controls. */
    private static boolean isCanvasWindowHovered() {
        return ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
    }

    public void render(ImDrawList draw, TimelineTrack track, float x, float contentX, float right,
                       float y, float height, float gap, TimelineRenderMath math) {
        if (!supports(track)) return;
        draw.addText(x + 8, y + 6, TimelineColors.COLOR_TEXT_LIGHT, expanded.contains(track) ? "v" : ">");
        if (!expanded.contains(track)) return;
        var selection = ActorBoneSelectionController.getInstance();
        var actorId = track.getActorTrackPayload().getActorRef().getActorId();
        rootRows.render(draw, renderModel, track, x, contentX, right, y + height + gap, math);
        var rig = trackRigs.get(track);
        if (rig == null) return;
        var rows=boneRows(track,rig);
        for (int i = 0; i < rows.size(); i++) {
            var row=rows.get(i);
            var bone = rig.bone(row.boneIndex());
            float top = y + height + gap + (ActorRootTimelineRows.COUNT + i) * CHILD_HEIGHT;
            var selectedMode=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getBoneGizmoMode();
            int selectedChannel=selectedMode==com.director.client.gizmo.GizmoMode.TRANSLATE?0:selectedMode==com.director.client.gizmo.GizmoMode.SCALE?2:1;
            boolean selected = actorId.equals(selection.getActorId()) && selection.isBoneSelected(bone.id()) && (row.channel()<0 || row.channel()==selectedChannel);
            draw.addRectFilled(x, top, right, top + CHILD_HEIGHT,
                    selected ? TimelineColors.rgba(48, 77, 100, 255) : TimelineColors.rgba(29, 32, 37, 255));
            int depth = 0;
            for (int parent = bone.parentIndex(); parent >= 0; parent = rig.bone(parent).parentIndex()) depth++;
            draw.pushClipRect(x, top, contentX - 24, top + CHILD_HEIGHT, true);
            String label=row.channel()<0 ? (bone.id().equals("root")?"模型根（局部）":bone.displayName()) : new String[]{"位置（局部）","旋转（局部）","缩放（局部）"}[row.channel()];
            if(row.channel()<0) draw.addText(x+12+depth*12,top+4,TimelineColors.COLOR_TEXT_LIGHT,
                    expandedBones.getOrDefault(track,Set.of()).contains(bone.id())?"v":">");
            draw.addText(x+28+depth*12+(row.channel()<0?0:12),top+4,TimelineColors.COLOR_TEXT_LIGHT,label);
            draw.popClipRect();
            var keyContext=ActorAnimationEditContext.resolve(renderModel,actorId);
            var mode=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getBoneGizmoMode();
            int channel=row.channel()<0 ? (mode==com.director.client.gizmo.GizmoMode.TRANSLATE ? 1 : mode==com.director.client.gizmo.GizmoMode.SCALE ? 4 : 2) : 1<<row.channel();
            boolean canKey=(keyContext!=null && keyContext.isCurrent() || keyContext==null && com.director.ui.actor.pose.ActorEmbeddedAnimation.target(renderModel,track)!=null) && !ActorBonePropertiesPanel.hasNumericDraft()
                    && !ActorBoneGizmoController.getInstance().isDragging()
                    && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                    && ActorBoneChannelLayout.supports(rig,row.boneIndex(),channel==1?0:channel==2?1:2);
            float bx=contentX-12,by=top+CHILD_HEIGHT/2;
            int color=canKey?TimelineColors.rgba(255,190,90,255):TimelineColors.rgba(100,100,100,255);
            boolean hasKey=false;
            if(keyContext!=null) {
                var authored=keyContext.asset().findBoneTrack(bone.id()).orElse(null);
                if(authored!=null) {
                    var keys=channel==1?authored.translation().keyframes():channel==2?authored.rotation().keyframes():authored.scale().keyframes();
                    hasKey=keys.stream().anyMatch(k->Math.abs(k.time()-keyContext.assetTime())<=1e-5f);
                }
            }
            if(hasKey) draw.addQuadFilled(bx,by-5,bx+5,by,bx,by+5,bx-5,by,color);
            else draw.addQuad(bx,by-5,bx+5,by,bx,by+5,bx-5,by,color);
            if(isCanvasWindowHovered() && Math.abs(ImGui.getMousePosX()-bx)<10 && Math.abs(ImGui.getMousePosY()-by)<10)
                ImGui.setTooltip(canKey ? "记录"+bone.displayName()+new String[]{"位置","旋转","缩放"}[channel==1?0:channel==2?1:2]+"关键帧" : "需要当前时刻可编辑的骨骼动画资源及支持的通道");
            draw.addLine(contentX, top, contentX, top + CHILD_HEIGHT, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.addLine(x, top + CHILD_HEIGHT, right, top + CHILD_HEIGHT, TimelineColors.COLOR_SEPARATOR_LINE);
            renderKeys(draw, track, bone.id(), contentX, right, top, math, row.channel());
        }
    }

    private void renderKeys(ImDrawList draw, TimelineTrack track, String bone, float left, float right,
                            float top, TimelineRenderMath math, int rowChannel) {
        draw.pushClipRect(left, top, right, top + CHILD_HEIGHT, true);
        try {
            for (var clip : com.director.ui.actor.pose.ActorEmbeddedAnimation.clips(track.getActorTrackPayload())) {
                if (!(clip instanceof EntityAnimationClip animation) || clip.isMuted()) continue;
                var asset = AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).orElse(null);
                if (asset == null || !asset.rigId().equals(rigFor(track).id())) continue;
                var boneTrack = asset.findBoneTrack(bone).orElse(null);
                if (boneTrack == null) continue;
                var channels = List.of(boneTrack.translation(), boneTrack.rotation(), boneTrack.scale());
                int[] colors = {TimelineColors.rgba(100, 190, 255, 255),
                        TimelineColors.rgba(255, 190, 90, 255), TimelineColors.rgba(140, 220, 130, 255)};
                for (int channel = 0; channel < channels.size(); channel++) {
                    if(rowChannel>=0 && channel!=rowChannel)continue;
                    float cy = rowChannel>=0 ? top+CHILD_HEIGHT/2 : top + 5 + channel * 7;
                    Set<Integer> pixels = new HashSet<>();
                    for (var key : channels.get(channel).keyframes()) {
                        var mode = animation.isLoop() ? asset.loopMode() : AnimationLoopMode.ONCE;
                        if (dragging != null && dragging.asset() == asset && dragKeys.containsKey(key(bone,channel,key.time()))) {
                            // Move one asset key; project its preview through every visible reference.
                            float target = key.time()+dragDelta();
                            for (float preview : ActorKeyframeProjection.times(target, asset.duration(), mode,
                                    clip.getStartTime(), clip.getEndTime(), animation.getSpeed(), math.xToTime(left), math.xToTime(right)))
                                draw.addCircle(math.timeToX(preview), cy, 5, colors[channel]);
                        }
                        for (float time : ActorKeyframeProjection.times(key.time(), asset.duration(), mode,
                                clip.getStartTime(), clip.getEndTime(), animation.getSpeed(),
                                math.xToTime(left), math.xToTime(right))) {
                            float px = math.timeToX(time);
                            if (pixels.add(Math.round(px))) draw.addCircleFilled(px, cy, 3, colors[channel]);
                            if(selectedAsset==asset && selectedKeys.containsKey(key(bone,channel,key.time())))
                                draw.addCircle(px,cy,4,TimelineColors.COLOR_TEXT_LIGHT);
                            keyHits.add(new KeyHit(track, bone, px, cy, time, asset, animation, boneTrack, channel, key.time()));
                            if (isCanvasWindowHovered() && Math.abs(ImGui.getMousePosX() - px) < 4
                                    && Math.abs(ImGui.getMousePosY() - cy) < 4) {
                                ImGui.setTooltip(asset.name() + " · " + new String[]{"位置", "旋转", "缩放"}[channel]
                                        + String.format(" · %.3fs\n", time)+tr("selection_hint"));
                            }
                        }
                    }
                }
            }
        } finally { draw.popClipRect(); }
    }

    /** Consume only a new click; ongoing timeline drags must still receive updates/releases. */
    public boolean handleClick(TimelineModel model, TimelineRowLayout layout, float x, float contentX,
                               float right, float top, float viewportHeight, float scrollY, float height, float gap,
                               float pixelsPerSecond, TimelineRenderMath math) {
        try { return handleClickInternal(model,layout,x,contentX,right,top,viewportHeight,scrollY,height,gap,pixelsPerSecond,math); }
        finally { publishSelection(model); }
    }
    private boolean handleClickInternal(TimelineModel model,TimelineRowLayout layout,float x,float contentX,
            float right,float top,float viewportHeight,float scrollY,float height,float gap,float pixelsPerSecond,TimelineRenderMath math) {
        if (!isCanvasWindowHovered() || (!ImGui.isMouseClicked(ImGuiMouseButton.Left)
                && !ImGui.isMouseClicked(ImGuiMouseButton.Right))) return false;
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        if (mx < x || mx >= right || my < top || my >= top + viewportHeight) return false;
        float contentY = my - top + scrollY;
        int owner = layout.ownerAt(contentY);
        if (owner < 0) return false;
        var track = model.getTrackAt(owner);
        if (!supports(track)) { keyFocus=false; return false; }
        float local = contentY - layout.trackTop(owner);
        if(local<height) { keyFocus=false; rootRows.clearFocus(); }
        if (local < height && mx < Math.min(contentX, x + 26)) {
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) { rootRows.cancel(); if (!expanded.remove(track)) expanded.add(track); }
            return true;
        }
        if (!expanded.contains(track) || local < height + gap) return false;
        int bone = (int) ((local - height - gap) / CHILD_HEIGHT);
        if (bone < ActorRootTimelineRows.COUNT) {
            keyFocus = false;
            boolean consumed = rootRows.click(model, track, mx, contentX, bone, math);
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && mx >= contentX && !rootRows.isDragging()) {
                boolean nearKey = ActorRootTimelineRows.clips(track).stream().flatMap(c -> c.getKeyframes().stream())
                        .anyMatch(k -> Math.abs(math.timeToX(k.getTime()) - mx) <= 7);
                if (!nearKey) rootRows.beginMarquee(model, track, mx, my, contentX, right, top, viewportHeight, scrollY,
                        top + layout.trackTop(owner) + height + gap - scrollY, math, ImGui.getIO().getKeyCtrl());
            }
            return consumed;
        }
        rootRows.clearFocus();
        bone -= ActorRootTimelineRows.COUNT;
        var rig = trackRigs.get(track);
        if (rig == null) return false;
        var rows=boneRows(track,rig);
        if(bone>=rows.size())return false;
        var row=rows.get(bone);
        final int boneIndex=row.boneIndex();
        int depth=0;
        for(int parent=rig.bone(boneIndex).parentIndex();parent>=0;parent=rig.bone(parent).parentIndex())depth++;
        if(row.channel()<0 && mx>=x+8+depth*12 && mx<x+26+depth*12 && ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            cancelKeyInteraction();var opened=expandedBones.computeIfAbsent(track,t->new HashSet<>());
            if(!opened.remove(rig.bone(boneIndex).id()))opened.add(rig.bone(boneIndex).id());return true;
        }
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) || ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            var preferredClip=model.getSelectedClip();
            boolean rightClick=ImGui.isMouseClicked(ImGuiMouseButton.Right);
            var actorId = track.getActorTrackPayload().getActorRef().getActorId();
            boolean additive=mx>=contentX && ImGui.getIO().getKeyCtrl();
            if(row.channel()>=0) com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().setBoneGizmoMode(
                    new com.director.client.gizmo.GizmoMode[]{com.director.client.gizmo.GizmoMode.TRANSLATE,com.director.client.gizmo.GizmoMode.ROTATE,com.director.client.gizmo.GizmoMode.SCALE}[row.channel()]);
            ActorSelectionHub.selectBone(actorId, rig.bone(boneIndex).id(), mx<contentX && ImGui.getIO().getKeyCtrl());
            if(!rightClick && mx>=contentX-24 && mx<contentX) {
                if(preferredClip!=null && preferredClip.getParentTrack()==track) model.setSelectedClip(preferredClip);
                var context=ActorAnimationEditContext.resolve(model,actorId);
                if(context==null) {
                    var settings=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance();
                    var mode=settings.getBoneGizmoMode();int channel=mode==com.director.client.gizmo.GizmoMode.TRANSLATE?1:mode==com.director.client.gizmo.GizmoMode.SCALE?4:2;
                    try { com.director.ui.actor.pose.ActorEmbeddedAnimation.firstKey(model,track,rig.bone(boneIndex).id(),channel,settings.getInterpolation()); }
                    catch(IllegalArgumentException failure) { ImGui.setTooltip(failure.getMessage()); }
                    return true;
                }
                if(context.isCurrent() && !ActorBonePropertiesPanel.hasNumericDraft()
                        && !ActorBoneGizmoController.getInstance().isDragging()
                        && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()) {
                    var settings=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance();
                    var mode=settings.getBoneGizmoMode();
                    int channel=mode==com.director.client.gizmo.GizmoMode.TRANSLATE?1:mode==com.director.client.gizmo.GizmoMode.SCALE?4:2;
                    var pose=context.asset().sample(context.assetTime(),com.director.actor.animation.asset.AnimationLoopMode.ONCE);
                    ActorAnimationAuthoringService.keyPose(context,pose,List.of(rig.bone(boneIndex).id()),settings.getInterpolation(),channel);
                }
                return true;
            }
            if(mx<contentX) { keyFocus=false; selectedKeys.clear(); }
            model.selectItem(track, false);
            if (mx >= contentX) {
                var points = new ArrayList<ActorKeyframeHitCandidates.Point<KeyHit>>();
                for (var hit : keyHits) {
                    if (hit.track() == track && hit.bone().equals(rig.bone(boneIndex).id()) && (row.channel()<0 || hit.channel()==row.channel()))
                        points.add(new ActorKeyframeHitCandidates.Point<>(hit,hit.x(),hit.y()));
                }
                var candidates=ActorKeyframeHitCandidates.near(points,mx,my,6);
                var preferred=candidates.stream().filter(hit -> hit.clip().getTimelineClip()==preferredClip).toList();
                if (!rightClick && preferred.size()==1) candidates=preferred;
                if (candidates.size()>1 || rightClick) {
                    if(rightClick && preferredClip!=null && preferredClip.getParentTrack()==track) model.setSelectedClip(preferredClip);
                    var pending=new ArrayList<PendingKey>();
                    for(var hit:candidates) pending.add(new PendingKey(hit,
                            ActorAnimationEditContext.capture(model,track,hit.clip()),source(hit)));
                    publishSelection(model);
                    pendingSharedRevision=sharedSelection.revision();
                    pendingKeys=List.copyOf(pending); pendingModel=model;
                    pendingRevision=model.getActorEditContextRevision();
                    pendingSelectionRevision=ActorBoneSelectionController.getInstance().getRevision();
                    pendingSettingsRevision=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision();
                    actionMenu=true;
                    pendingAdditive=additive;
                    dragging=null; ImGui.openPopup(KEY_POPUP); return true;
                }
                KeyHit closest=candidates.isEmpty()?null:candidates.getFirst();
                if (closest != null) {
                    selectKey(closest,additive);
                    model.setSelectedClip(closest.clip().getTimelineClip());
                    model.setCurrentTime(closest.time());
                    if (!additive && !model.isPlaying() && !track.isLocked() && !ActorBonePropertiesPanel.hasNumericDraft()
                            && !ActorBoneGizmoController.getInstance().isDragging() && (!closest.clip().isLoop() || closest.asset().loopMode() == AnimationLoopMode.ONCE)) {
                        cancelAllKeyInteractions();
                        com.director.ui.actor.pose.ActorAnimationDopeSheet.cancelKeyInteraction();
                        com.director.ui.actor.pose.ActorKeyframeTimePanel.cancelAll();
                        publishSelection(model);
                        dragSharedRevision=sharedSelection.revision();
                        dragContext = ActorAnimationEditContext.capture(model, track, closest.clip());
                        if (dragContext == null || !dragContext.isCurrent()) return true;
                        dragSelectionRevision = ActorBoneSelectionController.getInstance().getRevision();
                        dragSettingsRevision = com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision();
                        var frozen=new LinkedHashMap<ActorAnimationAuthoringService.Key,PendingKey>();
                        for(var entry:selectedKeys.entrySet()) frozen.put(entry.getKey(),new PendingKey(entry.getValue(),
                                ActorAnimationEditContext.capture(model,track,closest.clip()),source(entry.getValue())));
                        dragKeys=Collections.unmodifiableMap(frozen);
                        dragging = closest;
                        dragMouseX = mx;
                        dragPixelsPerSecond = pixelsPerSecond;
                        dragStart = closest.clip().getStartTime();
                        dragEnd = closest.clip().getEndTime();
                        dragSpeed = closest.clip().getSpeed();
                        dragSource = source(closest);
                    }
                } else {
                    if(preferredClip instanceof com.director.timeline.model.entity.EntityTimelineClip wrapper
                            && wrapper.getParentTrack()==track && wrapper.getEntityClip() instanceof EntityAnimationClip animation) {
                        model.setSelectedClip(wrapper);
                        var context=ActorAnimationEditContext.capture(model,track,animation);
                        if(context!=null && context.isBound() && !ActorBonePropertiesPanel.hasNumericDraft()
                                && !ActorBoneGizmoController.getInstance().isDragging()) {
                            var frozen=new ArrayList<PendingKey>();
                            for(var hit:keyHits) if(hit.track()==track && hit.clip()==animation)
                                frozen.add(new PendingKey(hit,context,source(hit)));
                            float rowsTop=Math.max(top,top+layout.trackTop(owner)+height+gap+ActorRootTimelineRows.COUNT*CHILD_HEIGHT-scrollY);
                            float rowsBottom=Math.min(top+viewportHeight,top+layout.trackTop(owner)+height+gap+(ActorRootTimelineRows.COUNT+rows.size())*CHILD_HEIGHT-scrollY);
                            focusReference(track,animation,context.asset());
                            cancelAllKeyInteractions();
                            com.director.ui.actor.pose.ActorAnimationDopeSheet.cancelKeyInteraction();
                            com.director.ui.actor.pose.ActorKeyframeTimePanel.cancelAll();
                            publishSelection(model);
                            if(rowsTop<rowsBottom) marquee=new Marquee(context,ActorBoneSelectionController.getInstance().getRevision(),
                                    com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision(),sharedSelection.revision(),mx,my,
                                    contentX,right,rowsTop,rowsBottom,additive && selectedReference==animation,
                                    viewport(contentX,right,top,viewportHeight,scrollY,math),List.copyOf(model.getAllTracks()),List.copyOf(frozen));
                        }
                    } else {
                        if(!additive) selectedKeys.clear();
                        ImGui.setTooltip(tr("marquee_reference"));
                    }
                }
            }
        }
        return true;
    }
}
