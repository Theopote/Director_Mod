package com.director.actor;

import com.director.actor.persistence.ActorRegistryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * ActorRegistryManager v1.0
 *
 * <p>全局 ActorRegistry 管理器（单例模式）
 * <p>负责管理项目级的 ActorRegistry 实例
 *
 * <p>设计特点：
 * <ul>
 *   <li>单例模式：全局唯一实例</li>
 *   <li>线程安全：使用 AtomicReference</li>
 *   <li>项目级：一个世界/项目一个 ActorRegistry</li>
 *   <li>可切换：支持切换项目时更换 ActorRegistry</li>
 * </ul>
 *
 * <p>使用方式：
 * <ul>
 *   <li>获取当前 ActorRegistry：{@link #getInstance()}</li>
 *   <li>创建新项目时：{@link #createNewRegistry()}</li>
 *   <li>切换项目时：{@link #setRegistry(ActorRegistry)}</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorRegistryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRegistryManager.class);
    private static final AtomicReference<ActorRegistry> GLOBAL = new AtomicReference<>();

    /**
     * 获取全局 ActorRegistry 实例
     * <p>如果不存在则自动创建，并尝试从文件加载
     *
     * @return ActorRegistry 实例
     */
    public static ActorRegistry getInstance() {
        ActorRegistry registry = GLOBAL.get();
        if (registry == null) {
            // 自动创建新实例
            registry = new ActorRegistry();
            GLOBAL.set(registry);
            LOGGER.info("[ActorRegistryManager] 自动创建新的 ActorRegistry 实例");
            
            // 尝试从文件加载
            if (ActorRegistryPersistence.hasSavedData()) {
                ActorRegistryPersistence.loadFromFile(registry);
            }
        }
        return registry;
    }

    /**
     * 创建新的 ActorRegistry 实例（用于新项目）
     *
     * @return 新创建的 ActorRegistry
     */
    public static ActorRegistry createNewRegistry() {
        ActorRegistry registry = new ActorRegistry();
        GLOBAL.set(registry);
        LOGGER.info("[ActorRegistryManager] 创建新的 ActorRegistry 实例");
        
        // 尝试从文件加载（如果存在）
        if (ActorRegistryPersistence.hasSavedData()) {
            ActorRegistryPersistence.loadFromFile(registry);
        }
        
        return registry;
    }

    /**
     * 设置全局 ActorRegistry 实例（用于切换项目）
     *
     * @param registry ActorRegistry 实例
     */
    public static void setRegistry(ActorRegistry registry) {
        if (registry == null) {
            LOGGER.warn("[ActorRegistryManager] 尝试设置 null ActorRegistry，将清空当前实例");
            GLOBAL.set(null);
        } else {
            // 保存当前实例（如果存在）
            ActorRegistry oldRegistry = GLOBAL.get();
            if (oldRegistry != null && !oldRegistry.isEmpty()) {
                ActorRegistryPersistence.saveToFile(oldRegistry);
            }
            
            GLOBAL.set(registry);
            LOGGER.info("[ActorRegistryManager] 设置新的 ActorRegistry 实例，包含 {} 个 Actor", registry.size());
            
            // 尝试从文件加载新项目的 ActorRegistry
            if (ActorRegistryPersistence.hasSavedData()) {
                ActorRegistryPersistence.loadFromFile(registry);
            }
        }
    }

    /**
     * 清空当前 ActorRegistry（用于项目卸载）
     */
    public static void clear() {
        ActorRegistry registry = GLOBAL.get();
        if (registry != null) {
            // 保存当前实例
            if (!registry.isEmpty()) {
                ActorRegistryPersistence.saveToFile(registry);
            }
            registry.clear();
            LOGGER.info("[ActorRegistryManager] 清空 ActorRegistry");
        }
    }

    // ⭐ 性能优化：节流保存操作，避免频繁的文件 I/O 阻塞主线程
    private static volatile long lastSaveTime = 0;
    private static volatile boolean isSaving = false; // 防止并发保存
    private static final long MIN_SAVE_INTERVAL_MS = 1000; // 最小保存间隔：1秒（避免频繁保存导致崩溃）
    
    /**
     * 保存当前 ActorRegistry 到文件
     * ⭐ 性能优化：添加节流机制和并发保护，避免频繁保存导致崩溃
     */
    public static void save() {
        // ⭐ 性能优化：节流保存操作，避免频繁的文件 I/O 阻塞主线程
        long currentTime = System.currentTimeMillis();
        
        // 如果正在保存，直接返回（防止并发）
        if (isSaving) {
            return;
        }
        
        // 如果距离上次保存时间太短，跳过本次保存
        if (currentTime - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
            return;
        }
        
        ActorRegistry registry = GLOBAL.get();
        if (registry != null) {
            isSaving = true;
            try {
                ActorRegistryPersistence.saveToFile(registry);
                lastSaveTime = currentTime;
            } catch (Exception e) {
                LOGGER.error("[ActorRegistryManager] 保存 ActorRegistry 时发生异常", e);
            } finally {
                isSaving = false;
            }
        }
    }

    /**
     * 检查是否有活动的 ActorRegistry
     *
     * @return 如果有活动的 ActorRegistry 则返回 true
     */
    public static boolean hasActiveRegistry() {
        return GLOBAL.get() != null;
    }
}

