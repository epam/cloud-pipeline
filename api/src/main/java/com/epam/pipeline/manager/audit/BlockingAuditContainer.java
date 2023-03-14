package com.epam.pipeline.manager.audit;

import com.epam.pipeline.exception.audit.AuditException;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class BlockingAuditContainer implements AuditContainer {

    private final BlockingQueue<DataAccessEntry> queue;

    public BlockingAuditContainer(final int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public void put(final DataAccessEntry entry) {
        try {
            queue.add(entry);
        } catch (IllegalStateException e) {
            log.warn("Rejecting audit entry because container is full...");
        }
    }

    @Override
    public void putAll(final List<DataAccessEntry> entries) {
        try {
            queue.addAll(entries);
        } catch (IllegalStateException e) {
            log.warn("Rejecting audit entries ({}) because container is full...", entries.size());
        }
    }

    @Override
    public List<DataAccessEntry> pull() {
        try {
            final DataAccessEntry entry = queue.take();
            final List<DataAccessEntry> entries = new ArrayList<>();
            entries.add(entry);
            queue.drainTo(entries);
            return entries;
        } catch (InterruptedException e) {
            throw new AuditException(e);
        }
    }
}
