package com.director.ui.lighting;

import com.director.client.gizmo.*;
import com.director.client.input.DirectorInputSystem;
import com.director.lighting.*;
import com.director.lighting.PointLightRuntime;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Objects;

public final class PointLightController {
    private static final PointLightController INSTANCE=new PointLightController();
    private boolean active, placing, leftDown, rightDown;
    private TimelineModel owner;
    private ClientWorld world;
    private long revision;
    private PointLight dragBefore;
    private LightKeyframe dragKey;
    private LightTimelineClip dragClip;
    private LightType placementType=LightType.POINT;
    private final java.util.ArrayList<LightPoint> draft=new java.util.ArrayList<>();
    private int anchorIndex=-1;
    public LightType placementType() { return placementType; }
    public List<LightPoint> draft() { return List.copyOf(draft); }
    public static PointLightController get() { return INSTANCE; }
    private PointLightController() {}
    public boolean isActive() { return active; }
    public boolean isPlacing() { return placing; }
    public void startPlacement() { startPlacement(LightType.POINT); }
    public void startPlacement(LightType type) {
        PointLightParameterEditing.finish();finishDrag();enter();placing=true;placementType=type;draft.clear();anchorIndex=-1;
        if(ownsGizmo()) GizmoManager.get().deactivate();
    }
    private void enter() {
        if (!active) {
            com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
            var actorGizmo=com.director.ui.actor.gizmo.ActorGizmoController.getInstance();
            if(actorGizmo.isDragging()) actorGizmo.cancelGizmoDrag();
            GizmoManager.get().deactivate();
            active=true;
            owner=PointLightEditor.model(); world=MinecraftClient.getInstance().world;
            revision=owner==null?0:owner.getActorEditContextRevision();
            // Seed button state: the panel click that enters this mode must not select the world.
            var client=MinecraftClient.getInstance();
            leftDown=down(client,GLFW.GLFW_MOUSE_BUTTON_LEFT); rightDown=down(client,GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        }
    }
    public void select(PointLight light) {
        finishDrag(); enter(); placing=false; draft.clear();anchorIndex=-1;PointLightEditor.select(light); attach(light);
    }
    public void inspectTimeline() {
        finishDrag();enter();placing=false;draft.clear();anchorIndex=-1;
        var light=PointLightEditor.selected();if(light!=null) attach(light);
    }
    public void stop() {
        cancelDrag();PointLightParameterEditing.finish(); active=false; placing=false;draft.clear();anchorIndex=-1;owner=null; world=null;
        if (ownsGizmo()) GizmoManager.get().deactivate();
    }
    public void update() {
        if (!active) return;
        var client=MinecraftClient.getInstance();
        if (owner==null || (owner!=PointLightEditor.model() || owner.getActorEditContextRevision()!=revision) || world!=client.world || client.world==null) { stop(); return; }
        boolean left=down(client,GLFW.GLFW_MOUSE_BUTTON_LEFT), right=down(client,GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean leftPressed=left&&!leftDown, rightPressed=right&&!rightDown;
        leftDown=left;rightDown=right;
        if (!ImGui.getIO().getWantTextInput()
                && GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_ESCAPE)==GLFW.GLFW_PRESS) { stop(); return; }
        if (com.director.export.video.VideoExportManager.getInstance().isExporting()) return;
        PointLight light=PointLightEditor.selected();
        if (!placing && light==null) { stop(); return; }
        if (!placing && dragBefore == null) attach(light);
        if (light==null && ownsGizmo()) { cancelDrag(); GizmoManager.get().deactivate(); }
        if (ImGui.getIO().getWantCaptureMouse() || ImGui.getIO().getWantTextInput()) return;
        if (placing && rightPressed) {
            var hit=DirectorInputSystem.raycastFromImGui();
            if (hit!=null && hit.getType()==HitResult.Type.BLOCK) {
                Vec3d p=hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(.35));
                if(placementType==LightType.LINE) {
                    if(draft.size()<128 && (draft.isEmpty() || new Vec3d(draft.getLast().x(),draft.getLast().y(),draft.getLast().z()).squaredDistanceTo(p)>1e-6))
                        draft.add(new LightPoint(p.x,p.y,p.z));
                } else {
                    Vec3d n=Vec3d.of(hit.getSide().getVector());
                    PointLight created=placementType==LightType.AREA
                        ? PointLight.createArea(p.x,p.y,p.z,(float)Math.toDegrees(Math.atan2(n.x,n.z)),(float)-Math.toDegrees(Math.asin(n.y)))
                        : PointLight.create(p.x,p.y,p.z);
                    PointLightEditor.change(owner,null,created); select(created);
                }
            }
            return;
        }
        if(placing && placementType==LightType.LINE && leftPressed) {
            if(draft.size()>=2) { var created=PointLight.createLine(draft);PointLightEditor.change(owner,null,created);select(created); }
            return;
        }
        if (leftPressed && !GizmoManager.get().isHot() && !GizmoManager.get().isDraggingHandle()) {
            var ray=DirectorInputSystem.computeImGuiRay();
            if (ray==null) return;
            PointLight nearest=null;int nearestAnchor=-1; double distance=Double.POSITIVE_INFINITY;
            for (PointLight asset:owner.getPointLights()) {
                PointLight candidate=light!=null && light.id().equals(asset.id())?light:asset;
                var points=candidate.type()==LightType.LINE?LightGeometry.vertices(candidate):List.of(new LightPoint(candidate.x(),candidate.y(),candidate.z()));
                for(int i=0;i<points.size();i++) {
                    var vertex=points.get(i);Vec3d p=new Vec3d(vertex.x(),vertex.y(),vertex.z());
                    var intersection=markerBox(p).raycast(ray.origin(),ray.origin().add(ray.direction().multiply(256)));
                    if(intersection.isPresent()) {
                        double d=intersection.get().squaredDistanceTo(ray.origin());
                        if(d<distance) { distance=d;nearest=candidate;nearestAnchor=candidate.type()==LightType.LINE?i:-1; }
                    }
                }
            }
            if(nearest!=null) {
                var payload=com.director.core.DirectorServices.get().uiSelection().getSelectionPayload();
                if(!(payload instanceof LightKeyframe key && key.value().id().equals(nearest.id()))) select(nearest);
                anchorIndex=nearestAnchor;attach(nearest);
            }
            else { stop();com.director.core.DirectorServices.get().uiSelection().clearSelection(); }
        }
    }
    private void attach(PointLight light) {
        var gm=GizmoManager.get();
        if (gm.isDraggingHandle()) return;
        if (!ownsGizmo()) {
            gm.activate(GizmoType.TRANSFORM,markerBox(targetPosition(light)),targetPosition(light));
            gm.getContext().setMode(GizmoMode.TRANSLATE);
            gm.getController().setRotationHandles(List.of());
            gm.getController().setTransformApplier(new PointLightTransformApplier(this));
        } else { gm.setTargetBox(markerBox(targetPosition(light)));gm.setPivot(targetPosition(light)); }
    }
    private boolean ownsGizmo() {
        var gm=GizmoManager.get();
        return gm.isActive() && gm.getController()!=null && gm.getController().getTransformApplier() instanceof PointLightTransformApplier;
    }
    boolean beginDrag() {
        PointLightParameterEditing.finish();
        if (!active || (owner!=PointLightEditor.model() || owner.getActorEditContextRevision()!=revision) || world!=MinecraftClient.getInstance().world) return false;
        dragKey=null;dragClip=null;
        var payload=com.director.core.DirectorServices.get().uiSelection().getSelectionPayload();
        if(payload instanceof LightKeyframe key) {
            for(var track:owner.getTracks()) for(var clip:track.getClips())
                if(clip instanceof LightTimelineClip light && light.getClipKeyframes().contains(key)) {
                    if(track.isLocked()) return false;dragKey=key;dragClip=light;
                }
            if(dragClip==null) return false;
        }
        dragBefore=PointLightEditor.selected();return dragBefore!=null;
    }
    void move(Vec3d delta) {
        if (dragBefore==null || (owner!=PointLightEditor.model() || owner.getActorEditContextRevision()!=revision) || world!=MinecraftClient.getInstance().world) return;
        var current=dragKey==null?owner.getPointLight(dragBefore.id()):dragKey.value();
        if(current==null || !current.id().equals(PointLightEditor.selected()==null?null: Objects.requireNonNull(PointLightEditor.selected()).id())) return;
        Vec3d p=targetPosition(current).add(delta);
        var next=anchorIndex>=0 && current.type()==LightType.LINE
            ? current.withAnchor(anchorIndex,new LightPoint(p.x,p.y,p.z)) : current.at(p.x,p.y,p.z);
        if(dragKey==null) owner.replacePointLight(current.id(),next);
        else dragKey.setValue(next);
        PointLightRuntime.update(owner);
        GizmoManager.get().setTargetBox(markerBox(p));
        GizmoManager.get().setPivot(p);
    }
    void finishDrag() {
        PointLight before=dragBefore;dragBefore=null;
        if(dragKey!=null) {
            var key=dragKey;var clip=dragClip;dragKey=null;dragClip=null;
            if(before!=null && owner==PointLightEditor.model() && owner.getActorEditContextRevision()==revision
                && world==MinecraftClient.getInstance().world && clip!=null && owner.findTrackForClip(clip)!=null
                && clip.getClipKeyframes().contains(key) && !before.equals(key.value())) {
                var after=key.value();var model=owner;
                com.director.ui.timeline.commands.TimelineCommandBridge.execute(new com.director.ui.timeline.commands.StateChangeCommand(model,"Move light keyframe",
                    () -> {key.setValue(before);PointLightRuntime.update(model);},
                    () -> {key.setValue(after);PointLightRuntime.update(model);}));
            }
            return;
        }
        if (before!=null && owner!=null && owner==PointLightEditor.model() && owner.getActorEditContextRevision()==revision && world==MinecraftClient.getInstance().world)
            PointLightEditor.change(owner,before,owner.getPointLight(before.id()));
    }
    public void cancelDrag() {
        PointLight before=dragBefore;dragBefore=null;
        if(dragKey!=null) {
            if(before!=null && owner==PointLightEditor.model() && owner.getActorEditContextRevision()==revision && dragClip!=null
                && owner.findTrackForClip(dragClip)!=null && dragClip.getClipKeyframes().contains(dragKey)) dragKey.setValue(before);
            dragKey=null;dragClip=null;PointLightParameterEditing.finish();PointLightRuntime.update(owner);return;
        }
        if (before!=null && owner!=null && owner==PointLightEditor.model() && owner.getActorEditContextRevision()==revision && world==MinecraftClient.getInstance().world) {
            // A timeline reset/deletion must never resurrect an obsolete light.
            if(owner.getPointLight(before.id())!=null) owner.replacePointLight(before.id(),before);
            PointLightRuntime.update(owner);
        }
    }
    private Vec3d targetPosition(PointLight light) {
        if(anchorIndex>=0 && light.type()==LightType.LINE && anchorIndex<light.anchors().size()) {
            var p=light.anchors().get(anchorIndex);return new Vec3d(light.x()+p.x(),light.y()+p.y(),light.z()+p.z());
        }
        return position(light);
    }
    private static Box markerBox(Vec3d p) { return new Box(p.x-.25,p.y-.25,p.z-.25,p.x+.25,p.y+.25,p.z+.25); }
    public static Vec3d position(PointLight light) { return new Vec3d(light.x(),light.y(),light.z()); }
    public static Box box(PointLight light) { Vec3d p=position(light);return new Box(p.x-.25,p.y-.25,p.z-.25,p.x+.25,p.y+.25,p.z+.25); }
    private static boolean down(MinecraftClient client,int button) {
        return client.getWindow()!=null && GLFW.glfwGetMouseButton(client.getWindow().getHandle(),button)==GLFW.GLFW_PRESS;
    }
}
