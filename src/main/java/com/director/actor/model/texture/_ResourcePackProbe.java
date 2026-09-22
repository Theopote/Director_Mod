package com.director.actor.model.texture;

import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.UUID;

/** Temporary compile probe — delete after API verification. */
@SuppressWarnings("unused")
final class _ResourcePackProbe {
    void probe(Path root) {
        ResourcePackInfo info = new ResourcePackInfo(
                "director_runtime_custom_models",
                Text.literal("Director Runtime Models"),
                ResourcePackSource.SERVER,
                java.util.Optional.empty(),
                ResourcePackProfileInsertionMode.NORMAL,
                false,
                ResourcePackPosition.TOP);
    }
}
