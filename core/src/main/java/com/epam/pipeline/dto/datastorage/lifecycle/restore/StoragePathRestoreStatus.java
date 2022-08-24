package com.epam.pipeline.dto.datastorage.lifecycle.restore;

import lombok.Getter;

@Getter
public enum StoragePathRestoreStatus {
    INITIATED(true), RUNNING(true), SUCCESS(true), FAILED(false);

    private final boolean active;

    StoragePathRestoreStatus(final boolean active) {
        this.active = active;
    }

}
