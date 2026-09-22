package com.director.actor.model.bbmodel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses Blockbench .bbmodel JSON into {@link BbModelFile}. */
public final class BbModelParser {
    private static final Gson GSON = new Gson();
    private static final int FILE_SIZE_LIMIT = 4 * 1024 * 1024;
    private static final Type ELEMENT_LIST_TYPE = new TypeToken<List<BbModelFile.Element>>() {}.getType();
    private static final Type TEXTURE_LIST_TYPE = new TypeToken<List<BbModelFile.Texture>>() {}.getType();

    private BbModelParser() {}

    public static BbModelFile parse(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(FILE_SIZE_LIMIT + 1);
            if (bytes.length > FILE_SIZE_LIMIT) {
                throw new BbModelImportException("bbmodel 文件超过 4 MiB 限制");
            }
            return parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    public static BbModelFile parse(String json) throws BbModelImportException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            BbModelFile file = new BbModelFile();

            if (root.has("meta") && !root.get("meta").isJsonNull()) {
                file.meta = GSON.fromJson(root.get("meta"), BbModelFile.Meta.class);
            }
            if (root.has("resolution") && !root.get("resolution").isJsonNull()) {
                file.resolution = GSON.fromJson(root.get("resolution"), BbModelFile.Resolution.class);
            }

            JsonElement elements = required(root, "elements");
            if (!elements.isJsonArray()) {
                throw new BbModelImportException("bbmodel 文件校验失败：elements 必须是数组");
            }
            file.elements = GSON.fromJson(elements, ELEMENT_LIST_TYPE);
            if (file.elements == null) {
                file.elements = List.of();
            }

            JsonElement outliner = required(root, "outliner");
            if (!outliner.isJsonArray()) {
                throw new BbModelImportException("bbmodel 文件校验失败：outliner 必须是数组");
            }
            ParsedOutliner parsedOutliner = parseOutliner(outliner.getAsJsonArray());
            file.outliner = parsedOutliner.groups();
            file.topLevelElementRefs = parsedOutliner.topLevelElementRefs();

            if (root.has("textures") && !root.get("textures").isJsonNull()) {
                file.textures = GSON.fromJson(root.get("textures"), TEXTURE_LIST_TYPE);
            }
            if (file.textures == null) {
                file.textures = List.of();
            }

            return file;
        } catch (BbModelImportException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BbModelImportException("bbmodel 文件校验失败：" + e.getMessage(), e);
        }
    }

    private record ParsedOutliner(List<BbModelFile.OutlinerGroup> groups, List<String> topLevelElementRefs) {}

    private static ParsedOutliner parseOutliner(JsonArray array) {
        List<BbModelFile.OutlinerGroup> groups = new ArrayList<>();
        List<String> topLevelElementRefs = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                topLevelElementRefs.add(element.getAsString());
            } else if (element.isJsonObject()) {
                groups.add(parseGroup(element.getAsJsonObject()));
            }
        }
        return new ParsedOutliner(groups, topLevelElementRefs);
    }

    private static BbModelFile.OutlinerGroup parseGroup(JsonObject object) {
        BbModelFile.OutlinerGroup group = new BbModelFile.OutlinerGroup();
        group.name = stringOrNull(object, "name");
        group.uuid = stringOrNull(object, "uuid");
        group.origin = readFloatTriple(object, "origin");
        group.rotation = readFloatTriple(object, "rotation");
        group.children = new ArrayList<>();

        JsonElement children = object.get("children");
        if (children != null && children.isJsonArray()) {
            for (JsonElement child : children.getAsJsonArray()) {
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    group.children.add(new BbModelFile.OutlinerElementRef(child.getAsString()));
                } else if (child.isJsonObject()) {
                    group.children.add(new BbModelFile.OutlinerGroupChild(parseGroup(child.getAsJsonObject())));
                }
            }
        }
        return group;
    }

    private static float[] readFloatTriple(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 3) {
            return null;
        }
        return new float[] {
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat()
        };
    }

    private static String stringOrNull(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static JsonElement required(JsonObject object, String name) throws BbModelImportException {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            throw new BbModelImportException("bbmodel 文件校验失败：缺少字段 " + name);
        }
        return object.get(name);
    }
}
