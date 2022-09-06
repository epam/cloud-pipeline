package com.epam.pipeline.dto.datastorage.lifecycle.restore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageRestorePath {
    private String path;
    private StorageRestorePathType type;
}
