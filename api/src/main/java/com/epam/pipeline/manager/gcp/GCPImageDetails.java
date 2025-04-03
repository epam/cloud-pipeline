package com.epam.pipeline.manager.gcp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GCPImageDetails {
    private String registry;
    private String project;
    private String repository;
    private String image;
    private String digest;
    private String region;
    private String tag;
}
