package com.epam.pipeline.manager.audit;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.audit.entity.DataAccessEntryType;
import com.epam.pipeline.manager.audit.entity.StorageDataAccessEntry;
import org.junit.Test;

import java.time.Duration;

import static com.epam.pipeline.util.CustomMatchers.matches;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ContainerAuditClientTest {

    private static final String USER = "USER";
    private static final String STORAGE = "storage";
    private static final String PATH = "path/to/file";
    private final AuditContainer container = mock(AuditContainer.class);
    private final AuditClient client = new ContainerAuditClient(DataStorageType.S3, container, USER);

    @Test
    public void ensureClientPutsEntryWithFullStoragePath() {
        client.put(entry());

        verify(container).put(argThat(matches(entry ->
                entry.getPath().equals("s3://storage/path/to/file"))));
    }

    @Test
    public void ensureClientPutsEntryWithRecentTimestamp() {
        client.put(entry());

        verify(container).put(argThat(matches(entry ->
                entry.getTimestamp().isAfter(DateUtils.nowUTC().minus(Duration.ofSeconds(5))))));
    }

    private static StorageDataAccessEntry entry() {
        return new StorageDataAccessEntry(STORAGE, PATH, DataAccessEntryType.READ);
    }
}
