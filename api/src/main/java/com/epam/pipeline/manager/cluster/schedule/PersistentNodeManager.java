/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.schedule;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.schedule.PersistentNodeVO;
import com.epam.pipeline.dao.cluster.schedule.PersistentNodeDao;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.mapper.cluster.schedule.PersistentNodeMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersistentNodeManager {

    private final PersistentNodeDao nodeDao;
    private final NodeScheduleManager scheduleManager;
    private final CloudRegionManager regionManager;
    private final PersistentNodeMapper nodeMapper;
    private final InstanceOfferManager instanceOfferManager;
    private final ToolManager toolManager;
    private final MessageHelper messageHelper;

    public List<PersistentNode> getActiveNodes() {
        return nodeDao.loadAll();
    }

    @Transactional
    public PersistentNode createOrUpdate(final PersistentNodeVO vo) {
        return vo.getId() == null ? create(vo) : update(vo);
    }

    public List<PersistentNode> loadAll() {
        return nodeDao.loadAll();
    }

    public PersistentNode load(final Long nodeId) {
        return nodeDao.find(nodeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_NOD_FOUND, nodeId)));
    }

    @Transactional
    public PersistentNode delete(final Long nodeId) {
        final PersistentNode node = load(nodeId);
        nodeDao.delete(nodeId);
        return node;
    }

    private PersistentNode update(final PersistentNodeVO vo) {
        load(vo.getId());
        validate(vo);
        final PersistentNode updated = nodeDao.update(nodeMapper.toEntity(vo));
        return load(updated.getId());
    }

    private PersistentNode create(final PersistentNodeVO vo) {
        validate(vo);
        final PersistentNode persistentNode = nodeMapper.toEntity(vo);
        persistentNode.setCreated(DateUtils.nowUTC());
        final PersistentNode created = nodeDao.create(persistentNode);
        return load(created.getId());
    }

    private void validate(final PersistentNodeVO vo) {
        Assert.notNull(vo.getRegionId(),
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_MISSING_REGION));
        regionManager.load(vo.getRegionId());

        Assert.notNull(vo.getPriceType(),
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_MISSING_PRICE_TYPE));
        Assert.isTrue(instanceOfferManager.isPriceTypeAllowed(vo.getPriceType().getLiteral(), null),
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_PRICE_TYPE_NOT_ALLOWED,
                        vo.getPriceType()));

        Assert.isTrue(StringUtils.isNotBlank(vo.getInstanceType()),
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_MISSING_INSTANCE_TYPE));
        Assert.isTrue(instanceOfferManager.isToolInstanceAllowed(vo.getInstanceType(), null, vo.getRegionId(),
                PriceType.SPOT.equals(vo.getPriceType())),
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_INSTANCE_TYPE_NOT_ALLOWED,
                        vo.getInstanceType()));

        Assert.isTrue(vo.getInstanceDisk() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_INVALID_DISK_SIZE));

        Assert.isTrue(vo.getCount() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_PERSISTENT_NODE_INVALID_COUNT));

        Optional.ofNullable(vo.getScheduleId())
                .ifPresent(scheduleManager::load);

        Optional.ofNullable(vo.getDockerImage())
                .ifPresent(image -> toolManager.loadByNameOrId(vo.getDockerImage()));
    }
}
