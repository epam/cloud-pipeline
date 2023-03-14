package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import com.epam.pipeline.manager.log.LogManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class LogAuditConsumer implements AuditConsumer {

    private static final String LOG_SEVERITY = "INFO";
    private static final String LOG_SERVICE = "api-srv";
    private static final String LOG_TYPE = "audit";
    private final String host;

    private final LogManager manager;

    @Override
    public void consume(final List<DataAccessEntry> entries) {
        try {
            manager.save(toLogs(entries));
        } catch (Exception e) {
            log.error("Audit entries have not been saved due to error", e);
        }
    }

    private List<LogEntry> toLogs(final List<DataAccessEntry> entries) {
        log.debug("Preparing audit entries ({})...", entries.size());
        return entries.stream().map(this::toLog).collect(Collectors.toList());
    }

    private LogEntry toLog(final DataAccessEntry entry) {
        return LogEntry.builder()
                .eventId(System.nanoTime())
                .hostname(host)
                .message(toMessage(entry))
                .messageTimestamp(entry.getTimestamp())
                .serviceName(LOG_SERVICE)
                .type(LOG_TYPE)
                .user(entry.getUser())
                .severity(LOG_SEVERITY)
                .build();
    }

    private String toMessage(final DataAccessEntry entry) {
        return entry.getType() + " " + entry.getPath();
    }
}
