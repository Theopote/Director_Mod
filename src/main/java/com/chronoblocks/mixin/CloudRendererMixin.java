package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController;
import com.chronoblocks.ui.timeline.playback.weather.WeatherSample;
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
    private CloudRenderer.CloudCells chronoblocks$baseCells;

    @Unique
    private CloudRenderer.CloudCells chronoblocks$coverageCells;

    @Unique
    private float chronoblocks$lastCoverage = -1f;

    @Inject(
        method = "apply(Ljava/util/Optional;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL")
    )
    private void chronoblocks$captureBaseCells(Optional<CloudRenderer.CloudCells> cellsOpt, ResourceManager resourceManager,
                                               Profiler profiler, CallbackInfo ci) {
        chronoblocks$baseCells = this.cells;
        chronoblocks$coverageCells = null;
        chronoblocks$lastCoverage = -1f;
    }

    @Inject(
        method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;F)V",
        at = @At("HEAD")
    )
    private void chronoblocks$applyCloudCoverage(int tickCount, CloudRenderMode mode, float cloudHeight, Vec3d cameraPos,
                                                 float cloudPhase, CallbackInfo ci) {
        if (this.cells == null) {
            return;
        }

        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasOverride()) {
            chronoblocks$restoreBaseCells();
            return;
        }

        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            chronoblocks$restoreBaseCells();
            return;
        }

        float coverage = MathHelper.clamp(sample.cloudCoverage, 0f, 1f);
        if (coverage >= 0.99f) {
            chronoblocks$restoreBaseCells();
            chronoblocks$lastCoverage = coverage;
            return;
        }

        if (chronoblocks$baseCells == null) {
            chronoblocks$baseCells = this.cells;
        }

        if (chronoblocks$coverageCells != null && Math.abs(coverage - chronoblocks$lastCoverage) < 0.01f) {
            if (this.cells != chronoblocks$coverageCells) {
                this.cells = chronoblocks$coverageCells;
                this.rebuild = true;
            }
            return;
        }

        chronoblocks$coverageCells = chronoblocks$buildCoverageCells(chronoblocks$baseCells, coverage);
        chronoblocks$lastCoverage = coverage;
        this.cells = chronoblocks$coverageCells;
        this.rebuild = true;
    }

    @ModifyVariable(
        method = "renderClouds(ILnet/minecraft/client/option/CloudRenderMode;FLnet/minecraft/util/math/Vec3d;F)V",
        at = @At("HEAD"),
        index = 5,
        argsOnly = true
    )
    private float chronoblocks$scaleCloudPhase(float cloudPhase) {
        TimelineWeatherController controller = TimelineWeatherController.getInstance();
        if (!controller.hasOverride()) {
            return cloudPhase;
        }
        WeatherSample sample = controller.getCurrentSample();
        if (sample == null || !sample.enabled) {
            return cloudPhase;
        }

        float speed = MathHelper.clamp(sample.cloudSpeed, 0f, 4f);
        float windBoost = MathHelper.clamp(sample.windStrength, 0f, 1f) * 0.6f;
        speed = MathHelper.clamp(speed * (1f + windBoost), 0f, 4f);
        return cloudPhase * speed;
    }

    @Unique
    private void chronoblocks$restoreBaseCells() {
        if (chronoblocks$baseCells != null && this.cells != chronoblocks$baseCells) {
            this.cells = chronoblocks$baseCells;
            this.rebuild = true;
        }
    }

    @Unique
    private CloudRenderer.CloudCells chronoblocks$buildCoverageCells(CloudRenderer.CloudCells source, float coverage) {
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
                keep[idx] = chronoblocks$noise01(x, y) < coverage;
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
                boolean northEmpty = !chronoblocks$isKept(keep, x, y - 1, width, height);
                boolean eastEmpty = !chronoblocks$isKept(keep, x + 1, y, width, height);
                boolean southEmpty = !chronoblocks$isKept(keep, x, y + 1, width, height);
                boolean westEmpty = !chronoblocks$isKept(keep, x - 1, y, width, height);
                out[idx] = chronoblocks$packCloudCell(color, northEmpty, eastEmpty, southEmpty, westEmpty);
            }
        }

        return new CloudRenderer.CloudCells(out, width, height);
    }

    @Unique
    private boolean chronoblocks$isKept(boolean[] keep, int x, int y, int width, int height) {
        int wx = Math.floorMod(x, width);
        int wy = Math.floorMod(y, height);
        return keep[wy * width + wx];
    }

    @Unique
    private double chronoblocks$noise01(int x, int y) {
        long h = x * 3129871L ^ y * 116129781L ^ 0x9E3779B97F4A7C15L;
        h = h * h * 42317861L + h * 11L;
        return ((h >>> 24) & 0xFFFFFF) / (double) 0x1000000;
    }

    @Unique
    private static long chronoblocks$packCloudCell(int color, boolean northEmpty, boolean eastEmpty, boolean southEmpty,
                                                   boolean westEmpty) {
        long packed = ((long) color) << 4;
        packed |= (northEmpty ? 1L : 0L) << 3;
        packed |= (eastEmpty ? 1L : 0L) << 2;
        packed |= (southEmpty ? 1L : 0L) << 1;
        packed |= (westEmpty ? 1L : 0L);
        return packed;
    }
}
