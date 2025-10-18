package com.chronoblocks.registry;

import com.chronoblocks.ChronoBlocksMod;
import com.chronoblocks.item.ChronoBlocksToolItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * ModItems - Registers all custom items for ChronoBlocks.
 */
public final class ModItems {
    public static Item CHRONOBLOCKS_TOOL;

    private ModItems() {}

    public static void register() {
        // Define id and preset registry key on settings BEFORE constructing the Item (MC 1.21+)
        // 使用 ChronoBlocksMod.id() 辅助方法统一资源定位
        Identifier id = ChronoBlocksMod.id("chronoblocks_tool");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        CHRONOBLOCKS_TOOL = new ChronoBlocksToolItem(new Item.Settings().registryKey(key).maxCount(1));

        // Register item
        Registry.register(Registries.ITEM, id, CHRONOBLOCKS_TOOL);

        // Add to Tools creative tab
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(CHRONOBLOCKS_TOOL));
    }
}
