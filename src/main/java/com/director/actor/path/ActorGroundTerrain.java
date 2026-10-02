package com.director.actor.path;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Block-collision queries with explicit bounds, independent of preview entity position/lifetime. */
public final class ActorGroundTerrain implements GroundRoute.Terrain {
    public interface CollisionWorld {
        boolean loaded(Box bounds);
        Iterable<Box> collisions(Box bounds);
    }
    private final CollisionWorld world;
    private final double width,height;
    public ActorGroundTerrain(Entity entity) { this(entity,new org.joml.Vector3f(1)); }
    public ActorGroundTerrain(Entity entity,org.joml.Vector3f scale) {
        this(entity.getEntityWorld(),new ActorCollisionDimensions(entity.getWidth(),entity.getHeight()).scaled(scale));
    }
    public ActorGroundTerrain(World world,ActorCollisionDimensions dimensions) {
        this(new CollisionWorld() {
            public boolean loaded(Box box) {
                int minX=BlockPos.ofFloored(box.minX,0,box.minZ).getX()>>4;
                int maxX=BlockPos.ofFloored(box.maxX,0,box.maxZ).getX()>>4;
                int minZ=BlockPos.ofFloored(box.minX,0,box.minZ).getZ()>>4;
                int maxZ=BlockPos.ofFloored(box.maxX,0,box.maxZ).getZ()>>4;
                for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++)
                    if(!world.isChunkLoaded(new BlockPos(x*16,0,z*16))) return false;
                return true;
            }
            public Iterable<Box> collisions(Box box) {
                var result=new ArrayList<Box>();
                for(var shape:world.getBlockCollisions(null,box)) result.addAll(shape.getBoundingBoxes());
                return result;
            }
        },dimensions);
    }
    public ActorGroundTerrain(CollisionWorld world,ActorCollisionDimensions dimensions) {
        this.world=Objects.requireNonNull(world); Objects.requireNonNull(dimensions);
        width=dimensions.width(); height=dimensions.height();
    }
    private double halfWidth() { return Math.max(.05,width/2-.001); }
    @Override public OptionalDouble floor(double x,double z,double y,double up,double down) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(up)||!Double.isFinite(down)||up<0||down<0) return OptionalDouble.empty();
        double half=halfWidth();
        var column=new Box(x-half,y-down-.001,z-half,x+half,y+up+.001,z+half);
        if(!world.loaded(column)) return OptionalDouble.empty();
        double best=Double.NEGATIVE_INFINITY;
        for(var box:world.collisions(column)) {
            if(box.maxY>y+up+1e-5 || box.maxY<y-down-1e-5) continue;
            if(box.maxY>best && clear(new Vec3d(x,box.maxY,z))) best=box.maxY;
        }
        return Double.isFinite(best)?OptionalDouble.of(best):OptionalDouble.empty();
    }
    @Override public boolean clear(Vec3d feet) {
        if(feet==null || !Double.isFinite(feet.x)||!Double.isFinite(feet.y)||!Double.isFinite(feet.z)) return false;
        double half=halfWidth(),epsilon=Math.min(.001,height/4);
        var body=new Box(feet.x-half,feet.y+epsilon,feet.z-half,feet.x+half,feet.y+height-epsilon,feet.z+half);
        return world.loaded(body) && !world.collisions(body).iterator().hasNext();
    }
}
