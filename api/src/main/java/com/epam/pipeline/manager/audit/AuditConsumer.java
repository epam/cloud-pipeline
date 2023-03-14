package com.epam.pipeline.manager.audit;

import com.epam.pipeline.manager.audit.entity.DataAccessEntry;

import java.util.List;

public interface AuditConsumer {

    void consume(List<DataAccessEntry> entries);
}
