package com.example.bootserver.order.internal.application.command;

import java.util.Optional;

/** 命令侧加载库存结算所需快照的端口。 */
public interface OrderCommandSnapshotRepository {
    Optional<OrderCommandSnapshot> getCommandSnapshotByIdAndUserId(Long id, Long userId);
}
