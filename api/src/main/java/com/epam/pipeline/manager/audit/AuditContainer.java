package com.epam.pipeline.manager.audit;

import com.epam.pipeline.manager.audit.entity.DataAccessEntry;

import java.util.List;

public interface AuditContainer {

    void put(DataAccessEntry entry);

    void putAll(List<DataAccessEntry> entries);

    List<DataAccessEntry> pull();
}
