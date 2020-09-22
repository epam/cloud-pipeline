package com.epam.pipeline.entity.dts;

import lombok.Value;

import java.nio.file.Path;

@Value
public class DtsDataStorageListingRequest {
    Path path;
    Integer pageSize;
    String marker;
    PipelineCredentials credentials;
}
