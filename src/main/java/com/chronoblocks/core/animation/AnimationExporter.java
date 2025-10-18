package com.chronoblocks.core.animation;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 动画导出工具 v4.5 - 导出和导入动画数据
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>导出为 JSON 格式</li>
 *   <li>导入 JSON 动画数据</li>
 *   <li>批量导出多个动画片段</li>
 *   <li>版本兼容性检查</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 导出动画
 * AnimationExporter.export(animationClip, "my_animation.json");
 * 
 * // 导入动画
 * AnimationClip clip = AnimationExporter.importClip("my_animation.json");
 * 
 * // 批量导出
 * AnimationExporter.exportBatch(clipList, "animations/");
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class AnimationExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationExporter.class);
    
    /** 当前版本 */
    private static final String VERSION = "4.5";
    
    /** Gson 实例 */
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    
    // ==================== 导出方法 ====================
    
    /**
     * 导出动画片段为 JSON
     * 
     * @param clip 动画片段
     * @param filePath 文件路径
     * @return 是否成功
     */
    public static boolean export(AnimationClip clip, String filePath) {
        if (clip == null || filePath == null) {
            LOGGER.error("[AnimationExporter] 参数为空");
            return false;
        }
        
        try {
            // 创建 JSON 对象
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("name", clip.getName());
            root.addProperty("duration", clip.computeDuration());
            
            // 导出轨道
            JsonArray tracksArray = new JsonArray();
            for (Track track : clip.getTracks()) {
                tracksArray.add(exportTrack(track));
            }
            root.add("tracks", tracksArray);
            
            // 写入文件
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
            
            LOGGER.info("[AnimationExporter] 成功导出动画: {} -> {}", clip.getName(), filePath);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("[AnimationExporter] 导出失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 导出轨道
     */
    private static JsonObject exportTrack(Track track) {
        JsonObject trackObj = new JsonObject();
        trackObj.addProperty("name", track.getName());
        trackObj.addProperty("targetId", track.getTargetId());
        trackObj.addProperty("targetIsContainer", track.isTargetContainer());
        
        // 导出关键帧
        JsonArray keyframesArray = new JsonArray();
        for (Keyframe kf : track.getKeyframes()) {
            keyframesArray.add(exportKeyframe(kf));
        }
        trackObj.add("keyframes", keyframesArray);
        
        return trackObj;
    }
    
    /**
     * 导出关键帧
     */
    private static JsonObject exportKeyframe(Keyframe kf) {
        JsonObject kfObj = new JsonObject();
        kfObj.addProperty("time", kf.getTime());
        
        // 位置
        JsonArray posArray = new JsonArray();
        for (float v : kf.getPosition()) {
            posArray.add(v);
        }
        kfObj.add("position", posArray);
        
        // 旋转
        JsonArray rotArray = new JsonArray();
        for (float v : kf.getRotation()) {
            rotArray.add(v);
        }
        kfObj.add("rotation", rotArray);
        
        // 缩放
        JsonArray scaleArray = new JsonArray();
        for (float v : kf.getScale()) {
            scaleArray.add(v);
        }
        kfObj.add("scale", scaleArray);
        
        // 不透明度
        kfObj.addProperty("opacity", kf.getOpacity());
        
        // 插值类型
        kfObj.addProperty("interpolationType", kf.getInterpolationType().name());
        
        // 贝塞尔控制点
        if (kf.getBezierControlPoints() != null) {
            JsonArray bezierArray = new JsonArray();
            for (float v : kf.getBezierControlPoints()) {
                bezierArray.add(v);
            }
            kfObj.add("bezierControlPoints", bezierArray);
        }
        
        // 自定义参数
        if (kf.getCustomParams() != null) {
            JsonArray paramsArray = new JsonArray();
            for (float v : kf.getCustomParams()) {
                paramsArray.add(v);
            }
            kfObj.add("customParams", paramsArray);
        }
        
        return kfObj;
    }
    
    // ==================== 导入方法 ====================
    
    /**
     * 从 JSON 导入动画片段
     * 
     * @param filePath 文件路径
     * @return 动画片段，失败返回 null
     */
    public static AnimationClip importClip(String filePath) {
        if (filePath == null) {
            LOGGER.error("[AnimationExporter] 文件路径为空");
            return null;
        }
        
        try {
            // 读取文件
            Path path = Paths.get(filePath);
            String jsonContent = Files.readString(path);
            
            // 解析 JSON
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            
            // 版本检查
            String version = root.get("version").getAsString();
            if (!isVersionCompatible(version)) {
                LOGGER.warn("[AnimationExporter] 版本不兼容: {} (当前: {})", version, VERSION);
            }
            
            // 创建动画片段
            String name = root.get("name").getAsString();
            AnimationClip clip = new AnimationClip(name);
            
            // 导入轨道
            JsonArray tracksArray = root.getAsJsonArray("tracks");
            for (JsonElement trackElement : tracksArray) {
                Track track = importTrack(trackElement.getAsJsonObject());
                clip.addTrack(track);
            }
            
            LOGGER.info("[AnimationExporter] 成功导入动画: {} <- {}", name, filePath);
            return clip;
            
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("[AnimationExporter] 导入失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 导入轨道
     */
    private static Track importTrack(JsonObject trackObj) {
        String name = trackObj.get("name").getAsString();
        String targetId = trackObj.get("targetId").getAsString();
        boolean targetIsContainer = trackObj.get("targetIsContainer").getAsBoolean();
        
        Track track = new Track(name, targetId, targetIsContainer);
        
        // 导入关键帧
        JsonArray keyframesArray = trackObj.getAsJsonArray("keyframes");
        for (JsonElement kfElement : keyframesArray) {
            Keyframe kf = importKeyframe(kfElement.getAsJsonObject());
            track.addKeyframe(kf);
        }
        
        return track;
    }
    
    /**
     * 导入关键帧
     */
    private static Keyframe importKeyframe(JsonObject kfObj) {
        float time = kfObj.get("time").getAsFloat();
        Keyframe kf = new Keyframe(time);
        
        // 位置
        JsonArray posArray = kfObj.getAsJsonArray("position");
        kf.setPosition(
            posArray.get(0).getAsFloat(),
            posArray.get(1).getAsFloat(),
            posArray.get(2).getAsFloat()
        );
        
        // 旋转
        JsonArray rotArray = kfObj.getAsJsonArray("rotation");
        kf.setRotation(
            rotArray.get(0).getAsFloat(),
            rotArray.get(1).getAsFloat(),
            rotArray.get(2).getAsFloat()
        );
        
        // 缩放
        JsonArray scaleArray = kfObj.getAsJsonArray("scale");
        kf.setScale(
            scaleArray.get(0).getAsFloat(),
            scaleArray.get(1).getAsFloat(),
            scaleArray.get(2).getAsFloat()
        );
        
        // 不透明度
        kf.setOpacity(kfObj.get("opacity").getAsFloat());
        
        // 插值类型
        String interpTypeName = kfObj.get("interpolationType").getAsString();
        InterpolationType interpType = InterpolationType.fromString(interpTypeName);
        kf.setInterpolationType(interpType);
        
        // 贝塞尔控制点
        if (kfObj.has("bezierControlPoints")) {
            JsonArray bezierArray = kfObj.getAsJsonArray("bezierControlPoints");
            float[] bezierPoints = new float[bezierArray.size()];
            for (int i = 0; i < bezierArray.size(); i++) {
                bezierPoints[i] = bezierArray.get(i).getAsFloat();
            }
            kf.setBezierControlPoints(bezierPoints);
        }
        
        // 自定义参数
        if (kfObj.has("customParams")) {
            JsonArray paramsArray = kfObj.getAsJsonArray("customParams");
            float[] params = new float[paramsArray.size()];
            for (int i = 0; i < paramsArray.size(); i++) {
                params[i] = paramsArray.get(i).getAsFloat();
            }
            kf.setCustomParams(params);
        }
        
        return kf;
    }
    
    // ==================== 批量操作 ====================
    
    /**
     * 批量导出动画片段
     * 
     * @param clips 动画片段列表
     * @param directory 目标目录
     * @return 成功数量
     */
    public static int exportBatch(List<AnimationClip> clips, String directory) {
        if (clips == null || directory == null) {
            LOGGER.error("[AnimationExporter] 参数为空");
            return 0;
        }
        
        int successCount = 0;
        
        for (AnimationClip clip : clips) {
            String fileName = sanitizeFileName(clip.getName()) + ".json";
            String filePath = Paths.get(directory, fileName).toString();
            
            if (export(clip, filePath)) {
                successCount++;
            }
        }
        
        LOGGER.info("[AnimationExporter] 批量导出完成: {}/{}", successCount, clips.size());
        return successCount;
    }
    
    /**
     * 批量导入动画片段
     * 
     * @param directory 源目录
     * @return 导入的动画片段列表
     */
    public static List<AnimationClip> importBatch(String directory) {
        List<AnimationClip> clips = new ArrayList<>();
        
        try {
            Path dirPath = Paths.get(directory);
            if (!Files.isDirectory(dirPath)) {
                LOGGER.error("[AnimationExporter] 不是有效的目录: {}", directory);
                return clips;
            }
            
            try (var stream = Files.walk(dirPath, 1)) {
                stream.filter(path -> path.toString().endsWith(".json"))
                      .forEach(path -> {
                          AnimationClip clip = importClip(path.toString());
                          if (clip != null) {
                              clips.add(clip);
                          }
                      });
            }
            
            LOGGER.info("[AnimationExporter] 批量导入完成: {} 个动画", clips.size());
            
        } catch (IOException e) {
            LOGGER.error("[AnimationExporter] 批量导入失败: {}", e.getMessage(), e);
        }
        
        return clips;
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 检查版本兼容性
     */
    private static boolean isVersionCompatible(String version) {
        // 简单的版本检查：主版本号相同即可
        String[] currentParts = VERSION.split("\\.");
        String[] fileParts = version.split("\\.");
        
        if (currentParts.length > 0 && fileParts.length > 0) {
            return currentParts[0].equals(fileParts[0]);
        }
        
        return false;
    }
    
    /**
     * 清理文件名（移除非法字符）
     */
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
    
    /**
     * 获取导出的 JSON 字符串（不写入文件）
     */
    public static String toJson(AnimationClip clip) {
        if (clip == null) {
            return "{}";
        }
        
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("name", clip.getName());
        root.addProperty("duration", clip.computeDuration());
        
        JsonArray tracksArray = new JsonArray();
        for (Track track : clip.getTracks()) {
            tracksArray.add(exportTrack(track));
        }
        root.add("tracks", tracksArray);
        
        return GSON.toJson(root);
    }
    
    /**
     * 从 JSON 字符串导入（不读取文件）
     */
    public static AnimationClip fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            String name = root.get("name").getAsString();
            AnimationClip clip = new AnimationClip(name);
            
            JsonArray tracksArray = root.getAsJsonArray("tracks");
            for (JsonElement trackElement : tracksArray) {
                Track track = importTrack(trackElement.getAsJsonObject());
                clip.addTrack(track);
            }
            
            return clip;
            
        } catch (JsonSyntaxException e) {
            LOGGER.error("[AnimationExporter] JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}

