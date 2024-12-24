package com.epam.pipeline.entity.pipeline.run.runtime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RunRuntimeData {
    private final RunSyncRuntimeDataType type;
    private final String data;
}
