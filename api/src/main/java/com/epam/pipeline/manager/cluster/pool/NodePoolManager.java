/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolInfo;
import com.epam.pipeline.entity.notification.NotificationType;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodePoolManager {

    private final NodePoolDao poolDao;
    private final NodePoolMapper poolMapper;
    private final NodePoolValidator validator;
    private final NotificationManager notificationManager;
    private final MessageHelper messageHelper;
    private final KubernetesPoolService kubernetesPoolService;

    public List<NodePool> getActivePools() {
        final LocalDateTime timestamp = DateUtils.nowUTC();
        return ListUtils.emptyIfNull(poolDao.loadAll())
                .stream()
                .filter(pool -> pool.isActive(timestamp))
                .collect(Collectors.toList());
    }

    @Transactional
    public NodePool createOrUpdate(final NodePoolVO vo) {
        return vo.getId() == null ? create(vo) : update(vo);
    }

    public List<? extends NodePoolInfo> loadAll(final boolean loadStatus) {
        final List<NodePool> allPools = poolDao.loadAll();
        return loadStatus ? kubernetesPoolService.attachUsage(allPools) : allPools;
    }

    public Optional<NodePool> find(final Long poolId) {
        return poolDao.find(poolId);
    }

    public NodePool load(final Long poolId) {
        return poolDao.find(poolId)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_NOT_FOUND, poolId)));
    }

    @Transactional
    public NodePool delete(final Long poolId) {
        final NodePool node = load(poolId);
        poolDao.delete(poolId);
        notificationManager.removeNotificationTimestamps(poolId, NotificationType.FULL_NODE_POOL);
        return node;
    }

    private NodePool update(final NodePoolVO vo) {
        load(vo.getId());
        validator.validate(vo);
        final NodePool updated = poolDao.update(poolMapper.toEntity(vo));
        return load(updated.getId());
    }

    private NodePool create(final NodePoolVO vo) {
        validator.validate(vo);
        final NodePool nodePool = poolMapper.toEntity(vo);
        nodePool.setCreated(DateUtils.nowUTC());
        final NodePool created = poolDao.create(nodePool);
        return load(created.getId());
    }
}
