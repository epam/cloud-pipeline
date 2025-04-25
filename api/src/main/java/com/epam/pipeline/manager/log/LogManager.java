package com.epam.pipeline.manager.log;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.entity.log.LogRequest;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public interface LogManager {
    String TYPE = "type";
    String SERVICE_NAME = "service_name";
    String HOSTNAME = "hostname";
    String USER = "user";
    String SERVICE_ACCOUNT = "service_account";
    String MESSAGE = "message";
    String MESSAGE_TIMESTAMP = "message_timestamp";
    String SEVERITY = "level";
    String ID = "event_id";
    String DEFAULT_SEVERITY = "INFO";
    String STORAGE_ID = "storage_id";
    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    LogPagination filter(LogFilter logFilter);

    Map<String, Long> group(LogRequest logRequest);

    LogFilter getFilters();

    void save(List<LogEntry> logEntries);
}
