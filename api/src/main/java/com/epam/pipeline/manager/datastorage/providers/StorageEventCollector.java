package com.epam.pipeline.manager.datastorage.providers;

import com.epam.pipeline.entity.datastorage.access.DataAccessEvent;
import com.epam.pipeline.manager.audit.AuditClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StorageEventCollector {

    private final AuditClient audit;

    public void put(DataAccessEvent entry) {
        audit.put(entry);
    }

    public void put(DataAccessEvent... entries) {
        audit.put(entries);
    }
}
