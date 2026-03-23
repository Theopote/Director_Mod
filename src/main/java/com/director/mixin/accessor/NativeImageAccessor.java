package com.director.mixin.accessor;

import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NativeImage.class)
public interface NativeImageAccessor {
    @Invoker("getColor")
    int invokeGetColor(int x, int y);

    @Accessor("pointer")
    long getPointer();
}
