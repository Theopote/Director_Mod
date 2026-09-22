package com.director.actor.model.bbmodel;

import com.director.actor.model.BoneCuboid;
import com.director.actor.model.CustomModelDefinition;
import com.director.actor.model.texture.CuboidFaceUvs;
import com.director.actor.model.texture.CustomModelResourceBuilder;
import com.director.actor.rig.skeleton.BoneCapabilities;
import com.director.actor.rig.skeleton.BoneDefinition;
import com.director.actor.rig.skeleton.BoneTransform;
import com.director.actor.rig.skeleton.RigDefinition;
import com.director.util.WorldIdentifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Converts parsed Blockbench .bbmodel data into Director rig + custom model definitions. */
public final class BbModelImporter {
    private static final float PIXELS_PER_BLOCK = 16f;
    /** Tune after calibrating against a known Blockbench export. */
    private static final boolean MIRROR_X = false;
    private static final int BONE_COUNT_WARN_THRESHOLD = 64;
    private static final float[] ZERO_ORIGIN = {0f, 0f, 0f};
    private static final Pattern BONE_ID_SANITIZER = Pattern.compile("[^a-z0-9_]");
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";

    private BbModelImporter() {}

    /**
     * @param file                .bbmodel file path
     * @param rigId               generated {@link RigDefinition} id
     * @param modelId             generated {@link CustomModelDefinition} id
     * @param defaultBlockStateId placeholder block for every cuboid until the texture pipeline is wired
     */
    public static BbModelImportResult importFile(
            Path file, String rigId, String modelId, String defaultBlockStateId) throws IOException {
        if (rigId == null || rigId.isBlank()) {
            throw new BbModelImportException("骨架 id 不能为空");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new BbModelImportException("模型 id 不能为空");
        }
        if (defaultBlockStateId == null || defaultBlockStateId.isBlank()) {
            throw new BbModelImportException("占位方块 id 不能为空");
        }

        BbModelFile bbModel = BbModelParser.parse(file);
        List<String> warnings = new ArrayList<>();
        warnUnsupportedModelFormat(bbModel, warnings);

        Map<String, BbModelFile.Element> elementsByUuid = indexElements(bbModel.elements, warnings);
        OutlinerLayout layout = analyzeOutliner(bbModel.outliner, bbModel.topLevelElementRefs, elementsByUuid.keySet(), warnings);

        Map<String, String> boneRenameMap = new LinkedHashMap<>();
        Set<String> usedBoneIds = new HashSet<>();
        List<BoneDefinition> bones = new ArrayList<>();
        Map<String, String> elementToBone = new HashMap<>();
        Map<String, float[]> boneOrigins = new HashMap<>();

        if (layout.syntheticRoot()) {
            String rootId = allocateBoneId("root", usedBoneIds, boneRenameMap);
            bones.add(rootBone(rootId));
            boneOrigins.put(rootId, ZERO_ORIGIN);
            int rootIndex = 0;
            for (BbModelFile.OutlinerGroup group : layout.groups()) {
                walkGroup(group, rootIndex, ZERO_ORIGIN, bones, elementToBone, boneOrigins, usedBoneIds, boneRenameMap, warnings);
            }
            for (String elementUuid : layout.unparentedTopLevelElements()) {
                elementToBone.put(elementUuid, rootId);
            }
        } else if (layout.groups().size() == 1) {
            walkGroup(layout.groups().getFirst(), -1, ZERO_ORIGIN, bones, elementToBone, boneOrigins, usedBoneIds, boneRenameMap, warnings);
        } else {
            throw new BbModelImportException("bbmodel 文件校验失败：outliner 未定义任何骨骼");
        }

        assignUnreferencedElements(elementsByUuid.keySet(), elementToBone, bones, warnings);

        if (bones.isEmpty()) {
            throw new BbModelImportException("bbmodel 文件校验失败：未解析到任何骨骼");
        }
        appendBoneRenameWarnings(boneRenameMap, warnings);
        if (bones.size() > BONE_COUNT_WARN_THRESHOLD) {
            warnings.add("骨骼较多（" + bones.size() + " 根），可能影响预览性能");
        }

        String displayName = deriveDisplayName(file);
        RigDefinition rig = new RigDefinition(rigId, displayName, bones);
        int atlasWidth = bbModel.resolution == null ? 64 : Math.max(1, bbModel.resolution.width);
        int atlasHeight = bbModel.resolution == null ? 64 : Math.max(1, bbModel.resolution.height);
        List<BoneCuboid> cuboids = buildCuboids(
                elementsByUuid, elementToBone, boneOrigins, defaultBlockStateId, modelId, bbModel.textures, warnings);
        CustomModelDefinition model = new CustomModelDefinition(modelId, displayName, rigId, cuboids);
        Map<String, Path> extractedTextures = extractTextures(bbModel.textures, modelId, warnings);

        return new BbModelImportResult(
                rig, model, List.copyOf(warnings), Map.copyOf(extractedTextures), atlasWidth, atlasHeight);
    }

    private record OutlinerLayout(
            boolean syntheticRoot,
            List<BbModelFile.OutlinerGroup> groups,
            List<String> unparentedTopLevelElements
    ) {}

    private static OutlinerLayout analyzeOutliner(
            List<BbModelFile.OutlinerGroup> outliner,
            List<String> topLevelElementRefs,
            Set<String> elementUuids,
            List<String> warnings) throws BbModelImportException {
        List<BbModelFile.OutlinerGroup> groups = outliner == null ? List.of() : outliner;
        List<String> topRefs = topLevelElementRefs == null ? List.of() : topLevelElementRefs;
        int topLevelNodeCount = groups.size() + topRefs.size();

        if (topLevelNodeCount == 0) {
            if (elementUuids.isEmpty()) {
                throw new BbModelImportException("bbmodel 文件校验失败：outliner 为空且没有 elements");
            }
            warnings.add("outliner 为空，已创建合成 root 骨骼承载所有元素");
            return new OutlinerLayout(true, List.of(), List.copyOf(elementUuids));
        }

        if (topLevelNodeCount > 1 || !topRefs.isEmpty()) {
            if (topLevelNodeCount > 1) {
                warnings.add("outliner 顶层有 " + topLevelNodeCount + " 个节点，已插入合成 root 骨骼");
            } else {
                warnings.add("outliner 顶层包含未分组的 element，已插入合成 root 骨骼");
            }
            return new OutlinerLayout(true, List.copyOf(groups), List.copyOf(topRefs));
        }
        return new OutlinerLayout(false, List.copyOf(groups), List.of());
    }

    private static Map<String, BbModelFile.Element> indexElements(List<BbModelFile.Element> elements, List<String> warnings) {
        Map<String, BbModelFile.Element> indexed = new LinkedHashMap<>();
        if (elements == null) {
            return indexed;
        }
        for (BbModelFile.Element element : elements) {
            if (element == null || element.uuid == null || element.uuid.isBlank()) {
                warnings.add("跳过缺少 uuid 的 element");
                continue;
            }
            if (element.type != null && !element.type.isBlank() && !"cube".equalsIgnoreCase(element.type)) {
                warnings.add("跳过不支持的 element 类型 \"" + element.type + "\"（仅支持 cube）");
                continue;
            }
            indexed.put(element.uuid, element);
        }
        return indexed;
    }

    private static BoneDefinition rootBone(String rootId) {
        return new BoneDefinition(
                rootId,
                rootId,
                -1,
                BoneTransform.identity(),
                BoneCapabilities.full());
    }

    private static void walkGroup(
            BbModelFile.OutlinerGroup group,
            int parentIndex,
            float[] parentOrigin,
            List<BoneDefinition> bones,
            Map<String, String> elementToBone,
            Map<String, float[]> boneOrigins,
            Set<String> usedBoneIds,
            Map<String, String> boneRenameMap,
            List<String> warnings) {
        String rawName = group.name == null || group.name.isBlank() ? "bone" : group.name;
        String boneId = allocateBoneId(rawName, usedBoneIds, boneRenameMap);
        float[] origin = group.origin != null ? group.origin : ZERO_ORIGIN;
        float[] rotation = group.rotation != null ? group.rotation : ZERO_ORIGIN;

        Vector3f localTranslation = localTranslation(origin, parentOrigin, parentIndex);
        Quaternionf localRotation = eulerDegreesToQuaternion(rotation);
        BoneCapabilities capabilities = parentIndex == -1 ? BoneCapabilities.full() : BoneCapabilities.rotationAndScale();

        int boneIndex = bones.size();
        bones.add(new BoneDefinition(
                boneId,
                rawName,
                parentIndex,
                new BoneTransform(localTranslation, localRotation, new Vector3f(1f, 1f, 1f)),
                capabilities));
        boneOrigins.put(boneId, origin);

        if (group.children != null) {
            for (BbModelFile.OutlinerChild child : group.children) {
                switch (child) {
                    case BbModelFile.OutlinerElementRef(String elementUuid) -> elementToBone.put(elementUuid, boneId);
                    case BbModelFile.OutlinerGroupChild(BbModelFile.OutlinerGroup nested) ->
                            walkGroup(nested, boneIndex, origin, bones, elementToBone, boneOrigins, usedBoneIds, boneRenameMap, warnings);
                }
            }
        }
    }

    private static void assignUnreferencedElements(
            Set<String> allElementUuids,
            Map<String, String> elementToBone,
            List<BoneDefinition> bones,
            List<String> warnings) {
        Set<String> unreferenced = new HashSet<>(allElementUuids);
        unreferenced.removeAll(elementToBone.keySet());
        if (unreferenced.isEmpty()) {
            return;
        }

        String rootBoneId = bones.getFirst().id();
        for (String elementUuid : unreferenced) {
            elementToBone.put(elementUuid, rootBoneId);
        }
        warnings.add("有 " + unreferenced.size() + " 个 element 未出现在 outliner 中，已挂到骨骼 \"" + rootBoneId + "\"");
    }

    private static List<BoneCuboid> buildCuboids(
            Map<String, BbModelFile.Element> elementsByUuid,
            Map<String, String> elementToBone,
            Map<String, float[]> boneOrigins,
            String defaultBlockStateId,
            String modelId,
            List<BbModelFile.Texture> textures,
            List<String> warnings) {
        List<BoneCuboid> cuboids = new ArrayList<>();
        int ignoredRotations = 0;
        int invalidGeometry = 0;

        for (Map.Entry<String, String> entry : elementToBone.entrySet()) {
            BbModelFile.Element element = elementsByUuid.get(entry.getKey());
            if (element == null) {
                warnings.add("outliner 引用了不存在的 element uuid: " + entry.getKey());
                continue;
            }
            if (hasNonZeroRotation(element.rotation)) {
                ignoredRotations++;
            }

            float[] from = element.from;
            float[] to = element.to;
            if (from == null || to == null || from.length < 3 || to.length < 3) {
                invalidGeometry++;
                continue;
            }

            Vector3f size = new Vector3f(
                    (to[0] - from[0]) / PIXELS_PER_BLOCK,
                    (to[1] - from[1]) / PIXELS_PER_BLOCK,
                    (to[2] - from[2]) / PIXELS_PER_BLOCK);
            if (size.x <= 0f || size.y <= 0f || size.z <= 0f) {
                invalidGeometry++;
                continue;
            }

            float[] boneOrigin = boneOrigins.getOrDefault(entry.getValue(), ZERO_ORIGIN);
            Vector3f offset = new Vector3f(
                    (from[0] - boneOrigin[0]) / PIXELS_PER_BLOCK,
                    (from[1] - boneOrigin[1]) / PIXELS_PER_BLOCK,
                    (from[2] - boneOrigin[2]) / PIXELS_PER_BLOCK);
            applyMirrorX(offset);

            try {
                String textureKey = resolveElementTextureKey(element, textures);
                CuboidFaceUvs faceUvs = parseFaceUvs(element);
                if (textureKey != null && faceUvs != null && faceUvs.hasAnyFace()) {
                    BoneCuboid draft = new BoneCuboid(entry.getValue(), offset, size, defaultBlockStateId, null, textureKey, faceUvs);
                    String pieceKey = CustomModelResourceBuilder.pieceKey(draft, modelId, textureKey);
                    String itemModelId = "director:custom_model_piece/" + pieceKey;
                    cuboids.add(new BoneCuboid(
                            entry.getValue(), offset, size, defaultBlockStateId, itemModelId, textureKey, faceUvs));
                } else {
                    cuboids.add(new BoneCuboid(entry.getValue(), offset, size, defaultBlockStateId));
                }
            } catch (IllegalArgumentException e) {
                invalidGeometry++;
            }
        }

        if (ignoredRotations > 0) {
            warnings.add("忽略了 " + ignoredRotations + " 个 cuboid 自身旋转（MVP 仅支持骨骼级旋转）");
        }
        if (invalidGeometry > 0) {
            warnings.add("跳过了 " + invalidGeometry + " 个无效 cuboid（尺寸必须大于 0）");
        }
        return cuboids;
    }

    private static Map<String, Path> extractTextures(List<BbModelFile.Texture> textures, String modelId, List<String> warnings) {
        Map<String, Path> extracted = new LinkedHashMap<>();
        if (textures == null || textures.isEmpty()) {
            return extracted;
        }

        Path outputRoot = textureOutputRoot(modelId);
        if (outputRoot == null) {
            warnings.add("无法保存贴图：未检测到世界存档目录（贴图数据已解析但未写入磁盘）");
            return extracted;
        }

        try {
            Files.createDirectories(outputRoot);
        } catch (IOException e) {
            warnings.add("无法创建贴图目录：" + e.getMessage());
            return extracted;
        }

        for (BbModelFile.Texture texture : textures) {
            if (texture == null || texture.source == null || texture.source.isBlank()) {
                continue;
            }
            String textureKey = textureKey(texture);
            byte[] pngBytes = decodeTextureSource(texture.source);
            if (pngBytes == null) {
                warnings.add("贴图 \"" + textureKey + "\" 解码失败，已跳过");
                continue;
            }
            Path target = outputRoot.resolve(sanitizeTextureFileName(textureKey) + ".png");
            try {
                Files.write(target, pngBytes);
                extracted.put(textureKey, target);
            } catch (IOException e) {
                warnings.add("贴图 \"" + textureKey + "\" 写入失败：" + e.getMessage());
            }
        }
        return extracted;
    }

    private static Path textureOutputRoot(String modelId) {
        Path worldSave = WorldIdentifier.getWorldSavePath();
        if (worldSave == null) {
            return null;
        }
        return worldSave.resolve("director").resolve("custom_model_textures").resolve(sanitizeTextureFileName(modelId));
    }

    private static byte[] decodeTextureSource(String source) {
        String payload = source.startsWith(DATA_URL_PREFIX) ? source.substring(DATA_URL_PREFIX.length()) : source;
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String textureKey(BbModelFile.Texture texture) {
        if (texture.id != null && !texture.id.isBlank()) {
            return texture.id;
        }
        if (texture.uuid != null && !texture.uuid.isBlank()) {
            return texture.uuid;
        }
        if (texture.name != null && !texture.name.isBlank()) {
            return texture.name;
        }
        return "texture";
    }

    private static String sanitizeTextureFileName(String value) {
        return BONE_ID_SANITIZER.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("_");
    }

    private static Vector3f localTranslation(float[] origin, float[] parentOrigin, int parentIndex) {
        float dx = origin[0];
        float dy = origin[1];
        float dz = origin[2];
        if (parentIndex != -1) {
            dx -= parentOrigin[0];
            dy -= parentOrigin[1];
            dz -= parentOrigin[2];
        }
        Vector3f translation = new Vector3f(dx / PIXELS_PER_BLOCK, dy / PIXELS_PER_BLOCK, dz / PIXELS_PER_BLOCK);
        applyMirrorX(translation);
        return translation;
    }

    private static Quaternionf eulerDegreesToQuaternion(float[] rotationDegrees) {
        float x = (float) Math.toRadians(rotationDegrees[0]);
        float y = (float) Math.toRadians(rotationDegrees[1]);
        float z = (float) Math.toRadians(rotationDegrees[2]);
        return new Quaternionf().rotateXYZ(x, y, z);
    }

    private static boolean hasNonZeroRotation(float[] rotation) {
        if (rotation == null || rotation.length < 3) {
            return false;
        }
        return Math.abs(rotation[0]) > 1.0e-4f
                || Math.abs(rotation[1]) > 1.0e-4f
                || Math.abs(rotation[2]) > 1.0e-4f;
    }

    private static void applyMirrorX(Vector3f vector) {
        if (MIRROR_X) {
            vector.x = -vector.x;
        }
    }

    private static String allocateBoneId(String rawName, Set<String> usedBoneIds, Map<String, String> boneRenameMap) {
        String sanitized = sanitizeBoneId(rawName);
        if (sanitized.isBlank()) {
            sanitized = "bone";
        }
        String candidate = sanitized;
        int suffix = 2;
        while (!usedBoneIds.add(candidate)) {
            candidate = sanitized + "_" + suffix++;
        }
        if (!candidate.equals(rawName)) {
            boneRenameMap.put(rawName, candidate);
        }
        return candidate;
    }

    private static String sanitizeBoneId(String rawName) {
        String normalized = rawName.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return BONE_ID_SANITIZER.matcher(normalized).replaceAll("");
    }

    private static void appendBoneRenameWarnings(Map<String, String> boneRenameMap, List<String> warnings) {
        if (boneRenameMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : boneRenameMap.entrySet()) {
            warnings.add("骨骼 \"" + entry.getKey() + "\" 已重命名为 \"" + entry.getValue() + "\"");
        }
    }

    private static void warnUnsupportedModelFormat(BbModelFile bbModel, List<String> warnings) {
        if (bbModel.meta == null || bbModel.meta.model_format == null || bbModel.meta.model_format.isBlank()) {
            return;
        }
        String format = bbModel.meta.model_format.toLowerCase(Locale.ROOT);
        if (!"free".equals(format) && !"bedrock".equals(format)) {
            warnings.add("model_format=\"" + bbModel.meta.model_format + "\" 将按 free 模型处理");
        }
    }

    private static CuboidFaceUvs parseFaceUvs(BbModelFile.Element element) {
        if (element.faces == null || element.faces.isEmpty()) {
            return null;
        }
        return new CuboidFaceUvs(
                readFaceUv(element.faces.get("north")),
                readFaceUv(element.faces.get("south")),
                readFaceUv(element.faces.get("east")),
                readFaceUv(element.faces.get("west")),
                readFaceUv(element.faces.get("up")),
                readFaceUv(element.faces.get("down")));
    }

    private static float[] readFaceUv(BbModelFile.Face face) {
        if (face == null || face.uv == null || face.uv.length < 4) {
            return null;
        }
        return new float[] {face.uv[0], face.uv[1], face.uv[2], face.uv[3]};
    }

    private static String resolveElementTextureKey(BbModelFile.Element element, List<BbModelFile.Texture> textures) {
        if (element.faces == null || element.faces.isEmpty()) {
            return null;
        }
        Object textureRef = null;
        for (BbModelFile.Face face : element.faces.values()) {
            if (face != null && face.texture != null) {
                textureRef = face.texture;
                break;
            }
        }
        if (textureRef == null) {
            return null;
        }
        int index = parseTextureIndex(textureRef);
        if (textures != null && index >= 0 && index < textures.size()) {
            return textureKey(textures.get(index));
        }
        return String.valueOf(index);
    }

    private static int parseTextureIndex(Object textureRef) {
        if (textureRef instanceof Number number) {
            return number.intValue();
        }
        if (textureRef instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String deriveDisplayName(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return "Imported Model";
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
