package com.director.ui.actor.pick;
import com.director.client.input.DirectorInputSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;
/** Actor tools share the viewport cursor ray, including while the Minecraft world is paused. */
public final class ActorViewportRay {
    private ActorViewportRay() {}
    public static BlockHitResult blockHit() {
        var client=MinecraftClient.getInstance();
        var ray=DirectorInputSystem.computeImGuiRay();
        if(client==null || client.world==null || client.getCameraEntity()==null || ray==null) return null;
        return client.world.raycast(new RaycastContext(ray.origin(),ray.origin().add(ray.direction().multiply(128)),
                RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,client.getCameraEntity()));
    }
}
