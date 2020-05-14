package com.epam.pipeline.dao.cluster;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.cluster.DiskRegistrationRequest;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.utils.DateUtils;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class NodeDiskDaoTest extends AbstractSpringTest {
    
    private static final String NODE_ID = "NODE_ID";
    private static final String ANOTHER_NODE_ID = "ANOTHER_NODE_ID";
    private static final String NULL_NODE_ID = null;
    private static final Long SIZE = 1L;
    private static final Long NULL_SIZE = null;

    @Autowired
    private NodeDiskDao dao;

    @After
    public void deleteDisksByNodeId() {
        dao.deleteByNodeId(NODE_ID);
        dao.deleteByNodeId(ANOTHER_NODE_ID);
    }

    @Test
    public void insertShouldFailOnMissingNodeId() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(NULL_NODE_ID, diskRequestOf(SIZE)));
    }

    @Test
    public void insertShouldFailOnMissingSize() {
        assertThrows(DataIntegrityViolationException.class, () -> insert(NODE_ID, diskRequestOf(NULL_SIZE)));
    }

    @Test
    public void insertShouldSaveDiskParameters() {
        final List<NodeDisk> disks = insert(NODE_ID, diskRequestOf(SIZE));
        
        assertThat(disks.size(), is(1));
        final NodeDisk disk = disks.get(0);
        assertThat(disk.getSize(), is(SIZE));
        assertThat(disk.getNodeId(), is(NODE_ID));
        final LocalDateTime expectedCreationDate = DateUtils.nowUTC();
        final LocalDateTime lowerExpectedCreationDate = expectedCreationDate.minusMinutes(1);
        final LocalDateTime upperExpectedCreationDate = expectedCreationDate.plusMinutes(1);
        final LocalDateTime actualCreationDate = disk.getCreatedDate();
        assertTrue(actualCreationDate.isAfter(lowerExpectedCreationDate)
                && actualCreationDate.isBefore(upperExpectedCreationDate));
    }
    
    @Test
    public void insertShouldSaveAllDisks() {
        final List<NodeDisk> disks = insert(NODE_ID, diskRequestOf(SIZE), diskRequestOf(SIZE));
        
        assertThat(disks.size(), is(2));
    }
    
    @Test
    public void insertShouldSaveAllDisksWithSameCreationDate() {
        final List<NodeDisk> disks = insert(NODE_ID, diskRequestOf(SIZE), diskRequestOf(SIZE));
        
        final Set<LocalDateTime> creationDates = disks.stream()
                .map(NodeDisk::getCreatedDate)
                .collect(Collectors.toSet());
        assertThat(creationDates.size(), is(1));
    }
    
    @Test
    public void insertShouldAllowToRegisterDisksViaSubsequentCalls() {
        insert(NODE_ID, diskRequestOf(SIZE), diskRequestOf(SIZE));
        insert(NODE_ID, diskRequestOf(SIZE));
        
        final List<NodeDisk> disks = dao.loadByNodeId(NODE_ID);
        assertThat(disks.size(), is(3));
    }

    @Test
    public void loadByNodeIdShouldReturnSingleNodeDisks() {
        insert(NODE_ID, diskRequestOf(SIZE), diskRequestOf(SIZE));
        insert(ANOTHER_NODE_ID, diskRequestOf(SIZE), diskRequestOf(SIZE), diskRequestOf(SIZE));

        final List<NodeDisk> disks = dao.loadByNodeId(ANOTHER_NODE_ID);
        
        assertThat(disks.size(), is(3));
    }

    private List<NodeDisk> insert(final String nodeId, final DiskRegistrationRequest... requests) {
        return dao.insert(nodeId, Arrays.asList(requests));
    }

    private DiskRegistrationRequest diskRequestOf(final Long size) {
        return new DiskRegistrationRequest(size);
    }
}
