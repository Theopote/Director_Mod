package com.director.mixin;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.weather.WeatherSample;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @Shadow
    private CloudRenderer.CloudCells cells;

    @Shadow
    private boolean rebuild;

    @Unique
    private CloudRenderer.CloudCells director$baseCells;

    @Unique
    private CloudRenderer.CloudCells director$coverageCells;

    @Unique
    private float director$lastCoverage = -1f;

    @Inject(
        method = "apply(Ljava/util/Optional;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL")
    )
    private void director$captureBaseCells(Optional<CloudRenderer.CloudCells> cellsOpt, ResourceManager resourceManager,
                                               Profiler profiler, CallbackInfo ci) {
        director$baseCells = this.cells;
        director$coverageCells = null;
        director$lastCoverage = -1f;
    }

    @Inject(
        method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;F)V",
        at = @At("HEAD")
    )
    private void director$applyCloudCoverage(int tickCount, CloudRenderMode mode, float cloudHeight, Vec3d cameraPos,
                                                 float cloudPhase, CallbackInfo ci) {
        if (this.cells == null) {
            return;
        }

        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasWeatherOverride()) {
            director$restoreBaseCells();
            return;
        }

        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            director$restoreBaseCells();
            return;
        }

        float coverage = MathHelper.clamp(sample.cloudCoverage, 0f, 1f);
        if (coverage >= 0.99f) {
            director$restoreBaseCells();
            director$lastCoverage = coverage;
            return;
        }

        if (director$baseCells == null) {
            director$baseCells = this.cells;
        }

        if (director$coverageCells != null && Math.abs(coverage - director$lastCoverage) < 0.01f) {
            if (this.cells != director$coverageCells) {
                this.cells = director$coverageCells;
                this.rebuild = true;
            }
            return;
        }

        director$coverageCells = director$buildCoverageCells(director$baseCells, coverage);
        director$lastCoverage = coverage;
        this.cells = director$coverageCells;
        this.rebuild = true;
    }

    @ModifyVariable(
        method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;F)V",
        at = @At("HEAD"),
        index = 5,
        argsOnly = true
    )
    private float director$scaleCloudPhase(float cloudPhase) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (!state.hasWeatherOverride()) {
            return cloudPhase;
        }
        WeatherSample sample = state.weatherSample();
        if (sample == null || !sample.enabled) {
            return cloudPhase;
        }

        float speed = MathHelper.clamp(sample.cloudSpeed, 0f, 4f);
        float windBoost = MathHelper.clamp(sample.windStrength, 0f, 1f) * 0.6f;
        speed = MathHelper.clamp(speed * (1f + windBoost), 0f, 4f);
        return cloudPhase * speed;
    }

    @Unique
    private void director$restoreBaseCells() {
        if (director$baseCells != null && this.cells != director$baseCells) {
            this.cells = director$baseCells;
            this.rebuild = true;
        }
    }

    @Unique
    private CloudRenderer.CloudCells director$buildCoverageCells(CloudRenderer.CloudCells source, float coverage) {
        long[] base = source.cells();
        int width = source.width();
        int height = source.height();
        int size = width * height;
        long[] out = new long[size];
        boolean[] keep = new boolean[size];

        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int idx = row + x;
                long cell = base[idx];
                if (cell == 0L) {
                    keep[idx] = false;
                    continue;
                }
                keep[idx] = director$noise01(x, y) < coverage;
            }
        }

        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int idx = row + x;
                if (!keep[idx]) {
                    out[idx] = 0L;
                    continue;
                }
                int color = (int) (base[idx] >>> 4);
                boolean northEmpty = !director$isKept(keep, x, y - 1, width, height);
                boolean eastEmpty = !director$isKept(keep, x + 1, y, width, height);
                boolean southEmpty = !director$isKept(keep, x, y + 1, width, height);
                boolean westEmpty = !director$isKept(keep, x - 1, y, width, height);
                out[idx] = director$packCloudCell(color, northEmpty, eastEmpty, southEmpty, westEmpty);
            }
        }

        return new CloudRenderer.CloudCells(out, width, height);
    }

    @Unique
    private boolean director$isKept(boolean[] keep, int x, int y, int width, int height) {
        int wx = Math.floorMod(x, width);
        int wy = Math.floorMod(y, height);
        return keep[wy * width + wx];
    }

    @Unique
    private double director$noise01(int x, int y) {
        long h = x * 3129871L ^ y * 116129781L ^ 0x9E3779B97F4A7C15L;
        h = h * h * 42317861L + h * 11L;
        return ((h >>> 24) & 0xFFFFFF) / (double) 0x1000000;
    }

    @Unique
    private static long director$packCloudCell(int color, boolean northEmpty, boolean eastEmpty, boolean southEmpty,
                                                   boolean westEmpty) {
        long packed = ((long) color) << 4;
        packed |= (northEmpty ? 1L : 0L) << 3;
        packed |= (eastEmpty ? 1L : 0L) << 2;
        packed |= (southEmpty ? 1L : 0L) << 1;
        packed |= (westEmpty ? 1L : 0L);
        return packed;
    }
}
