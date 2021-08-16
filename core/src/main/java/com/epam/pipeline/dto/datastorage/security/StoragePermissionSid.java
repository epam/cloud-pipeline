package com.epam.pipeline.dto.datastorage.security;

import lombok.Value;

@Value
public class StoragePermissionSid {

    String name;
    StoragePermissionSidType type;

    public static StoragePermissionSid user(final String name) {
        return new StoragePermissionSid(name, StoragePermissionSidType.USER);
    }

    public static StoragePermissionSid group(final String name) {
        return new StoragePermissionSid(name, StoragePermissionSidType.GROUP);
    }
}
