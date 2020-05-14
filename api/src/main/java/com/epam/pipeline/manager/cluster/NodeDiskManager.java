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
        requests.forEach(request -> Assert.notNull(nodeId,
                messageHelper.getMessage(MessageConstants.ERROR_DISK_NODE_MISSING)));
        requests.forEach(request -> Assert.notNull(request.getSize(),
                messageHelper.getMessage(MessageConstants.ERROR_DISK_SIZE_MISSING)));
        requests.forEach(request -> Assert.isTrue(request.getSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_DISK_SIZE_INVALID, request.getSize())));
        return nodeDiskDao.insert(nodeId, requests);
    }

    public List<NodeDisk> loadByNodeId(final String nodeId) {
        Assert.notNull(nodeId, messageHelper.getMessage(MessageConstants.ERROR_DISK_NODE_MISSING));
        return nodeDiskDao.loadByNodeId(nodeId);
    }
}
