package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.audit.entity.DataAccessEntry;
import com.epam.pipeline.manager.audit.entity.DataAccessEntryType;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

public class BlockingAuditContainerTest {

    private static final int TIMEOUT = 5000;
    private static final int CAPACITY = 100;
    private static final String PATH = "path";
    private static final String USER = "user";
    private static final LocalDateTime NOW = DateUtils.nowUTC();

    private final AuditContainer audit = new BlockingAuditContainer(CAPACITY);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Test(timeout = TIMEOUT)
    public void pullShallWaitForSingleEntry() throws ExecutionException, InterruptedException {
        final DataAccessEntry entry = entry(PATH);

        executor.execute(() -> audit.put(entry));
        final List<DataAccessEntry> output = collect(Collections.singletonList(entry));

        assertThat(output.size(), equalTo(1));
        assertThat(output.get(0), equalTo(entry));
    }

    @Test(timeout = TIMEOUT)
    public void pullShallWaitForMultipleEntriesAddedAtOnce() throws ExecutionException, InterruptedException {
        final List<DataAccessEntry> input = entries();

        executor.execute(() -> audit.putAll(input));
        final List<DataAccessEntry> output = collect(input);

        assertThat(output.size(), equalTo(input.size()));
        assertThat(output, containsInAnyOrder(input.toArray()));
    }

    @Test(timeout = TIMEOUT)
    public void pullShallWaitForMultipleEntriesAddedSeparately() throws ExecutionException, InterruptedException {
        final List<DataAccessEntry> inputs = entries();

        inputs.forEach(entry -> executor.execute(() -> audit.put(entry)));
        final List<DataAccessEntry> outputs = collect(inputs);

        assertThat(outputs.size(), equalTo(inputs.size()));
        assertThat(outputs, containsInAnyOrder(inputs.toArray()));
    }

    @Test(timeout = TIMEOUT)
    public void putShallIgnoreOverCapacityEntries() throws ExecutionException, InterruptedException {
        final List<DataAccessEntry> inputs = entries();

        audit.putAll(inputs);
        audit.put(entry(PATH));
        final List<DataAccessEntry> outputs = collect(inputs);

        assertThat(outputs.size(), equalTo(inputs.size()));
        assertThat(outputs, containsInAnyOrder(inputs.toArray()));
    }

    private List<DataAccessEntry> entries() {
        return IntStream.range(0, CAPACITY)
                .mapToObj(Integer::toString)
                .map(this::entry)
                .collect(Collectors.toList());
    }

    private DataAccessEntry entry(final String path) {
        return new DataAccessEntry(path, DataAccessEntryType.READ, NOW, USER);
    }

    private List<DataAccessEntry> collect(final List<DataAccessEntry> inputs)
            throws ExecutionException, InterruptedException {
        final List<DataAccessEntry> outputs = new ArrayList<>();
        while (outputs.size() < inputs.size()) {
            outputs.addAll(executor.submit(audit::pull).get());
        }
        return outputs;
    }
}
