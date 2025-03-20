package com.epam.pipeline.entity.pipeline;

import lombok.Data;

@Data
public class GcpArtifactRegistryEvent {
    private String action;
    private String digest;
    private String tag;
}
