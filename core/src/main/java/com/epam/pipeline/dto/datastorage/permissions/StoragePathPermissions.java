package com.epam.pipeline.dto.datastorage.permissions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class StoragePathPermissions {
    private String folderPath;
    private String fileName;
    private int mask;
}
