package com.director.client.gizmo;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class GizmoSolidPassTest {
    @Test void allAxesRenderWithoutReusingAConsumerAfterLayerSwitch() throws Exception {
        for(var mode:new GizmoMode[]{GizmoMode.TRANSLATE,GizmoMode.SCALE}) {
            var context=new GizmoContext(GizmoType.TRANSFORM,new Box(0,0,0,1,1,1),Vec3d.ZERO);
            context.setMode(mode);
            var renderer=new GizmoRenderer(context,new GizmoInteractionController(context,null));
            int[] generation={0}; int[] colors=new int[3]; int[] translucent={0};
            RenderLayer[] active={null};
            VertexConsumerProvider provider=layer -> {
                if(active[0]!=layer) { active[0]=layer; generation[0]++; }
                int captured=generation[0];
                return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
                    assertEquals(captured,generation[0],"Consumer reused after a layer flush");
                    if(method.getName().equals("color") && args!=null && args.length==4 && args[0] instanceof Float) {
                        float r=(Float)args[0],g=(Float)args[1],b=(Float)args[2];
                        colors[r>g && r>b?0:g>b?1:2]++;
                        if((Float)args[3]<.6f) translucent[0]++;
                    }
                    return method.getReturnType()==VertexConsumer.class?proxy:null;
                });
            };
            var draw=GizmoRenderer.class.getDeclaredMethod("renderAxisHandlesArrows",Matrix4f.class,VertexConsumerProvider.class,
                    Vec3d.class,double.class,double.class,double.class,Vec3d.class,double.class);
            draw.setAccessible(true);
            draw.invoke(renderer,new Matrix4f(),provider,Vec3d.ZERO,2d,.4d,.15d,new Vec3d(4,4,4),0d);
            for(int count:colors) assertTrue(count>0,"Missing axis color");
            assertTrue(translucent[0]>0);
        }
    }
}
