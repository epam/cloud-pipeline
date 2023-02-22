package com.epam.pipeline.entity.datastorage.lifecycle.restore;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum StorageRestoreStatus {

    INITIATED(true, false),
    RUNNING(true, false),
    SUCCEEDED(true, true),
    CANCELLED(false, true),
    FAILED(false, true);

    public static final List<StorageRestoreStatus> ACTIVE_STATUSES = Arrays.stream(StorageRestoreStatus.values())
            .filter(StorageRestoreStatus::isActive).collect(Collectors.toList());

    public static final List<StorageRestoreStatus> TERMINAL_STATUSES = Arrays.stream(StorageRestoreStatus.values())
            .filter(StorageRestoreStatus::isTerminal).collect(Collectors.toList());

    private final boolean active;
    private final boolean terminal;

    StorageRestoreStatus(final boolean active, final boolean terminal) {
        this.active = active;
        this.terminal = terminal;
    }

}
