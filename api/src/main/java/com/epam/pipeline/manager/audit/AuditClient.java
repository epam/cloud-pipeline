package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.access.DataAccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;

@Slf4j
@RequiredArgsConstructor
public class AuditClient {

    private static final String KEY_STORAGE_ID = "storage_id";

    private final DataStorageType type;

    public void put(final DataAccessEvent entry) {
        ThreadContext.put(KEY_STORAGE_ID, entry.getStorage().getId().toString());
        log.info("{} {}://{}/{}", entry.getType(), type.getId().toLowerCase(), entry.getStorage().getRoot(),
                entry.getPath());
    }

    public void put(final DataAccessEvent... entries) {
        for (final DataAccessEvent entry : entries) {
            put(entry);
        }
    }
}
