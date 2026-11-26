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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Manages the on-disk runtime client resource pack used for custom model previews. */
public final class DirectorRuntimeResourcePack {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorRuntimeResourcePack.class);
    private static final String PACK_ID = "director_runtime_custom_models";
    private static final long RELOAD_DEBOUNCE_MS = 200L;
    private static final ScheduledExecutorService RELOAD_DEBOUNCE = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Director-RuntimePackReload");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Path rootDirectory;
    private static volatile ScheduledFuture<?> pendingReload;

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

    /** 1.21.4+ {@code ITEM_MODEL} component resolves {@code assets/<ns>/items/<path>.json}. */
    public static Path itemDefinitionPath(String itemModelPath) {
        return assetsRoot().resolve("items").resolve(itemModelPath + ".json");
    }

    public static void ensurePackSkeleton() throws IOException {
        Path root = rootDirectory();
        Files.createDirectories(assetsRoot());
        Path packMeta = root.resolve("pack.mcmeta");
        String meta = """
                {
                  "pack": {
                    "description": "Director runtime custom models",
                    "pack_format": 69,
                    "min_format": 69,
                    "max_format": 69
                  }
                }
                """.trim() + System.lineSeparator();
        if (!Files.exists(packMeta) || !Files.readString(packMeta).contains("\"pack_format\": 69")) {
            Files.writeString(packMeta, meta, StandardCharsets.UTF_8);
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
        String path = generated.modelId().getPath();
        Path target = modelPath(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, generated.modelJson(), StandardCharsets.UTF_8);
        Path itemDef = itemDefinitionPath(path);
        Files.createDirectories(itemDef.getParent());
        // items/<path>.json → models/item/<path>.json
        String itemJson = """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "director:item/%s"
                  }
                }
                """.formatted(path).trim() + System.lineSeparator();
        Files.writeString(itemDef, itemJson, StandardCharsets.UTF_8);
    }

    /** Shared path token sanitizer; keeps {@code -} so reload paths match import. */
    public static String sanitize(String value) {
        if (value == null || value.isBlank()) return "_";
        return value.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
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

    /**
     * Trailing debounce on a background timer. Never sleep on the client thread —
     * only {@code enable} / {@code reloadResources} run via {@code client.execute}.
     */
    public static synchronized void scheduleReload() {
        ScheduledFuture<?> previous = pendingReload;
        if (previous != null) {
            previous.cancel(false);
        }
        pendingReload = RELOAD_DEBOUNCE.schedule(
                DirectorRuntimeResourcePack::reloadOnClientThread,
                RELOAD_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS);
    }

    private static void reloadOnClientThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            try {
                ResourcePackProfile profile = client.getResourcePackManager().getProfile(PACK_ID);
                if (profile != null) {
                    client.getResourcePackManager().enable(PACK_ID);
                }
                client.reloadResources();
            } catch (RuntimeException e) {
                LOGGER.warn("[DirectorRuntimeResourcePack] 资源重载失败", e);
            }
        });
    }

    private static Path assetsRoot() {
        return rootDirectory().resolve("assets").resolve(DirectorMod.MOD_ID);
    }
}
