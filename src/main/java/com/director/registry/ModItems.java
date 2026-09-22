package com.director.registry;

import com.director.DirectorMod;
import com.director.item.CustomModelCarrierItem;
import com.director.item.DirectorToolItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * ModItems - Registers all custom items for Director.
 */
public final class ModItems {
    public static Item DIRECTOR_TOOL;
    public static Item CUSTOM_MODEL_CARRIER;

    private ModItems() {}

    public static void register() {
        // Define id and preset registry key on settings BEFORE constructing the Item (MC 1.21+)
        // 使用 DirectorMod.id() 辅助方法统一资源定位
        Identifier id = DirectorMod.id("director_tool");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        DIRECTOR_TOOL = new DirectorToolItem(new Item.Settings().registryKey(key).maxCount(1));

        // Register item
        Registry.register(Registries.ITEM, id, DIRECTOR_TOOL);

        Identifier carrierId = DirectorMod.id("custom_model_carrier");
        RegistryKey<Item> carrierKey = RegistryKey.of(RegistryKeys.ITEM, carrierId);
        CUSTOM_MODEL_CARRIER = new CustomModelCarrierItem(new Item.Settings().registryKey(carrierKey).maxCount(1));
        Registry.register(Registries.ITEM, carrierId, CUSTOM_MODEL_CARRIER);

        // Add to Tools creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(DIRECTOR_TOOL));
    }
}
