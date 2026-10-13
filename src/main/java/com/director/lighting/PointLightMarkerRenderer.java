package com.director.lighting;

import com.director.ui.lighting.PointLightController;
import com.director.ui.lighting.PointLightEditor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Editor-only markers; lighting itself remains active during video capture. */
public final class PointLightMarkerRenderer {
    private PointLightMarkerRenderer() {}
    public static void render(WorldRenderContext context) {
        var client=MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof com.director.ui.DirectorScreen)
                || com.director.export.video.VideoExportManager.getInstance().isExporting()
                || context.consumers()==null || context.matrices()==null) return;
        var model=PointLightEditor.model();if(model==null) return;
        var matrix=context.matrices().peek().getPositionMatrix();
        var consumer=context.consumers().getBuffer(RenderLayer.getLines());
        Vec3d camera=client.gameRenderer.getCamera().getPos();
        var selected=PointLightEditor.selected();
        for(var light:model.getPointLights()) {
            Vec3d p=PointLightController.position(light).subtract(camera);
            if(p.lengthSquared()>256*256) continue;
            boolean isSelected=selected!=null && selected.id().equals(light.id());
            float red=light.enabled()?1f:.45f, green=isSelected?1f:.65f, blue=light.enabled()?.2f:.45f;
            double size=.25;
            line(consumer,matrix,p.add(-size,0,0),p.add(size,0,0),red,green,blue,1);
            line(consumer,matrix,p.add(0,-size,0),p.add(0,size,0),red,green,blue,1);
            line(consumer,matrix,p.add(0,0,-size),p.add(0,0,size),red,green,blue,1);
            if(isSelected) for(int plane=0;plane<3;plane++) for(int i=0;i<48;i++) {
                double a=i*Math.PI*2/48,b=(i+1)*Math.PI*2/48,r=light.range();
                line(consumer,matrix,p.add(circle(plane,a,r)),p.add(circle(plane,b,r)),1,1,.2f,.35f);
            }
        }
    }
    private static Vec3d circle(int plane,double a,double r) {
        double x=Math.cos(a)*r,y=Math.sin(a)*r;
        return switch(plane) { case 0 -> new Vec3d(x,y,0);case 1 -> new Vec3d(x,0,y);default -> new Vec3d(0,x,y); };
    }
    private static void line(VertexConsumer consumer,Matrix4f matrix,Vec3d from,Vec3d to,float r,float g,float b,float alpha) {
        Vec3d normal=to.subtract(from).normalize();
        consumer.vertex(matrix,(float)from.x,(float)from.y,(float)from.z).color(r,g,b,alpha).normal((float)normal.x,(float)normal.y,(float)normal.z);
        consumer.vertex(matrix,(float)to.x,(float)to.y,(float)to.z).color(r,g,b,alpha).normal((float)normal.x,(float)normal.y,(float)normal.z);
    }
}
