package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.NodeDiskDao;
import com.epam.pipeline.entity.cluster.DiskRegistrationRequest;
import com.epam.pipeline.entity.cluster.NodeDisk;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("PMD.TooManyStaticImports")
public class NodeDiskManagerTest {

    private static final String NODE_ID = "NODE_ID";
    private static final String NULL_NODE_ID = null;
    private static final Long SIZE = 1L;
    private static final Long ZERO_SIZE = 0L;
    private static final Long NEGATIVE_SIZE = -1L;
    private static final Long NULL_SIZE = null;

    private final NodeDiskDao nodeDiskDao = mock(NodeDiskDao.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final NodeDiskManager manager = new NodeDiskManager(nodeDiskDao, messageHelper);

    @Before
    public void mockInsertingDisk() {
        doReturn(Collections.singletonList(disk())).when(nodeDiskDao).insert(any(), any(DiskRegistrationRequest.class));
    }

    @Test
    public void insertShouldFailOnMissingDiskSize() {
        assertThrows(IllegalArgumentException.class, () -> register(NODE_ID, request(NULL_SIZE)));
    }

    @Test
    public void insertShouldFailOnZeroDiskSize() {
        assertThrows(IllegalArgumentException.class, () -> register(NODE_ID, request(ZERO_SIZE)));
    }

    @Test
    public void insertShouldFailOnNegativeDiskSize() {
        assertThrows(IllegalArgumentException.class, () -> register(NODE_ID, request(NEGATIVE_SIZE)));
    }
    
    @Test
    public void insertShouldFailOnMissingNodeId() {
        assertThrows(IllegalArgumentException.class, () -> register(NULL_NODE_ID, request(SIZE)));
    }

    @Test
    public void insertShouldSaveDisk() {
        register(NODE_ID, request(SIZE));
        
        verify(nodeDiskDao).insert(eq(NODE_ID), eq(Collections.singletonList(request(SIZE))));
    }

    @Test
    public void insertShouldReturnSavedDisk() {
        final NodeDisk actualDisk = register(NODE_ID, request(SIZE));

        assertThat(actualDisk, is(disk()));
    }

    private NodeDisk register(final String nodeId, final DiskRegistrationRequest request) {
        return manager.register(nodeId, request);
    }

    private NodeDisk disk() {
        return new NodeDisk(SIZE, NODE_ID, LocalDateTime.MIN);
    }

    private DiskRegistrationRequest request(final Long size) {
        return new DiskRegistrationRequest(size);
    }
}
