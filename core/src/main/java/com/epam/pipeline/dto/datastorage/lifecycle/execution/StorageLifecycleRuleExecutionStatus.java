package com.epam.pipeline.dto.datastorage.lifecycle.execution;

import java.time.LocalDateTime;

public class StorageLifecycleRuleExecutionStatus {

    private LocalDateTime updated;
    private StorageLifecycleRuleExecutionStatusType status;
    private String storageType;

    public enum StorageLifecycleRuleExecutionStatusType {
        RUNNING, SUCCESS, FAILED
    }

}
