package com.epam.pipeline.dto.datastorage.lifecycle.restore;

import lombok.Getter;

@Getter
public enum StorageRestoreStatus {
    INITIATED(true, false),
    RUNNING(true, false),
    SUCCEEDED(true, true),
    CANCELLED(false, true),
    FAILED(false, true);

    private final boolean active;
    private final boolean terminal;

    StorageRestoreStatus(final boolean active, final boolean terminal) {
        this.active = active;
        this.terminal = terminal;
    }

}
