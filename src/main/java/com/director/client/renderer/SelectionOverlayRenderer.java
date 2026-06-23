package com.director.client.renderer;

import com.director.core.DirectorServices;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;

/**
 * 选区附加覆盖层渲染器。
 * <p>
 * 面中心双向箭头由 {@link InteractionHintRenderer} 负责；
 * 选区线框由 {@link SelectionRenderer} 负责。
 */
@Environment(EnvType.CLIENT)
public final class SelectionOverlayRenderer {

    private static SelectionOverlayRenderer INSTANCE;

    private SelectionOverlayRenderer() {}

    public static SelectionOverlayRenderer get() {
        if (INSTANCE == null) {
            INSTANCE = new SelectionOverlayRenderer();
        }
        return INSTANCE;
    }

    public void render(MatrixStack matrices, float tickDelta, Camera camera, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) {
            return;
        }

        var manager = DirectorServices.get().selection();
        if (manager == null || !manager.hasCurrentTool()) {
            return;
        }

        if (manager.getCurrentSelection() == null || manager.getCurrentBounds() == null) {
            return;
        }

        // 当前无附加覆盖层元素；保留入口供渲染管线注册
    }
}
