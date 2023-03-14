package com.epam.pipeline.manager.audit;

import com.epam.pipeline.manager.audit.entity.StorageDataAccessEntry;

import java.util.List;

public interface AuditClient {

    void put(StorageDataAccessEntry entry);
    void put(StorageDataAccessEntry... entries);
    void putAll(List<StorageDataAccessEntry> entries);
}
