package com.director.mixin.accessor;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private transform setter so client-only preview code can drive display entities directly. */
@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
    @Invoker("setTransformation")
    void director$invokeSetTransformation(AffineTransformation transformation);
}
