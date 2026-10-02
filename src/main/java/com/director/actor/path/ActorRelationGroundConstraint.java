package com.director.actor.path;
import net.minecraft.util.math.Vec3d;
import com.director.timeline.model.entity.ActorRouteClip;
import java.util.List;

/** Deterministic reachable ground projection for relation placement, not pathfinding. */
public final class ActorRelationGroundConstraint {
    public record Result(Vec3d position,String warning) {}
    private ActorRelationGroundConstraint() {}
    public static Result project(Vec3d from,Vec3d requested,GroundRoute.Terrain terrain) {
        if (from==null || requested==null || terrain==null) return new Result(null,"关系地形尚未就绪");
        double distance=Math.hypot(requested.x-from.x,requested.z-from.z);
        if (!Double.isFinite(distance)) return new Result(null,"关系位置无效");
        boolean limited=distance>64;
        if (limited) requested=new Vec3d(from.x+(requested.x-from.x)*64/distance,requested.y,from.z+(requested.z-from.z)*64/distance);
        var route=GroundRoute.compile(List.of(from,requested),terrain);
        if (!route.valid()) return new Result(null,route.warning());
        var position=route.sample(route.length()+1,1,ActorRouteClip.LoopMode.ONCE).position();
        String warning=route.warning();
        if (warning.isEmpty() && limited) warning="关系检测距离超过 64 格，已限制到可检测范围";
        return new Result(position,warning);
    }
}
