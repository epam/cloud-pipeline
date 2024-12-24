package com.epam.pipeline.manager.pipeline.runtime;

import com.epam.pipeline.entity.pipeline.run.runtime.RunRuntimeData;
import com.epam.pipeline.entity.pipeline.run.runtime.RunSyncRuntimeDataType;

import java.io.InputStream;
import java.util.Map;

public interface PipelineRunRuntimeDataExtractor {
    String getDataFilePath(Map<String, String> parameters);
    RunRuntimeData parseData(InputStream data);
    RunSyncRuntimeDataType getDataType();
}
