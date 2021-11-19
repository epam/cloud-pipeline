package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.NodeDiskDao;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.cluster.DiskRegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeDiskManager {
    
    private final NodeDiskDao nodeDiskDao;
    private final MessageHelper messageHelper;

    @Transactional
    public NodeDisk register(final String nodeId, final DiskRegistrationRequest request) {
        return register(nodeId, Collections.singletonList(request)).get(0);
    }

    @Transactional
    public List<NodeDisk> register(final String nodeId, final List<DiskRegistrationRequest> requests) {
        validateNodeId(nodeId);
        validateRequests(requests);
        return nodeDiskDao.insert(nodeId, requests);
    }

    @Transactional
    public List<NodeDisk> register(final String nodeId, final LocalDateTime creationDate,
                                   final List<DiskRegistrationRequest> requests) {
        validateNodeId(nodeId);
        validateCreationDate(creationDate);
        validateRequests(requests);
        return nodeDiskDao.insert(nodeId, creationDate, requests);
    }

    public List<NodeDisk> loadByNodeId(final String nodeId) {
        validateNodeId(nodeId);
        return nodeDiskDao.loadByNodeId(nodeId);
    }

    private void validateNodeId(final String nodeId) {
        Assert.notNull(nodeId, messageHelper.getMessage(MessageConstants.ERROR_DISK_NODE_MISSING));
    }

    private void validateCreationDate(final LocalDateTime date) {
        Assert.notNull(date, messageHelper.getMessage(MessageConstants.ERROR_DISK_DATE_MISSING));
    }

    private void validateRequests(final List<DiskRegistrationRequest> requests) {
        for (final DiskRegistrationRequest request : requests) {
            Assert.notNull(request.getSize(), messageHelper.getMessage(MessageConstants.ERROR_DISK_SIZE_MISSING));
            Assert.isTrue(request.getSize() > 0, messageHelper.getMessage(
                    MessageConstants.ERROR_DISK_SIZE_INVALID, request.getSize()));
        }
    }
}
