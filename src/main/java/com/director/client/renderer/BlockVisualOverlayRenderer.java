package com.director.client.renderer;

import com.director.core.animation.BlockAnimationRenderer;
import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.container.visibility.BlockVisualStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;

/** Non-timeline ghost states share the textured renderer; timeline entries render exactly once. */
public class BlockVisualOverlayRenderer {
    private static final BlockVisualOverlayRenderer INSTANCE = new BlockVisualOverlayRenderer();
    private BlockVisualOverlayRenderer() {}
    public static BlockVisualOverlayRenderer getInstance() { return INSTANCE; }

    public void renderOverlays(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera) {
        var manager = BlockVisualStateManager.getInstance();
        var client = MinecraftClient.getInstance();
        if (!manager.hasActiveOverrides() || client.world == null) return;
        var overlays = manager.getOverlayBlocks();
        if (overlays.isEmpty()) return;
        var fallback = new ArrayList<BlockVisualStateManager.RenderEntry>();
        for (var pos : overlays) {
            if (manager.hasRenderEntry(pos)) continue;
            var visual = manager.getBlockState(pos);
            if (visual.isHidden() || visual.getAlphaMultiplier() <= 0 || visual.getAlphaMultiplier() >= 1) continue;
            var state = client.world.getBlockState(pos);
            if (state.isAir()) continue;
            var snapshot = new BlockAnimationSnapshot(pos, state);
            snapshot.markFullyVisible(); snapshot.setTransform(null);
            fallback.add(new BlockVisualStateManager.RenderEntry(pos, state, visual, null, null, snapshot));
        }
        BlockAnimationRenderer.renderEntries(client.world, matrices, consumers, fallback);
    }
}
