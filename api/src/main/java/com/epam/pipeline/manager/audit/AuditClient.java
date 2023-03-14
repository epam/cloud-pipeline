package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuditClient {

    private final DataStorageType type;

    public void put(final DataAccessEntry entry) {
        log.info("{} {}://{}/{}", entry.getType(), type.getId().toLowerCase(), entry.getStorage(), entry.getPath());
    }

    public void put(final DataAccessEntry... entries) {
        for (final DataAccessEntry entry : entries) {
            put(entry);
        }
    }
}
