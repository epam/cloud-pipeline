package com.epam.pipeline.entity.dts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DtsTransferStorageItem {
    private String path;
    private DtsTransferStorageType type;
    private DtsPipelineCredentials credentials;
}
