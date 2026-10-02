package com.director.actor.prop;

import com.director.mixin.accessor.BlockDisplayEntityAccessor;
import com.director.mixin.accessor.DisplayEntityAccessor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One client-only display entity owned by one RuntimeProp. Never changes server blocks or inventory. */
public final class ActorPropPreviewVisual {
    private DisplayEntity entity;
    private ClientWorld world;
    private boolean unavailable;
    public boolean isUnavailable() { return unavailable; }

    /** Block displays use a corner origin; props use a centered half-block default size. */
    public static Matrix4f modelTransform(PropVisualType type, Matrix4f socket) {
        return modelTransform(type,socket,1);
    }
    public static Matrix4f modelTransform(PropVisualType type, Matrix4f socket, float scale) {
        if (!Float.isFinite(scale) || scale < .01f || scale > 10) throw new IllegalArgumentException("Invalid prop scale");
        var model=new Matrix4f(socket).scale(scale);
        if (type==PropVisualType.BLOCK) model.scale(.5f).translate(-.5f,-.5f,-.5f);
        return model;
    }
    public void sync(ClientWorld nextWorld, PropDescriptor descriptor, Matrix4f socket, boolean visible, float scale, String itemPose, ActorPropSlot slot) {
        if (nextWorld!=world) { dispose(); world=nextWorld; unavailable=false; }
        if (nextWorld==null || socket==null || !socket.isFinite() || !visible) {
            discard(); return;
        }
        if (entity!=null && entity.isRemoved()) entity=null;
        if (entity==null && !unavailable) {
            entity=create(nextWorld,descriptor);
            if (entity==null) { unavailable=true; return; }
            entity.setInvulnerable(true); entity.setNoGravity(true); entity.setSilent(true);
            nextWorld.addEntity(entity);
        }
        if (entity==null) return;
        if (entity instanceof DisplayEntity.ItemDisplayEntity item)
            item.setItemDisplayContext("HAND".equals(itemPose)
                    ? (slot==ActorPropSlot.OFF_HAND?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    : ItemDisplayContext.FIXED);
        var origin=socket.getTranslation(new Vector3f());
        entity.setPosition(origin.x,origin.y,origin.z);
        // Retain the full affine matrix, including nonuniform parent scale/shear.
        var local=modelTransform(descriptor.getVisualType(),socket,scale);
        local.setTranslation(local.getTranslation(new Vector3f()).sub(origin));
        ((DisplayEntityAccessor)entity).director$invokeSetTransformation(new AffineTransformation(local));
    }
    private static DisplayEntity create(ClientWorld world,PropDescriptor descriptor) {
        var id=Identifier.tryParse(descriptor.getBlockStateId()==null?"":descriptor.getBlockStateId());
        if (id==null) return null;
        if (descriptor.getVisualType()==PropVisualType.BLOCK && Registries.BLOCK.containsId(id)) {
            var display=new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,world);
            ((BlockDisplayEntityAccessor)display).director$invokeSetBlockState(Registries.BLOCK.get(id).getDefaultState());
            return display;
        }
        if (descriptor.getVisualType()==PropVisualType.ITEM && Registries.ITEM.containsId(id)) {
            var display=new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY,world);
            display.setItemStack(new ItemStack(Registries.ITEM.get(id)));
            display.setItemDisplayContext(ItemDisplayContext.FIXED);
            return display;
        }
        return null; // Unsupported custom models do not silently render a different asset.
    }
    private void discard() { if (entity!=null) entity.discard(); entity=null; }
    public void dispose() { discard(); world=null; unavailable=false; }
}
