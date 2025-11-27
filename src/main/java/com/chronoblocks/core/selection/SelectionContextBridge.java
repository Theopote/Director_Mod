package com.chronoblocks.core.selection;

import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.world.snapshot.WorldSnapshotManager;


/**
 * 🧩 SelectionContextBridge v1.0
 * 将选择系统与容器面板连接，生成容器/图层
 * 
 * 功能：
 * - 将选区转换为容器
 * - 管理选区与容器的映射关系
 * - 提供选区上下文信息
 * - 支持选区操作历史
 */
public class SelectionContextBridge {

    public SelectionContextBridge(SelectionManager selectionManager,
                                 ContainerManager containerManager,
                                 WorldSnapshotManager snapshotManager) {
    }
}
