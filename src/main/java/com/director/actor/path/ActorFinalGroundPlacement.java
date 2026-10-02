package com.director.actor.path;
import net.minecraft.util.math.Vec3d;

/** Final root displacement projection with a short, explicitly authored route jump only. */
public final class ActorFinalGroundPlacement {
    private ActorFinalGroundPlacement() {}
    public static ActorRelationGroundConstraint.Result project(Vec3d base,Vec3d rootOffset,double jump,GroundRoute.Terrain terrain) {
        if(base==null || rootOffset==null || !Double.isFinite(rootOffset.x) || !Double.isFinite(rootOffset.y)
                || !Double.isFinite(rootOffset.z) || !Double.isFinite(jump))
            return new ActorRelationGroundConstraint.Result(null,"最终动作位置无效");
        var projected=ActorRelationGroundConstraint.project(base,base.add(rootOffset.x,0,rootOffset.z),terrain);
        if(projected.position()==null) return projected;
        var feet=projected.position(); String warning=projected.warning();
        if(Math.abs(rootOffset.y)>1e-5 && warning.isEmpty()) warning="地面演员的根骨骼高度位移已投影到地面，请使用短跳跃";
        jump=Math.max(0,Math.min(1.25,jump));
        if(jump>0) {
            int steps=(int)Math.ceil(jump/.1);
            for(int i=1;i<=steps;i++) if(!terrain.clear(feet.add(0,jump*i/steps,0)))
                return new ActorRelationGroundConstraint.Result(feet,"跳跃上方空间不足，已保持地面位置");
            feet=feet.add(0,jump,0);
        }
        return new ActorRelationGroundConstraint.Result(feet,warning);
    }
}
