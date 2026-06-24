package com.director.export.video;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 解析 FFmpeg 可执行文件路径。
 *
 * <p>查找顺序：
 * <ol>
 *   <li>JVM 属性 {@code -Ddirector.ffmpeg=完整路径}</li>
 *   <li>游戏根目录（通常为 {@code .minecraft}）及其子目录（浅层搜索）</li>
 *   <li>系统 PATH 中的 {@code ffmpeg.exe} / {@code ffmpeg}</li>
 * </ol>
 */
public final class FFmpegLocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FFmpegLocator.class);

    public static final String JVM_PROPERTY = "director.ffmpeg";

    /** 浅层递归最大深度（相对游戏根目录） */
    private static final int MAX_SEARCH_DEPTH = 4;

    /** 优先检查的相对子路径（常见解压/安装位置） */
    private static final String[] PREFERRED_RELATIVE_PATHS = {
            "ffmpeg.exe",
            "ffmpeg/ffmpeg.exe",
            "ffmpeg/bin/ffmpeg.exe",
            "bin/ffmpeg.exe",
            "tools/ffmpeg.exe",
            "director/ffmpeg.exe",
            "native/ffmpeg.exe",
            // 非 Windows
            "ffmpeg",
            "ffmpeg/ffmpeg",
            "ffmpeg/bin/ffmpeg",
            "bin/ffmpeg",
            "tools/ffmpeg",
            "director/ffmpeg",
            "native/ffmpeg",
    };

    /** 跳过扫描的目录名（避免遍历存档、依赖等大目录） */
    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "saves",
            "mods",
            "assets",
            "versions",
            "libraries",
            "logs",
            "crash-reports",
            "resourcepacks",
            "shaderpacks",
            "screenshots",
            "data",
            "runtime",
            "caches",
            "webcache",
            "downloads",
            "backup",
            "backups"
    );

    public enum Source {
        JVM_PROPERTY,
        GAME_DIR,
        PATH_FALLBACK
    }

    public record Result(String path, Source source) {
        public boolean isPathFallback() {
            return source == Source.PATH_FALLBACK;
        }
    }

    private FFmpegLocator() {
    }

    /**
     * @return 用于 {@link ProcessBuilder} 的可执行路径或命令名
     */
    public static String resolveBinary() {
        return resolve().path();
    }

    public static Result resolve() {
        String override = System.getProperty(JVM_PROPERTY);
        if (override != null && !override.isBlank()) {
            return new Result(override.trim(), Source.JVM_PROPERTY);
        }

        String binaryName = executableFileName();
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            String fromGameDir = findUnderGameDir(gameDir, binaryName);
            if (fromGameDir != null) {
                return new Result(fromGameDir, Source.GAME_DIR);
            }
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[FFmpegLocator] 游戏目录搜索失败: {}", e.toString());
            }
        }

        return new Result(binaryName, Source.PATH_FALLBACK);
    }

    /**
     * 运行 {@code ffmpeg -version} 验证可执行性。
     */
    public static boolean verifyExecutable(String ffmpegPath) {
        return verifyExecutable(ffmpegPath, 2, TimeUnit.SECONDS);
    }

    public static boolean verifyExecutable(String ffmpegPath, long timeout, TimeUnit unit) {
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            return false;
        }
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean exited = p.waitFor(timeout, unit);
            if (!exited) {
                p.destroy();
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[FFmpegLocator] 验证失败 ({}): {}", ffmpegPath, e.toString());
            }
            return false;
        }
    }

    public static String executableFileName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
    }

    private static String findUnderGameDir(Path gameDir, String binaryName) throws IOException {
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            return null;
        }

        for (String relative : PREFERRED_RELATIVE_PATHS) {
            if (!relative.endsWith(binaryName)) {
                continue;
            }
            Path candidate = gameDir.resolve(relative);
            if (isExecutableFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }

        Path rootCandidate = gameDir.resolve(binaryName);
        if (isExecutableFile(rootCandidate)) {
            return rootCandidate.toAbsolutePath().toString();
        }

        return shallowSearch(gameDir, binaryName, MAX_SEARCH_DEPTH);
    }

    private static String shallowSearch(Path root, String binaryName, int maxDepth) throws IOException {
        if (maxDepth < 1) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> shouldConsiderPath(root, path))
                    .filter(path -> binaryName.equals(path.getFileName().toString()))
                    .filter(FFmpegLocator::isExecutableFile)
                    .map(path -> path.toAbsolutePath().toString())
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean shouldConsiderPath(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String segment = relative.getName(i).toString().toLowerCase(Locale.ROOT);
            if (SKIP_DIR_NAMES.contains(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExecutableFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        if (!Files.isExecutable(path)) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            // Windows 上 isExecutable 对 .exe 可能为 false，存在即可
            return os.contains("win") && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe");
        }
        return true;
    }
}
