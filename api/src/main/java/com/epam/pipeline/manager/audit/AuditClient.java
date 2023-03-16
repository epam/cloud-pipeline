package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.access.DataAccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuditClient {

    private final DataStorageType type;

    public void put(final DataAccessEvent entry) {
        log.info("{} {}://{}/{}", entry.getType(), type.getId().toLowerCase(), entry.getStorage(), entry.getPath());
    }

    public void put(final DataAccessEvent... entries) {
        for (final DataAccessEvent entry : entries) {
            put(entry);
        }
    }
}
