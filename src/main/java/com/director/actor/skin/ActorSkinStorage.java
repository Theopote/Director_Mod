package com.director.actor.skin;

import com.director.util.WorldIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * ActorSkinStorage
 *
 * <p>负责管理演员皮肤 PNG 文件在当前世界存档下的位置与读写。
 *
 * <p>存储约定：
 * <ul>
 *   <li>根目录：{@code saves/<world>/director/skins}</li>
 *   <li>文件名：{@code <actorId>.png}</li>
 * </ul>
 */
public final class ActorSkinStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSkinStorage.class);
    private static final String SKINS_DIR_NAME = "skins";

    private ActorSkinStorage() {
    }

    /**
     * 返回当前世界下的 skins 根目录（不存在则尝试创建）。
     *
     * @return 目录路径，若当前无活动世界则为 null
     */
    public static Path getSkinsRoot() {
        Path worldDir = getWorldDirectorDir();
        if (worldDir == null) {
            return null;
        }
        Path skins = worldDir.resolve(SKINS_DIR_NAME);
        try {
            if (!Files.exists(skins)) {
                Files.createDirectories(skins);
            }
        } catch (IOException e) {
            LOGGER.warn("[ActorSkinStorage] 无法创建皮肤目录: {}", skins, e);
            return null;
        }
        return skins;
    }

    /**
     * 计算给定演员的皮肤文件相对路径（用于持久化到 Actor.skinFileName）。
     */
    public static String getRelativeSkinFileName(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        return SKINS_DIR_NAME + "/" + actorId + ".png";
    }

    /**
     * 返回给定演员皮肤文件的绝对路径（不保证存在）。
     */
    public static Path getSkinFile(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        Path root = getSkinsRoot();
        if (root == null) {
            return null;
        }
        return root.resolve(actorId + ".png");
    }

    /**
     * 将用户选择的 PNG 文件导入为指定演员的皮肤。
     *
     * @param source  源 PNG 文件
     * @param actorId 演员 ID
     * @return 相对 `director` 目录的文件名（可直接存入 Actor.skinFileName），若失败则返回 null
     */
    public static String importSkin(Path source, UUID actorId) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(actorId, "actorId");

        if (!Files.exists(source)) {
            LOGGER.warn("[ActorSkinStorage] 源皮肤文件不存在: {}", source);
            return null;
        }

        Path target = getSkinFile(actorId);
        if (target == null) {
            return null;
        }

        // 防止目录穿越：目标始终在 skins 根目录下
        Path root = getSkinsRoot();
        if (root == null) {
            return null;
        }
        if (!target.normalize().startsWith(root.normalize())) {
            LOGGER.warn("[ActorSkinStorage] 目标路径越界，已拒绝: {}", target);
            return null;
        }

        try {
            Files.createDirectories(root);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[ActorSkinStorage] 导入皮肤: {} -> {}", source, target);
            return getRelativeSkinFileName(actorId);
        } catch (IOException e) {
            LOGGER.error("[ActorSkinStorage] 导入皮肤失败: {} -> {}", source, target, e);
            return null;
        }
    }

    /**
     * 删除指定演员的皮肤文件（若存在）。
     */
    public static void deleteSkin(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        Path file = getSkinFile(actorId);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
            LOGGER.info("[ActorSkinStorage] 删除皮肤文件: {}", file);
        } catch (IOException e) {
            LOGGER.warn("[ActorSkinStorage] 删除皮肤文件失败: {}", file, e);
        }
    }

    private static Path getWorldDirectorDir() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath == null) {
                return null;
            }
            Path dir = savePath.resolve("director");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir;
        } catch (Exception e) {
            LOGGER.warn("[ActorSkinStorage] 无法获取世界 director 目录", e);
            return null;
        }
    }
}

