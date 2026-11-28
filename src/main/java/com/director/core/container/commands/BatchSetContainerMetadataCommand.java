package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 批量设置容器元数据命令，支持撤销/重做。
 */
public class BatchSetContainerMetadataCommand implements ContainerCommand {

    private final ContainerManager manager;
    private final List<UUID> containerIds = new ArrayList<>();
    private final String key;
    private final Object newValue;
    private final Map<UUID, MetadataSnapshot> oldMetadata = new HashMap<>();

    public BatchSetContainerMetadataCommand(ContainerManager manager,
                                            Collection<Container> containers,
                                            String key,
                                            Object newValue) {
        this.manager = manager;
        this.key = key;
        this.newValue = newValue;

        for (Container container : containers) {
            if (container != null) {
                containerIds.add(container.getId());
            }
        }
    }

    @Override
    public void execute() {
        List<Container> resolved = resolveEditableTargets("批量修改元数据");
        if (resolved == null) {
            return;
        }

        oldMetadata.clear();
        for (Container container : resolved) {
            oldMetadata.put(container.getId(), new MetadataSnapshot(
                container.hasMetadata(key),
                container.getMetadata(key)
            ));

            if (newValue == null) {
                container.removeMetadata(key);
            } else {
                container.setMetadata(key, newValue);
            }
        }
    }

    @Override
    public void undo() {
        List<Container> resolved = new ArrayList<>();
        for (UUID containerId : containerIds) {
            Container container = manager.getContainer(containerId);
            if (container != null && oldMetadata.containsKey(containerId)) {
                resolved.add(container);
            }
        }
        if (!manager.requireAllEditable(resolved, "撤销批量元数据修改")) {
            return;
        }
        for (Container container : resolved) {
            MetadataSnapshot snapshot = oldMetadata.get(container.getId());
            if (!snapshot.hadKey) {
                container.removeMetadata(key);
            } else {
                container.setMetadata(key, snapshot.oldValue);
            }
        }
    }

    private List<Container> resolveEditableTargets(String action) {
        List<Container> resolved = new ArrayList<>();
        for (UUID containerId : containerIds) {
            Container container = manager.getContainer(containerId);
            if (container != null) {
                resolved.add(container);
            }
        }
        return manager.requireAllEditable(resolved, action) ? resolved : null;
    }

    @Override
    public String getDescription() {
        return String.format("批量设置 %d 个容器的元数据: %s", containerIds.size(), key);
    }

    private static final class MetadataSnapshot {
        private final boolean hadKey;
        private final Object oldValue;

        private MetadataSnapshot(boolean hadKey, Object oldValue) {
            this.hadKey = hadKey;
            this.oldValue = oldValue;
        }
    }
}
