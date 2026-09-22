package com.director.ui.actor.model;

import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.model.bbmodel.BbModelImportResult;
import com.director.actor.model.bbmodel.BbModelImporter;
import com.director.actor.rig.skeleton.CustomRigLibrary;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Undoable Blockbench .bbmodel import into project custom rig + model libraries. */
public final class BbModelAssetEditing {
    private static final Pattern ID_SANITIZER = Pattern.compile("[^a-z0-9_\\-:]");
    private static final String DEFAULT_BLOCK_STATE_ID = "minecraft:white_concrete";

    private BbModelAssetEditing() {}

    public static BbModelImportResult importFile(
            TimelineModel model,
            Path file,
            String rigId,
            String modelId,
            Actor actor) throws IOException {
        return importFile(model, file, rigId, modelId, DEFAULT_BLOCK_STATE_ID, actor);
    }

    public static BbModelImportResult importFile(
            TimelineModel model,
            Path file,
            String rigId,
            String modelId,
            String defaultBlockStateId,
            Actor actor) throws IOException {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(file, "file");
        String resolvedRigId = requireNewId(rigId, "骨架");
        String resolvedModelId = requireNewModelId(modelId, "模型");
        BbModelImportResult result = BbModelImporter.importFile(
                file, resolvedRigId, resolvedModelId, defaultBlockStateId);

        String previousRigId = actor == null ? null : actor.getCustomRigId();
        String previousModelId = actor == null ? null : actor.getCustomModelId();
        CustomRigLibrary rigLibrary = CustomRigLibrary.getInstance();
        CustomModelLibrary modelLibrary = CustomModelLibrary.getInstance();

        TimelineCommandBridge.execute(new StateChangeCommand(model, "Import Blockbench model",
                () -> {
                    rigLibrary.remove(resolvedRigId);
                    modelLibrary.remove(resolvedModelId);
                    restoreActorBinding(actor, previousRigId, previousModelId);
                    deleteExtractedTextures(result.extractedTextures());
                },
                () -> {
                    rigLibrary.register(result.rig());
                    modelLibrary.register(result.model());
                    bindImportedModel(actor, resolvedRigId, resolvedModelId);
                }));
        return result;
    }

    public static String suggestRigId(Path file) {
        return "project:" + sanitizeIdStem(file);
    }

    public static String suggestModelId(Path file) {
        return "project:" + sanitizeIdStem(file) + "_model";
    }

    private static void bindImportedModel(Actor actor, String rigId, String modelId) {
        if (actor == null || actor.getType() != ActorType.CUSTOM) {
            return;
        }
        actor.setCustomRigId(rigId);
        actor.setCustomModelId(modelId);
        ActorRegistryManager.save();
    }

    private static void restoreActorBinding(Actor actor, String rigId, String modelId) {
        if (actor == null) {
            return;
        }
        actor.setCustomRigId(rigId);
        actor.setCustomModelId(modelId);
        ActorRegistryManager.save();
    }

    private static void deleteExtractedTextures(Map<String, Path> extractedTextures) {
        if (extractedTextures == null || extractedTextures.isEmpty()) {
            return;
        }
        for (Path path : extractedTextures.values()) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best-effort cleanup on undo; orphaned PNGs are harmless.
            }
        }
    }

    private static String requireNewId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(label + " ID 不能为空");
        }
        String trimmed = id.trim();
        if (CustomRigLibrary.getInstance().contains(trimmed)) {
            throw new IllegalArgumentException(label + " ID 已存在，请填写新的 ID");
        }
        return trimmed;
    }

    private static String requireNewModelId(String id, String label) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(label + " ID 不能为空");
        }
        String trimmed = id.trim();
        if (CustomModelLibrary.getInstance().contains(trimmed)) {
            throw new IllegalArgumentException(label + " ID 已存在，请填写新的 ID");
        }
        return trimmed;
    }

    private static String sanitizeIdStem(Path file) {
        Path fileName = file.getFileName();
        String stem = fileName == null ? "bbmodel" : fileName.toString();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        stem = stem.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        stem = ID_SANITIZER.matcher(stem).replaceAll("");
        return stem.isBlank() ? "bbmodel" : stem;
    }
}
