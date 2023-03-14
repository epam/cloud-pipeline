package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.exception.audit.AuditException;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import com.epam.pipeline.manager.audit.entity.DataAccessEntryType;
import com.epam.pipeline.manager.log.LogManager;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LogAuditConsumerTest {

    private static final int CAPACITY = 100;
    private static final String USER = "user";
    private static final LocalDateTime NOW = DateUtils.nowUTC();

    private static final String HOSTNAME = "HOSTNAME";

    private final LogManager manager = mock(LogManager.class);
    private final AuditConsumer consumer = new LogAuditConsumer(HOSTNAME, manager);

    @Test
    public void consumeShouldNotFailOnError() {
        doThrow(AuditException.class).when(manager).save(any());

        consumer.consume(Collections.emptyList());
    }

    @Test
    public void consumeShouldSaveEntriesWithUniqueEventIds() {
        final List<DataAccessEntry> entries = entries();

        consumer.consume(entries);

        verify(manager).save(argThat(matches(logEntries ->
                entries.size() == logEntries.stream().map(LogEntry::getEventId).distinct().count())));
    }

    private List<DataAccessEntry> entries() {
        return entries(CAPACITY);
    }

    private List<DataAccessEntry> entries(final int capacity) {
        return IntStream.range(0, capacity)
                .mapToObj(Integer::toString)
                .map(this::entry)
                .collect(Collectors.toList());
    }

    private DataAccessEntry entry(final String path) {
        return new DataAccessEntry(path, DataAccessEntryType.READ, NOW, USER);
    }
}
