package com.director.ui.lighting;

import com.director.client.gizmo.*;
import com.director.client.input.DirectorInputSystem;
import com.director.lighting.PointLight;
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

public final class PointLightController {
    private static final PointLightController INSTANCE=new PointLightController();
    private boolean active, placing, leftDown, rightDown;
    private TimelineModel owner;
    private ClientWorld world;
    private long revision;
    private PointLight dragBefore;
    public static PointLightController get() { return INSTANCE; }
    private PointLightController() {}
    public boolean isActive() { return active; }
    public boolean isPlacing() { return placing; }
    public void startPlacement() { finishDrag();enter();placing=true;if(ownsGizmo()) GizmoManager.get().deactivate(); }
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
        finishDrag(); enter(); placing=false; PointLightEditor.select(light); attach(light);
    }
    public void stop() {
        PointLightParameterEditing.finish();
        cancelDrag(); active=false; placing=false; owner=null; world=null;
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
        if (!placing && light!=null && dragBefore==null) attach(light);
        if (light==null && ownsGizmo()) { cancelDrag(); GizmoManager.get().deactivate(); }
        if (ImGui.getIO().getWantCaptureMouse() || ImGui.getIO().getWantTextInput()) return;
        if (placing && rightPressed) {
            var hit=DirectorInputSystem.raycastFromImGui();
            if (hit!=null && hit.getType()==HitResult.Type.BLOCK) {
                Vec3d p=hit.getPos().add(Vec3d.of(hit.getSide().getVector()).multiply(.35));
                PointLight created=PointLight.create(p.x,p.y,p.z);
                PointLightEditor.change(owner,null,created); select(created);
            }
            return;
        }
        if (leftPressed && !GizmoManager.get().isHot() && !GizmoManager.get().isDraggingHandle()) {
            var ray=DirectorInputSystem.computeImGuiRay();
            if (ray==null) return;
            PointLight nearest=null; double distance=Double.POSITIVE_INFINITY;
            for (PointLight candidate:owner.getPointLights()) {
                var intersection=box(candidate).raycast(ray.origin(),ray.origin().add(ray.direction().multiply(256)));
                if (intersection.isPresent()) {
                    double d=intersection.get().squaredDistanceTo(ray.origin());
                    if(d<distance) { distance=d;nearest=candidate; }
                }
            }
            if(nearest!=null) select(nearest);
            else { stop();com.director.core.DirectorServices.get().uiSelection().clearSelection(); }
        }
    }
    private void attach(PointLight light) {
        var gm=GizmoManager.get();
        if (gm.isDraggingHandle()) return;
        if (!ownsGizmo()) {
            gm.activate(GizmoType.TRANSFORM,box(light),position(light));
            gm.getContext().setMode(GizmoMode.TRANSLATE);
            gm.getController().setRotationHandles(List.of());
            gm.getController().setTransformApplier(new PointLightTransformApplier(this));
        } else { gm.setTargetBox(box(light));gm.setPivot(position(light)); }
    }
    private boolean ownsGizmo() {
        var gm=GizmoManager.get();
        return gm.isActive() && gm.getController()!=null && gm.getController().getTransformApplier() instanceof PointLightTransformApplier;
    }
    boolean beginDrag() {
        PointLightParameterEditing.finish();
        if (!active || (owner!=PointLightEditor.model() || owner.getActorEditContextRevision()!=revision) || world!=MinecraftClient.getInstance().world) return false;
        dragBefore=PointLightEditor.selected();return dragBefore!=null;
    }
    void move(Vec3d delta) {
        if (dragBefore==null || (owner!=PointLightEditor.model() || owner.getActorEditContextRevision()!=revision) || world!=MinecraftClient.getInstance().world) return;
        var current=owner.getPointLight(dragBefore.id());
        if(current==null || !current.id().equals(PointLightEditor.selected()==null?null:PointLightEditor.selected().id())) return;
        Vec3d p=position(current).add(delta);
        owner.replacePointLight(current.id(),current.at(p.x,p.y,p.z));
        PointLightRuntime.update(owner);
        GizmoManager.get().setTargetBox(box(owner.getPointLight(current.id())));
        GizmoManager.get().setPivot(p);
    }
    void finishDrag() {
        PointLight before=dragBefore;dragBefore=null;
        if (before!=null && owner!=null && owner==PointLightEditor.model() && owner.getActorEditContextRevision()==revision && world==MinecraftClient.getInstance().world)
            PointLightEditor.change(owner,before,owner.getPointLight(before.id()));
    }
    public void cancelDrag() {
        PointLight before=dragBefore;dragBefore=null;
        if (before!=null && owner!=null && owner==PointLightEditor.model() && owner.getActorEditContextRevision()==revision && world==MinecraftClient.getInstance().world) {
            // A timeline reset/deletion must never resurrect an obsolete light.
            if(owner.getPointLight(before.id())!=null) owner.replacePointLight(before.id(),before);
            PointLightRuntime.update(owner);
        }
    }
    public static Vec3d position(PointLight light) { return new Vec3d(light.x(),light.y(),light.z()); }
    public static Box box(PointLight light) { Vec3d p=position(light);return new Box(p.x-.25,p.y-.25,p.z-.25,p.x+.25,p.y+.25,p.z+.25); }
    private static boolean down(MinecraftClient client,int button) {
        return client.getWindow()!=null && GLFW.glfwGetMouseButton(client.getWindow().getHandle(),button)==GLFW.GLFW_PRESS;
    }
}
