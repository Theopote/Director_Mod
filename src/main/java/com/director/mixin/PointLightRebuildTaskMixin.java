package com.director.mixin;

import com.director.lighting.PointLightRuntime;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Covers synchronous builds as well as the asynchronous worker's nested scope. */
@Mixin(targets = "net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
public abstract class PointLightRebuildTaskMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/chunk/SectionBuilder;build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;"))
    private SectionBuilder.RenderData director$pinLightSection(SectionBuilder builder,
            ChunkSectionPos pos, ChunkRendererRegion region, VertexSorter sorter,
            BlockBufferAllocatorStorage buffers) {
        try (var scope = PointLightRuntime.buildSnapshots().openScope();
             var season = com.director.season.SeasonTintContext.openScope()) {
            return builder.build(pos, region, sorter, buffers);
        }
    }
}
