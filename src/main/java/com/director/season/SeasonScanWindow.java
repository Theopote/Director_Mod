package com.director.season;

import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.function.LongPredicate;

/** Bounded rolling column cache: recentering keeps visited overlap and queues only missing columns. */
final class SeasonScanWindow {
    private final int radius;
    private final Set<Long> visited=new HashSet<>();
    private final ArrayDeque<Long> pending=new ArrayDeque<>();
    private int x,z;
    private boolean initialized;
    SeasonScanWindow(int radius) { this.radius=radius; }
    static long column(int x,int z) { return ((long)x<<32)|(z&0xffffffffL); }
    static int x(long column) { return (int)(column>>32); }
    static int z(long column) { return (int)column; }
    boolean contains(int wx,int wz) { return initialized && Math.abs((long)wx-x)<=radius && Math.abs((long)wz-z)<=radius; }
    boolean contains(BlockPos pos) { return contains(pos.getX(),pos.getZ()); }
    void moveTo(BlockPos pos,LongPredicate loaded) {
        x=Math.floorDiv(pos.getX(),16)*16+8;z=Math.floorDiv(pos.getZ(),16)*16+8;initialized=true;
        visited.removeIf(c->!contains(x(c),z(c)) || !loaded.test(c));
        pending.clear();
        // Discover the nearest missing columns first, without ever rewriting known overlap.
        for(int r=0;r<=radius;r++) {
            if(r==0) { queue(x,z,loaded);continue; }
            for(int d=-r;d<=r;d++) { queue(x+d,z-r,loaded);queue(x+d,z+r,loaded); }
            for(int d=-r+1;d<r;d++) { queue(x-r,z+d,loaded);queue(x+r,z+d,loaded); }
        }
    }
    private void queue(int wx,int wz,LongPredicate loaded) {
        long c=column(wx,wz);
        if(!visited.contains(c) && loaded.test(c))pending.add(c);
    }
    boolean wasVisited(BlockPos pos) { return visited.contains(column(pos.getX(),pos.getZ())); }
    void invalidate(BlockPos pos) { visited.remove(column(pos.getX(),pos.getZ())); }
    Long poll() { return pending.poll(); }
    void visited(long column) { visited.add(column); }
    boolean pending() { return !pending.isEmpty(); }
    void clear() { visited.clear();pending.clear();initialized=false; }
}
