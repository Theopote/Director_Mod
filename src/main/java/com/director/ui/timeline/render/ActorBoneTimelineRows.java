package com.director.ui.timeline.render;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.asset.*;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.actor.rig.HumanoidRigProfile;
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
    public static final float CHILD_HEIGHT = 24f;
    private final Set<TimelineTrack> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TimelineTrack> supported = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TimelineTrack, com.director.actor.rig.skeleton.RigDefinition> trackRigs = new IdentityHashMap<>();
    private record KeyHit(TimelineTrack track, String bone, float x, float y, float time,
                          AnimationAsset asset, EntityAnimationClip clip, BoneTrack boneTrack, int channel, float assetTime) {}
    private final List<KeyHit> keyHits = new ArrayList<>();
    private KeyHit dragging;
    private float dragMouseX;
    private float dragPixelsPerSecond;
    private float dragStart, dragEnd, dragSpeed;
    private Object dragSource;
    private final Map<ActorAnimationAuthoringService.Key, KeyHit> selectedKeys = new LinkedHashMap<>();
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
        selectedOwner=hit.track(); selectedReference=hit.clip(); selectedAsset=hit.asset();
        var key=key(hit.bone(),hit.channel(),hit.assetTime());
        if(additive && selectedKeys.containsKey(key)) selectedKeys.remove(key);
        else { if(!additive && !selectedKeys.containsKey(key)) selectedKeys.clear(); selectedKeys.put(key,hit); }
    }
    private float dragDelta() {
        return ActorKeyframeDrag.groupDelta(dragKeys.keySet().stream().map(ActorAnimationAuthoringService.Key::time).toList(),
                ImGui.getMousePosX()-dragMouseX,dragPixelsPerSecond,dragSpeed,dragging.asset().duration(),dragEnd-dragStart);
    }
    private ActorAnimationEditContext dragContext;
    private long dragSelectionRevision, dragSettingsRevision;
    private static final String KEY_POPUP = "ActorKeyCandidates";
    private record PendingKey(KeyHit hit, ActorAnimationEditContext context, Object source) {}
    private List<PendingKey> pendingKeys = List.of();
    private TimelineModel pendingModel;
    private long pendingRevision, pendingSelectionRevision, pendingSettingsRevision;

    private boolean renderCandidates(TimelineModel model) {
        if (pendingKeys.isEmpty()) return false;
        boolean current = pendingModel == model && pendingRevision == model.getActorEditContextRevision()
                && pendingSelectionRevision == ActorBoneSelectionController.getInstance().getRevision()
                && pendingSettingsRevision == com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision();
        if (ImGui.beginPopup(KEY_POPUP)) {
            try {
                if (!current || ImGui.isKeyPressed(ImGuiKey.Escape)) {
                    ImGui.closeCurrentPopup(); pendingKeys = List.of(); return true;
                }
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
                        pendingKeys=List.of(); ImGui.closeCurrentPopup(); break;
                    }
                }
                ImGui.textWrapped(tr("choose_hint"));
            } finally { ImGui.endPopup(); }
            return true;
        }
        pendingKeys=List.of(); return false;
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
        if (renderCandidates(model)) return true;
        if (dragging == null) return false;
        var hit = dragging;
        if (dragContext == null || dragContext.model() != model || !dragContext.isCurrent()
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
            if (rig.isEmpty()) continue;
            supported.add(track);
            trackRigs.put(track, rig.get());
            if (expanded.contains(track)) counts[i] = rig.get().boneCount();
        }
        return new TimelineRowLayout(height, gap, CHILD_HEIGHT, counts);
    }

    public boolean supports(TimelineTrack track) { return supported.contains(track); }

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
        var rig = rigFor(track);
        for (int i = 0; i < rig.boneCount(); i++) {
            var bone = rig.bone(i);
            float top = y + height + gap + i * CHILD_HEIGHT;
            boolean selected = actorId.equals(selection.getActorId()) && selection.isBoneSelected(bone.id());
            draw.addRectFilled(x, top, right, top + CHILD_HEIGHT,
                    selected ? TimelineColors.rgba(48, 77, 100, 255) : TimelineColors.rgba(29, 32, 37, 255));
            int depth = 0;
            for (int parent = bone.parentIndex(); parent >= 0; parent = rig.bone(parent).parentIndex()) depth++;
            draw.pushClipRect(x, top, contentX, top + CHILD_HEIGHT, true);
            draw.addText(x + 16 + depth * 12, top + 4, TimelineColors.COLOR_TEXT_LIGHT, bone.displayName());
            draw.popClipRect();
            draw.addLine(contentX, top, contentX, top + CHILD_HEIGHT, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.addLine(x, top + CHILD_HEIGHT, right, top + CHILD_HEIGHT, TimelineColors.COLOR_SEPARATOR_LINE);
            renderKeys(draw, track, bone.id(), contentX, right, top, math);
        }
    }

    private void renderKeys(ImDrawList draw, TimelineTrack track, String bone, float left, float right,
                            float top, TimelineRenderMath math) {
        draw.pushClipRect(left, top, right, top + CHILD_HEIGHT, true);
        try {
            for (var clip : track.getActorTrackPayload().getClips()) {
                if (!(clip instanceof EntityAnimationClip animation) || clip.isMuted()) continue;
                var asset = AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).orElse(null);
                if (asset == null || !asset.rigId().equals(rigFor(track).id())) continue;
                var boneTrack = asset.findBoneTrack(bone).orElse(null);
                if (boneTrack == null) continue;
                var channels = List.of(boneTrack.translation(), boneTrack.rotation(), boneTrack.scale());
                int[] colors = {TimelineColors.rgba(100, 190, 255, 255),
                        TimelineColors.rgba(255, 190, 90, 255), TimelineColors.rgba(140, 220, 130, 255)};
                for (int channel = 0; channel < channels.size(); channel++) {
                    float cy = top + 5 + channel * 7;
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
                               float pixelsPerSecond) {
        if (!isCanvasWindowHovered() || (!ImGui.isMouseClicked(ImGuiMouseButton.Left)
                && !ImGui.isMouseClicked(ImGuiMouseButton.Right))) return false;
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        if (mx < x || mx >= right || my < top || my >= top + viewportHeight) return false;
        float contentY = my - top + scrollY;
        int owner = layout.ownerAt(contentY);
        if (owner < 0) return false;
        var track = model.getTrackAt(owner);
        if (!supports(track)) return false;
        float local = contentY - layout.trackTop(owner);
        if (local < height && mx < Math.min(contentX, x + 26)) {
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !expanded.remove(track)) expanded.add(track);
            return true;
        }
        if (!expanded.contains(track) || local < height + gap) return false;
        int bone = (int) ((local - height - gap) / CHILD_HEIGHT);
        var rig = rigFor(track);
        if (bone >= rig.boneCount()) return false;
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) || ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            var preferredClip=model.getSelectedClip();
            boolean rightClick=ImGui.isMouseClicked(ImGuiMouseButton.Right);
            var actorId = track.getActorTrackPayload().getActorRef().getActorId();
            boolean additive=mx>=contentX && ImGui.getIO().getKeyCtrl();
            ActorSelectionHub.selectBone(actorId, rig.bone(bone).id(), mx<contentX && ImGui.getIO().getKeyCtrl());
            if(mx<contentX) selectedKeys.clear();
            model.selectItem(track, false);
            if (mx >= contentX) {
                var points = new ArrayList<ActorKeyframeHitCandidates.Point<KeyHit>>();
                for (var hit : keyHits) {
                    if (hit.track() == track && hit.bone().equals(rig.bone(bone).id()))
                        points.add(new ActorKeyframeHitCandidates.Point<>(hit,hit.x(),hit.y()));
                }
                var candidates=ActorKeyframeHitCandidates.near(points,mx,my,6);
                var preferred=candidates.stream().filter(hit -> hit.clip().getTimelineClip()==preferredClip).toList();
                if (!rightClick && preferred.size()==1) candidates=preferred;
                if (candidates.size()>1 || (rightClick && !candidates.isEmpty())) {
                    var pending=new ArrayList<PendingKey>();
                    for(var hit:candidates) pending.add(new PendingKey(hit,
                            ActorAnimationEditContext.capture(model,track,hit.clip()),source(hit)));
                    pendingKeys=List.copyOf(pending); pendingModel=model;
                    pendingRevision=model.getActorEditContextRevision();
                    pendingSelectionRevision=ActorBoneSelectionController.getInstance().getRevision();
                    pendingSettingsRevision=com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance().getRevision();
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
                } else if(!rightClick && !additive) selectedKeys.clear();
            }
        }
        return true;
    }
}
