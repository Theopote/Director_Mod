package com.chronoblocks.core.selection.block_filters;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 💾 方块过滤器预设管理器
 * 
 * 负责保存和加载过滤器预设到 JSON 文件。
 * - 支持预设的增删改查
 * - 自动持久化到配置文件
 * - 内置默认预设
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class BlockFilterPresetManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockFilterPresetManager.class);
    private static final String PRESET_FILE = "config/chronoblocks/filter_presets.json";
    
    private static final Map<String, BlockFilterPipeline> presets = new LinkedHashMap<>();
    
    static {
        loadDefaultPresets();
        loadPresetsFromFile();
    }
    
    /**
     * 加载默认预设
     */
    private static void loadDefaultPresets() {
        // 建筑材料预设（使用基础过滤器）
        BlockFilterPipeline buildingMaterials = new BlockFilterPipeline()
            .add(BlockFilters.solid());
        presets.put("building_materials", buildingMaterials);
        
        // 自然方块预设
        BlockFilterPipeline naturalBlocks = new BlockFilterPipeline()
            .add(BlockFilters.solid());
        presets.put("natural_blocks", naturalBlocks);
        
        // 透明方块预设
        BlockFilterPipeline transparentBlocks = new BlockFilterPipeline()
            .add(BlockFilters.transparent());
        presets.put("transparent_blocks", transparentBlocks);
        
        // 非空气预设
        BlockFilterPipeline nonAir = new BlockFilterPipeline()
            .add(BlockFilters.nonAir());
        presets.put("non_air", nonAir);
        
        // 可替换预设
        BlockFilterPipeline replaceable = new BlockFilterPipeline()
            .add(BlockFilters.replaceable());
        presets.put("replaceable", replaceable);
        
        LOGGER.info("[BlockFilterPresetManager] 加载了 {} 个默认预设", presets.size());
    }
    
    /**
     * 从文件加载预设
     */
    private static void loadPresetsFromFile() {
        Path path = Paths.get(PRESET_FILE);
        if (!Files.exists(path)) {
            LOGGER.debug("[BlockFilterPresetManager] 预设文件不存在，使用默认预设");
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject presetsObj = root.getAsJsonObject("presets");
            
            if (presetsObj != null) {
                for (Map.Entry<String, JsonElement> entry : presetsObj.entrySet()) {
                    String presetId = entry.getKey();
                    JsonArray filtersArray = entry.getValue().getAsJsonArray();
                    
                    BlockFilterPipeline pipeline = new BlockFilterPipeline();
                    for (JsonElement filterElem : filtersArray) {
                        String filterId = filterElem.getAsString();
                        BlockFilter filter = BlockFilterRegistry.getFilter(filterId);
                        if (filter != null) {
                            pipeline.add(filter);
                        } else {
                            LOGGER.warn("[BlockFilterPresetManager] 未找到过滤器: {}", filterId);
                        }
                    }
                    
                    // 不覆盖默认预设
                    if (!presets.containsKey(presetId)) {
                        presets.put(presetId, pipeline);
                    }
                }
            }

            if (presetsObj != null) {
                LOGGER.info("[BlockFilterPresetManager] 从文件加载了 {} 个预设", presetsObj.size());
            }

        } catch (Exception e) {
            LOGGER.error("[BlockFilterPresetManager] 加载预设失败", e);
        }
    }
    
    /**
     * 保存预设到文件
     */
    public static void savePresetsToFile() {
        Path path = Paths.get(PRESET_FILE);
        try {
            Files.createDirectories(path.getParent());
            
            JsonObject root = new JsonObject();
            JsonObject presetsObj = new JsonObject();
            
            for (Map.Entry<String, BlockFilterPipeline> entry : presets.entrySet()) {
                String presetId = entry.getKey();
                BlockFilterPipeline pipeline = entry.getValue();
                
                JsonArray filtersArray = new JsonArray();
                for (BlockFilter filter : pipeline.getFilters()) {
                    // 尝试从注册表中找到过滤器ID
                    String filterId = BlockFilterRegistry.getFilterId(filter);
                    if (filterId != null) {
                        filtersArray.add(filterId);
                    } else {
                        LOGGER.warn("[BlockFilterPresetManager] 无法序列化过滤器: {}", 
                            filter.getClass().getSimpleName());
                    }
                }
                
                presetsObj.add(presetId, filtersArray);
            }
            
            root.add("presets", presetsObj);
            
            try (Writer writer = Files.newBufferedWriter(path)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(root, writer);
            }
            
            LOGGER.info("[BlockFilterPresetManager] 保存了 {} 个预设到文件", presets.size());
            
        } catch (Exception e) {
            LOGGER.error("[BlockFilterPresetManager] 保存预设失败", e);
        }
    }
    
    /**
     * 获取预设
     */
    public static BlockFilterPipeline getPreset(String presetId) {
        BlockFilterPipeline pipeline = presets.get(presetId);
        return pipeline != null ? pipeline.clone() : null;
    }
    
    /**
     * 保存预设
     */
    public static void savePreset(String presetId, BlockFilterPipeline pipeline) {
        presets.put(presetId, pipeline.clone());
        savePresetsToFile();
        LOGGER.info("[BlockFilterPresetManager] 保存预设: {}", presetId);
    }
    
    /**
     * 删除预设
     */
    public static void deletePreset(String presetId) {
        if (presets.remove(presetId) != null) {
            savePresetsToFile();
            LOGGER.info("[BlockFilterPresetManager] 删除预设: {}", presetId);
        }
    }
    
    /**
     * 获取所有预设ID
     */
    public static Set<String> getPresetIds() {
        return new LinkedHashSet<>(presets.keySet());
    }
    
    /**
     * 检查预设是否存在
     */
    public static boolean hasPreset(String presetId) {
        return presets.containsKey(presetId);
    }
    
    /**
     * 重命名预设
     */
    public static void renamePreset(String oldId, String newId) {
        BlockFilterPipeline pipeline = presets.remove(oldId);
        if (pipeline != null) {
            presets.put(newId, pipeline);
            savePresetsToFile();
            LOGGER.info("[BlockFilterPresetManager] 重命名预设: {} -> {}", oldId, newId);
        }
    }
}

