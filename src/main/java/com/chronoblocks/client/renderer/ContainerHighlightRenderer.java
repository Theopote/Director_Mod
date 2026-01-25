package com.chronoblocks.client.renderer;
import com.chronoblocks.client.renderer.mesh.BlockOutlineMeshCache;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.opengl.GL11;

import java.util.Collection;

/**
 * 容器高亮渲染器
 * 当容器被选中时，高亮显示容器中的方块边框
 * 
 * @author ChronoBlocks Team
 * @version 5.0
 */
public final class ContainerHighlightRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerHighlightRenderer.class);
    
    private static volatile ContainerHighlightRenderer INSTANCE;
    
    // 高亮颜色（使用容器的标记颜色，如果没有则使用默认颜色）
    private final float[] defaultOutlineColor = {0.4f, 1.0f, 0.5f, 0.9f}; // 绿色边框（仅作为 fallback）

    // 统一使用 BlockOutlineMeshCache（与选区/工具预览共享）
    private final BlockOutlineMeshCache meshCache = new BlockOutlineMeshCache();

    // ✅ 关键性能优化：容器高亮的 mesh 只在容器结构变化时重建（避免每帧 O(N) 遍历/哈希/分配）
    private Container cachedContainer = null;
    private long cachedContainerModifiedTime = -1L;
    private BlockOutlineMeshCache.Mesh cachedContainerMesh = null;
    
    private ContainerHighlightRenderer() {
    }
    
    public static ContainerHighlightRenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (ContainerHighlightRenderer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContainerHighlightRenderer();
                }
            }
        }
        return INSTANCE;
    }
    
    // 全局设置中心（新系统优先读取）
    private final com.chronoblocks.client.settings.RenderSettings settings = com.chronoblocks.client.settings.RenderSettings.get();

    /**
     * 渲染选中容器的高亮边框
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        // ✅ 直接从全局设置读取（实时生效）
        if (!settings.container_showOutline) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        
        ContainerManager manager = ContainerManager.getInstance();
        if (manager == null) {
            return;
        }
        
        Container selectedContainer = manager.getSelectedContainer();
        if (selectedContainer == null) {
            return;
        }
        
        // 获取容器的方块
        Collection<BlockPos> blocks = selectedContainer.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            cachedContainer = null;
            cachedContainerModifiedTime = -1L;
            cachedContainerMesh = null;
            return;
        }
        
        // 获取相机位置
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        BlockPos cameraBlock = new BlockPos(
            (int) Math.floor(cameraPos.x),
            (int) Math.floor(cameraPos.y),
            (int) Math.floor(cameraPos.z)
        );
        
        // ✅ 直接从全局设置读取颜色和线宽（实时生效）
        final float r = (settings.container_outlineColor != null && settings.container_outlineColor.length >= 4)
            ? settings.container_outlineColor[0] : defaultOutlineColor[0];
        final float g = (settings.container_outlineColor != null && settings.container_outlineColor.length >= 4)
            ? settings.container_outlineColor[1] : defaultOutlineColor[1];
        final float b = (settings.container_outlineColor != null && settings.container_outlineColor.length >= 4)
            ? settings.container_outlineColor[2] : defaultOutlineColor[2];
        final float a = (settings.container_outlineColor != null && settings.container_outlineColor.length >= 4)
            ? settings.container_outlineColor[3] : defaultOutlineColor[3];
        
        // 设置渲染状态
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(false);
        float lw = settings.container_outlineWidth;
        RenderSystem.lineWidth(Math.max(1.0f, Math.min(lw, 4.0f)));
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer lineConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        
        try {
            // ✅ 直接从全局设置读取（实时生效）
            int maxDistSq = settings.selection_maxRenderDistance * settings.selection_maxRenderDistance;
            if (settings.container_enableGreedy) {
                // ✅ 贪婪网格生成非常昂贵：不要每帧构造“visibleBlocks”导致缓存失效。
                // 这里改为：使用容器包围盒做整体距离裁剪；网格基于容器 blocks 生成并稳定复用。
                Container.BoundingBox bbox = selectedContainer.getBoundingBox();
                if (bbox != null) {
                    // 计算相机到 AABB 的最短距离（平方）
                    double distSqToBox = getDistSqToBox(cameraPos, bbox);
                    if (distSqToBox > (double) maxDistSq) {
                        return;
                    }
                }

                // 基于方块集合构建贪婪网格，输出外轮廓线（按容器 modifiedTime 缓存）
                long mt = selectedContainer.getModifiedTime();
                BlockOutlineMeshCache.Mesh mesh;
                if (cachedContainer == selectedContainer && cachedContainerModifiedTime == mt && cachedContainerMesh != null) {
                    mesh = cachedContainerMesh;
                } else {
                    mesh = meshCache.getOrCreateMesh(blocks);
                    cachedContainer = selectedContainer;
                    cachedContainerModifiedTime = mt;
                    cachedContainerMesh = mesh;
                }
                int renderedCount = 0;
                for (BlockOutlineMeshCache.Line line : mesh.lines()) {
                    drawLine(lineConsumer, matrix,
                        (float) (line.x1() - cameraPos.x), (float) (line.y1() - cameraPos.y), (float) (line.z1() - cameraPos.z),
                        (float) (line.x2() - cameraPos.x), (float) (line.y2() - cameraPos.y), (float) (line.z2() - cameraPos.z),
                        r, g, b, a);
                    renderedCount++;
                }

                if (renderedCount > 0 && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[ContainerHighlightRenderer] 使用贪婪合并渲染容器外轮廓 {} 条线段", renderedCount);
                }
                
                // ⭐ 贪婪模式下也支持包围盒渲染（如果设置开启）
                if (settings.container_showBoundingBox) {
                    if (bbox != null) {
                        Box box = new Box(
                            bbox.getMin().getX(), bbox.getMin().getY(), bbox.getMin().getZ(),
                            bbox.getMax().getX() + 1.0, bbox.getMax().getY() + 1.0, bbox.getMax().getZ() + 1.0
                        ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                        // 使用已有的 r, g, b, a 变量
                        renderBoundingBoxLines(lineConsumer, matrix, box, r, g, b, a);
                    }
                }
            } else {
                // 非贪婪：使用逐方块渲染（与 SelectionRenderer 保持一致）
                int rendered = 0;
                for (BlockPos pos : blocks) {
                    if (cameraBlock.getSquaredDistance(pos) > maxDistSq) {
                        continue;
                    }
                    drawBlockOutline(lineConsumer, matrix, pos, r, g, b, a, cameraPos);
                    rendered++;
                }

                if (rendered > 0 && LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[ContainerHighlightRenderer] ✅ 逐方块渲染 {} 个方块线框", rendered);
                }

                // ⭐ 包围盒渲染（根据设置控制）
                if (settings.container_showBoundingBox && blocks.size() > 1) {
                    Container.BoundingBox bbox = selectedContainer.getBoundingBox();
                    if (bbox != null) {
                        Box box = new Box(
                            bbox.getMin().getX(), bbox.getMin().getY(), bbox.getMin().getZ(),
                            bbox.getMax().getX() + 1.0, bbox.getMax().getY() + 1.0, bbox.getMax().getZ() + 1.0
                        ).offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);

                        // 使用已有的 r, g, b, a 变量
                        renderBoundingBoxLines(lineConsumer, matrix, box, r, g, b, a);
                    }
                }
            }
        } finally {
            GlStateManager._depthMask(true);
            RenderSystem.lineWidth(1.0f);
        }
    }

    private static double getDistSqToBox(Vec3d cameraPos, Container.BoundingBox bbox) {
        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        double cx = cameraPos.x;
        double cy = cameraPos.y;
        double cz = cameraPos.z;
        double minX = bbox.getMin().getX();
        double minY = bbox.getMin().getY();
        double minZ = bbox.getMin().getZ();
        double maxX = bbox.getMax().getX() + 1.0;
        double maxY = bbox.getMax().getY() + 1.0;
        double maxZ = bbox.getMax().getZ() + 1.0;

        if (cx < minX) dx = minX - cx;
        else if (cx > maxX) dx = cx - maxX;
        if (cy < minY) dy = minY - cy;
        else if (cy > maxY) dy = cy - maxY;
        if (cz < minZ) dz = minZ - cz;
        else if (cz > maxZ) dz = cz - maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 绘制一条线
     */
    private void drawLine(VertexConsumer consumer, Matrix4f matrix,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = 0f, ny = 1f, nz = 0f;
        if (len > 1.0e-6f) {
            nx = dx / len;
            ny = dy / len;
            nz = dz / len;
        }
        consumer.vertex(matrix, x1, y1, z1)
                .color(r, g, b, a)
                .normal(nx, ny, nz);
        consumer.vertex(matrix, x2, y2, z2)
                .color(r, g, b, a)
                .normal(nx, ny, nz);
    }
    
    /**
     * 绘制单个方块的线框（12条边）
     * 与 SelectionRenderer 的 drawOutline 保持一致
     */
    private static final double OUTLINE_EXPAND = 0.001;
    
    private void drawBlockOutline(VertexConsumer consumer, Matrix4f matrix, BlockPos pos,
                                  float r, float g, float b, float a, Vec3d cameraPos) {
        double minX = pos.getX() - cameraPos.x - OUTLINE_EXPAND;
        double minY = pos.getY() - cameraPos.y - OUTLINE_EXPAND;
        double minZ = pos.getZ() - cameraPos.z - OUTLINE_EXPAND;
        double maxX = pos.getX() - cameraPos.x + 1.0 + OUTLINE_EXPAND;
        double maxY = pos.getY() - cameraPos.y + 1.0 + OUTLINE_EXPAND;
        double maxZ = pos.getZ() - cameraPos.z + 1.0 + OUTLINE_EXPAND;

        // 底部4条边
        drawLine(consumer, matrix, (float) minX, (float) minY, (float) minZ, (float) maxX, (float) minY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) minZ, (float) maxX, (float) minY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) maxZ, (float) minX, (float) minY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) minX, (float) minY, (float) maxZ, (float) minX, (float) minY, (float) minZ, r, g, b, a);
        
        // 顶部4条边
        drawLine(consumer, matrix, (float) minX, (float) maxY, (float) minZ, (float) maxX, (float) maxY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) maxY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) maxY, (float) maxZ, (float) minX, (float) maxY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) minX, (float) maxY, (float) maxZ, (float) minX, (float) maxY, (float) minZ, r, g, b, a);
        
        // 垂直4条边
        drawLine(consumer, matrix, (float) minX, (float) minY, (float) minZ, (float) minX, (float) maxY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) maxX, (float) minY, (float) maxZ, (float) maxX, (float) maxY, (float) maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) minX, (float) minY, (float) maxZ, (float) minX, (float) maxY, (float) maxZ, r, g, b, a);
    }
    
    /**
     * 渲染包围盒的12条边
     */
    private void renderBoundingBoxLines(VertexConsumer consumer, Matrix4f matrix, Box box, float r, float g, float b, float a) {
        // 底部4条边
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.minY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.minY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.minY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.minY, (float) box.minZ, r, g, b, a);
        // 顶部4条边
        drawLine(consumer, matrix, (float) box.minX, (float) box.maxY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        // 垂直4条边
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.minZ,
            (float) box.minX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.minZ,
            (float) box.maxX, (float) box.maxY, (float) box.minZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ,
            (float) box.maxX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
        drawLine(consumer, matrix, (float) box.minX, (float) box.minY, (float) box.maxZ,
            (float) box.minX, (float) box.maxY, (float) box.maxZ, r, g, b, a);
    }
    
    // 过时的本地配置接口已移除：运行期统一从 RenderSettings 读取配置
}

