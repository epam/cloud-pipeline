package com.epam.pipeline.manager.audit.entity;

import lombok.Value;

@Value
public class StorageDataAccessEntry {

    String storage;
    String path;
    DataAccessEntryType type;
}
