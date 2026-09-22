package com.director.actor.model.bbmodel;

import com.director.actor.model.CustomModelDefinition;
import com.director.actor.rig.skeleton.RigDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Result of importing a Blockbench .bbmodel file into Director rig + model definitions. */
public record BbModelImportResult(
        RigDefinition rig,
        CustomModelDefinition model,
        List<String> warnings,
        Map<String, Path> extractedTextures,
        int textureAtlasWidth,
        int textureAtlasHeight
) {}
