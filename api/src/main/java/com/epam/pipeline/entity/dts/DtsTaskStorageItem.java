package com.epam.pipeline.entity.dts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DtsTaskStorageItem {
    private String path;
    private DtsTaskStorageType type;
    private DtsPipelineCredentials credentials;
}
