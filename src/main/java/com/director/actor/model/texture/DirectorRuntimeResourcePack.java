package com.director.actor.model.texture;

import com.director.DirectorMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Manages the on-disk runtime client resource pack used for custom model previews. */
public final class DirectorRuntimeResourcePack {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorRuntimeResourcePack.class);
    private static final String PACK_ID = "director_runtime_custom_models";
    private static final long RELOAD_DEBOUNCE_MS = 200L;

    private static volatile Path rootDirectory;
    private static volatile long reloadRequestedAt;
    private static final AtomicBoolean reloadScheduled = new AtomicBoolean(false);

    private DirectorRuntimeResourcePack() {}

    public static Path rootDirectory() {
        Path cached = rootDirectory;
        if (cached != null) {
            return cached;
        }
        Path gameDir = FabricLoader.getInstance().getGameDir();
        cached = gameDir.resolve(".director_runtime_resources");
        rootDirectory = cached;
        return cached;
    }

    public static Path texturePath(String modelId, String textureKey) {
        return assetsRoot()
                .resolve("textures")
                .resolve("custom_model")
                .resolve(sanitize(modelId))
                .resolve(sanitize(textureKey) + ".png");
    }

    public static Path modelPath(String modelPath) {
        return assetsRoot().resolve("models").resolve("item").resolve(modelPath + ".json");
    }

    public static void ensurePackSkeleton() throws IOException {
        Path root = rootDirectory();
        Files.createDirectories(assetsRoot());
        Path packMeta = root.resolve("pack.mcmeta");
        if (!Files.exists(packMeta)) {
            Files.writeString(packMeta, """
                    {
                      "pack": {
                        "description": "Director runtime custom models",
                        "pack_format": 34
                      }
                    }
                    """.trim() + System.lineSeparator(), StandardCharsets.UTF_8);
        }
    }

    public static void writeTexture(Path sourcePng, String modelId, String textureKey) throws IOException {
        ensurePackSkeleton();
        Path target = texturePath(modelId, textureKey);
        Files.createDirectories(target.getParent());
        Files.copy(sourcePng, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static void writeModel(GeneratedItemModel generated) throws IOException {
        ensurePackSkeleton();
        Path target = modelPath(generated.modelId().getPath());
        Files.createDirectories(target.getParent());
        Files.writeString(target, generated.modelJson(), StandardCharsets.UTF_8);
    }

    public static void registerProfile(Consumer<ResourcePackProfile> consumer) {
        Path root = rootDirectory();
        if (!Files.isDirectory(root)) {
            return;
        }
        ResourcePackInfo info = new ResourcePackInfo(
                PACK_ID,
                Text.literal("Director Runtime Models"),
                ResourcePackSource.BUILTIN,
                Optional.empty());
        ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
            @Override
            public ResourcePack open(ResourcePackInfo ignored) {
                return new DirectoryResourcePack(info, root);
            }

            @Override
            public ResourcePack openWithOverlays(ResourcePackInfo ignored, ResourcePackProfile.Metadata metadata) {
                return open(ignored);
            }
        };
        consumer.accept(ResourcePackProfile.create(
                info,
                factory,
                ResourceType.CLIENT_RESOURCES,
                new ResourcePackPosition(false, ResourcePackProfile.InsertionPosition.TOP, false)));
    }

    public static void scheduleReload() {
        reloadRequestedAt = System.currentTimeMillis();
        if (!reloadScheduled.compareAndSet(false, true)) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            reloadScheduled.set(false);
            return;
        }
        client.execute(() -> {
            try {
                long waitUntil = reloadRequestedAt + RELOAD_DEBOUNCE_MS;
                long remaining = waitUntil - System.currentTimeMillis();
                if (remaining > 0) {
                    Thread.sleep(remaining);
                }
                if (reloadRequestedAt + RELOAD_DEBOUNCE_MS > System.currentTimeMillis()) {
                    scheduleReload();
                    return;
                }
                ResourcePackProfile profile = client.getResourcePackManager().getProfile(PACK_ID);
                if (profile != null) {
                    client.getResourcePackManager().enable(PACK_ID);
                }
                client.reloadResources();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.warn("[DirectorRuntimeResourcePack] 资源重载失败", e);
            } finally {
                reloadScheduled.set(false);
                if (System.currentTimeMillis() < reloadRequestedAt + RELOAD_DEBOUNCE_MS) {
                    scheduleReload();
                }
            }
        });
    }

    private static Path assetsRoot() {
        return rootDirectory().resolve("assets").resolve(DirectorMod.MOD_ID);
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }
}
