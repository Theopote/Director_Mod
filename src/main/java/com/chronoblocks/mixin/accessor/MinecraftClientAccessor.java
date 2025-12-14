package com.chronoblocks.mixin.accessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 允许在导出离屏渲染时临时替换 MinecraftClient 的主 framebuffer。
 *
 * <p>⚠️ 仅用于渲染线程的短时间替换，完成后必须立刻恢复。</p>
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("framebuffer")
    Framebuffer chronoblocks$getFramebufferField();

    @Mutable
    @Accessor("framebuffer")
    void chronoblocks$setFramebufferField(Framebuffer framebuffer);
}


