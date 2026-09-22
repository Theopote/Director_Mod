package com.director.actor.model.texture;

import net.minecraft.util.Identifier;

/** A generated client item model resource and its backing texture path. */
public record GeneratedItemModel(Identifier modelId, Identifier textureId, String modelJson) {}
