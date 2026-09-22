package com.director.actor.model.texture;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/** Temporary compile probe — delete after API verification. */
@SuppressWarnings("unused")
final class _ApiProbe {
    void probe(ItemStack stack, DisplayEntity.ItemDisplayEntity entity) {
        stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("director", "custom_model/test"));
    }
}
