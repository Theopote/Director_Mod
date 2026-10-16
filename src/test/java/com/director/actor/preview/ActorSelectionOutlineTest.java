package com.director.actor.preview;
import com.director.actor.rig.skeleton.*;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorSelectionOutlineTest {
    private ModelPart part(Map<String,ModelPart> children) { return new ModelPart(List.of(),children); }
    @Test void headIncludesHatButExcludesBodyAndArmsAndMultiSelectionIsAdditive() {
        var hat=part(Map.of());var head=part(Map.of("hat",hat));var body=part(Map.of());var arm=part(Map.of());
        var root=part(Map.of("head",head,"body",body,"left_arm",arm));
        var mask=ActorSelectionOutline.selectionMask(root,Set.of(HumanoidRig.HEAD));
        assertTrue(mask.parts().containsAll(List.of(head,hat)));assertFalse(mask.parts().contains(body));
        assertFalse(mask.parts().contains(arm));
        mask=ActorSelectionOutline.selectionMask(root,Set.of(HumanoidRig.HEAD,HumanoidRig.LEFT_ARM));
        assertTrue(mask.parts().containsAll(List.of(head,hat,arm)));assertFalse(mask.parts().contains(body));
        mask=ActorSelectionOutline.selectionMask(root,Set.of(ArticulatedPlayerRig.LEFT_FOREARM));
        assertTrue(mask.parts().contains(arm));assertFalse(mask.parts().contains(head));
    }
    @Test void outlineMaskIsScopedAndCannotLeakToNormalRenderingAfterFailure() {
        var cube=new ModelPart.Cuboid(0,0,0,0,0,1,1,1,0,0,0,false,16,16,EnumSet.allOf(net.minecraft.util.math.Direction.class));
        var head=new ModelPart(List.of(cube),Map.of());var body=part(Map.of());
        var root=part(Map.of("head",head,"body",body));
        var model=new Model<Object>(root,id->null) { @Override public void setAngles(Object state) {} };
        var consumer=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
            assertTrue(ActorSelectionOutline.accepts(head));assertFalse(ActorSelectionOutline.accepts(body));
            throw new IllegalStateException("simulate render failure");
        });
        assertThrows(IllegalStateException.class,()->ActorSelectionOutline.renderSelected(model,null,Set.of("head"),new MatrixStack(),consumer,0,0,-1));
        assertTrue(ActorSelectionOutline.accepts(body));assertTrue(ActorSelectionOutline.accepts(head));
        assertFalse(head.hidden);assertTrue(head.visible);
    }
    @Test void playerForearmMaskSplitsAnUnauthoredLimbAndRestoresOriginalVisibility() {
        var data=net.minecraft.client.render.entity.model.PlayerEntityModel.getTexturedModelData(Dilation.NONE,false);
        PlayerJointModel.build(data,Dilation.NONE,false);
        var model=new net.minecraft.client.render.entity.model.PlayerEntityModel(TexturedModelData.of(data,64,64).createModel(),false);
        var state=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var arm=model.leftArm;var surface=arm.getChild("director_joint_connector");
        surface.visible=false;assertFalse(arm.hidden);
        for(var selected:List.of(Set.of(ArticulatedPlayerRig.LEFT_FOREARM),Set.of(HumanoidRig.LEFT_ARM),Set.of(HumanoidRig.LEFT_ARM,ArticulatedPlayerRig.LEFT_FOREARM))) {
            boolean upper=selected.contains(HumanoidRig.LEFT_ARM),lower=selected.contains(ArticulatedPlayerRig.LEFT_FOREARM);
            var consumer=(VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class[]{VertexConsumer.class},(proxy,method,args)->{
                assertEquals(upper,ActorSelectionOutline.acceptsQuad(surface,-1));
                assertEquals(lower,ActorSelectionOutline.acceptsQuad(surface,1));
                assertFalse(ActorSelectionOutline.accepts(model.rightArm));
                assertTrue(arm.hidden);assertTrue(surface.visible);
                throw new IllegalStateException("stop after mask inspection");
            });
            assertThrows(IllegalStateException.class,()->ActorSelectionOutline.renderSelected(model,state,selected,new MatrixStack(),consumer,0,0,-1));
            assertFalse(arm.hidden);assertFalse(surface.visible);assertTrue(ActorSelectionOutline.acceptsQuad(surface,-1));
        }
    }
}
