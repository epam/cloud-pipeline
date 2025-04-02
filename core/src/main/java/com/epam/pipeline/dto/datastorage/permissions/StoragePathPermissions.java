package com.epam.pipeline.dto.datastorage.permissions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StoragePathPermissions {
    private String folderPath;
    private String fileName;
    private int mask;
    private String sidName;
    private boolean principal;
}
