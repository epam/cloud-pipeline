package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import com.epam.pipeline.manager.audit.entity.DataAccessEntryType;
import com.epam.pipeline.utils.StreamUtils;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BufferingAuditDaemonTest {

    private static final int TIMEOUT = 5000;
    private static final int CAPACITY = 100;
    private static final int BUFFER_SIZE = 25;
    private static final String USER = "user";
    private static final LocalDateTime NOW = DateUtils.nowUTC();

    private final AuditContainer container = new BlockingAuditContainer(CAPACITY);
    private final AuditConsumer consumer = mock(AuditConsumer.class);

    @Test(timeout = TIMEOUT)
    public void ensureDaemonConsumesEntriesOnBufferTimeout() throws InterruptedException {
        final List<DataAccessEntry> inputs = entries(BUFFER_SIZE / 2);
        final CountDownLatch count = new CountDownLatch(1);
        doAnswer(invocation -> returnNullAnd(count::countDown)).when(consumer).consume(any());

        container.putAll(inputs);
        daemon(100, TimeUnit.MILLISECONDS).start();
        count.await();

        verify(consumer).consume(inputs);
    }

    @Test(timeout = TIMEOUT)
    public void ensureDaemonConsumesEntriesOnBufferSize() throws InterruptedException {
        final List<DataAccessEntry> inputs = entries();
        final List<List<DataAccessEntry>> chunks = chunks(inputs);
        final CountDownLatch count = new CountDownLatch(chunks.size());
        doAnswer(invocation -> returnNullAnd(count::countDown)).when(consumer).consume(any());

        container.putAll(inputs);
        daemon(1, TimeUnit.DAYS).start();
        count.await();

        verify(consumer, times(chunks.size())).consume(any());
        for (final List<DataAccessEntry> chunk : chunks) {
            verify(consumer).consume(chunk);
        }
    }

    private BufferingAuditDaemon daemon(final int time, final TimeUnit unit) {
        return new BufferingAuditDaemon(container, consumer, unit, time, BUFFER_SIZE);
    }

    private <T> List<List<T>> chunks(final List<T> items) {
        return StreamUtils.chunked(items.stream(), BUFFER_SIZE)
                .collect(Collectors.toList());
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

    private Object returnNullAnd(final Runnable runnable) {
        runnable.run();
        return null;
    }
}
