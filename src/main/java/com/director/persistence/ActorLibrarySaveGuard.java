package com.director.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Protects a library file after a failed read until that file is loaded successfully. */
public final class ActorLibrarySaveGuard {
    private final Set<Path> failed = ConcurrentHashMap.newKeySet();

    public void loaded(Path file) { failed.remove(file.toAbsolutePath().normalize()); }
    public void loadFailed(Path file) { failed.add(file.toAbsolutePath().normalize()); }

    public void requireWritable(Path file) throws IOException {
        if (failed.contains(file.toAbsolutePath().normalize()))
            throw new IOException("库文件加载失败，修复并重新加载后才能保存：" + file);
    }
}
