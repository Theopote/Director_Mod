package com.director.mixin;

import com.director.lighting.PointLightRuntime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkBuilder.class)
public abstract class PointLightChunkBuilderMixin {
    @Redirect(method = "scheduleRunTasks", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<CompletableFuture<Object>> director$pinLightBuild(
            Supplier<CompletableFuture<Object>> task, Executor executor) {
        return PointLightRuntime.buildSnapshots().submit(task, executor);
    }
}

