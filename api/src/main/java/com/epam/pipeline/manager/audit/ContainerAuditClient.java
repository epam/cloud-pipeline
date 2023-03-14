package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import com.epam.pipeline.manager.audit.entity.StorageDataAccessEntry;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ContainerAuditClient implements AuditClient {

    private final DataStorageType type;
    private final AuditContainer container;
    private final String user;

    @Override
    public void put(final StorageDataAccessEntry entry) {
        container.put(toEntry(entry));
    }

    @Override
    public void put(final StorageDataAccessEntry... entries) {
        putAll(Arrays.asList(entries));
    }

    @Override
    public void putAll(final List<StorageDataAccessEntry> entries) {
        container.putAll(entries.stream().map(this::toEntry).collect(Collectors.toList()));
    }

    private DataAccessEntry toEntry(final StorageDataAccessEntry entry) {
        return new DataAccessEntry(toPath(entry.getStorage(), entry.getPath()), entry.getType(),
                DateUtils.nowUTC(), user);
    }

    private String toPath(final String root, final String path) {
        return type.getId().toLowerCase() + "://" + root + "/" + path;
    }
}
