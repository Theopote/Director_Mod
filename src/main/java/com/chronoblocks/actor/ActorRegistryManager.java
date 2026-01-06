package com.chronoblocks.actor;

import com.chronoblocks.actor.persistence.ActorRegistryPersistence;
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
 * @author ChronoBlocks Team
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

    /**
     * 保存当前 ActorRegistry 到文件
     */
    public static void save() {
        ActorRegistry registry = GLOBAL.get();
        if (registry != null) {
            ActorRegistryPersistence.saveToFile(registry);
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

