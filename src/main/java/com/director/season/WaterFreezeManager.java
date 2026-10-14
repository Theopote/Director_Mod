package com.director.season;

import com.director.timeline.playback.season.SeasonSample;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.*;

/** Client-thread, bounded surface scanning. No world reads or writes run on a worker. */
public final class WaterFreezeManager {
    private static final WaterFreezeManager INSTANCE = new WaterFreezeManager();
    private static final int RADIUS = 128, WIDTH = RADIUS * 2 + 1, BUDGET = 1024;
    private final Map<BlockPos, BlockState> originalWaterStates = new LinkedHashMap<>();
    private final Map<BlockPos, BlockState> ownedIceStates = new HashMap<>();
    private final List<BlockPos> candidates = new ArrayList<>();
    private SeasonSample lastSample;
    private ClientWorld ownerWorld;
    private BlockPos center;
    private int scanColumn, evaluationCursor, remainingEvaluations, chunkStamp;
    private WaterFreezeManager() {}
    public static WaterFreezeManager get() { return INSTANCE; }
    public void onSeasonSampleUpdated(SeasonSample sample) { applySeasonSample(sample); }
    public void onSeasonSampleChanged(SeasonSample sample) { applySeasonSample(sample); }
    public void applySeasonSample(SeasonSample sample) {
        float before = coverage();
        lastSample = sample == null ? null : sample.copy();
        if (coverage() <= .01f) { restoreOwned(); remainingEvaluations = 0; }
        else if (before != coverage()) remainingEvaluations = candidates.size();
    }
    private float coverage() {
        return lastSample != null && lastSample.enabled && Float.isFinite(lastSample.freezeStrength)
                ? Math.max(0, Math.min(.92f, lastSample.freezeStrength)) : 0;
    }
    public void tick(ClientWorld world, Vec3d cameraPos) {
        if (world == null || cameraPos == null) return;
        if (ownerWorld != world) { restoreOwned(); resetGeometry(); ownerWorld = world; }
        if (coverage() <= .01f) return;
        BlockPos pos = BlockPos.ofFloored(cameraPos);
        int stamp = SeasonWorldEffects.loadedChunkStamp(world, pos, 8);
        if (center == null || Math.abs(pos.getX()-center.getX()) >= 16 || Math.abs(pos.getZ()-center.getZ()) >= 16 || stamp != chunkStamp) {
            restoreOwned(); resetGeometry(); center = pos; chunkStamp = stamp;
        }
        for (int n=0; n<BUDGET && scanColumn<WIDTH*WIDTH; n++,scanColumn++) {
            int x=center.getX()-RADIUS+scanColumn/WIDTH, z=center.getZ()-RADIUS+scanColumn%WIDTH;
            if (!world.getChunkManager().isChunkLoaded(x>>4,z>>4)) continue;
            BlockPos surface=world.getTopPosition(Heightmap.Type.WORLD_SURFACE,new BlockPos(x,0,z)).down();
            BlockState state=world.getBlockState(surface);
            if (state.isOf(Blocks.WATER) && !originalWaterStates.containsKey(surface)) {
                originalWaterStates.put(surface,state); candidates.add(surface);
                evaluate(surface);
            }
        }
        for (int n=0; n<BUDGET && remainingEvaluations>0 && !candidates.isEmpty(); n++,remainingEvaluations--) {
            if (evaluationCursor>=candidates.size()) evaluationCursor=0;
            evaluate(candidates.get(evaluationCursor++));
        }
    }
    private void evaluate(BlockPos pos) {
        if (!ownerWorld.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)) return;
        BlockState actual=ownerWorld.getBlockState(pos), owned=ownedIceStates.get(pos);
        if (owned != null && !actual.equals(owned)) {
            ownedIceStates.remove(pos); originalWaterStates.put(pos,actual);
            return; // Server/user changes take precedence over our visual overlay.
        }
        if (NoiseField.iceCoverage(pos)<coverage()) {
            if (actual.isOf(Blocks.WATER)) {
                originalWaterStates.put(pos,actual);
                BlockState ice=Blocks.ICE.getDefaultState();
                ownerWorld.setBlockState(pos,ice,3); ownedIceStates.put(pos,ice);
            }
        } else if (owned != null) {
            ownerWorld.setBlockState(pos,originalWaterStates.get(pos),3); ownedIceStates.remove(pos);
        }
    }
    private void restoreOwned() {
        if (ownerWorld != null) for (var e:ownedIceStates.entrySet()) {
            BlockPos pos=e.getKey();
            if (ownerWorld.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4)
                    && ownerWorld.getBlockState(pos).equals(e.getValue()))
                ownerWorld.setBlockState(pos,originalWaterStates.get(pos),3);
        }
        ownedIceStates.clear();
    }
    private void resetGeometry() { originalWaterStates.clear(); candidates.clear(); center=null; scanColumn=0; evaluationCursor=0; remainingEvaluations=0; }
    public boolean hasPendingWork() { return coverage()>.01f && (center==null || scanColumn<WIDTH*WIDTH || remainingEvaluations>0); }
    public void clearAll() { lastSample=null; restoreOwned(); resetGeometry(); ownerWorld=null; }
    /** World unload is reusable; there is no permanently closed singleton executor. */
    public void shutdown() { clearAll(); }
    public void tick() { var mc=MinecraftClient.getInstance(); if(mc!=null && mc.world!=null && mc.player!=null) tick(mc.world,mc.player.getEntityPos()); }
}
