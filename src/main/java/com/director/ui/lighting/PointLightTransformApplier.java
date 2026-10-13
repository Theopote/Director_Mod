package com.director.ui.lighting;
import com.director.client.gizmo.Axis;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.core.DirectorServices;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
public final class PointLightTransformApplier extends TransformApplier {
    private final PointLightController controller;
    private boolean accepted;
    PointLightTransformApplier(PointLightController controller) { super(DirectorServices.get().selection());this.controller=controller; }
    @Override public void beginDragSession() { accepted=controller.beginDrag(); }
    @Override public void resetAccumulators() { super.resetAccumulators();controller.finishDrag();accepted=false; }
    @Override public void applyMove(Vec3d delta) { if(accepted && delta!=null) controller.move(delta); }
    @Override public void applyResize(Axis axis,double delta) {}
    @Override public void applyResize(Axis axis,boolean max,double delta) {}
    @Override public void applyResize(Vec3d delta) {}
    @Override public void applyResizeFromCorner(Vec3d delta,Vec3i sign) {}
    @Override public void applyRotation(double angle,Axis axis) {}
}
