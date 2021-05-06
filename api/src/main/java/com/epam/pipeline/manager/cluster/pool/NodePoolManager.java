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
import com.epam.pipeline.entity.docker.ToolVersion;
import com.epam.pipeline.entity.docker.ToolVersionAttributes;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import com.epam.pipeline.mapper.cluster.pool.NodePoolMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

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
    private final MessageHelper messageHelper;
    private final ToolManager toolManager;

    public List<NodePool> getActivePools() {
        final LocalDateTime timestamp = DateUtils.nowUTC();
        return ListUtils.emptyIfNull(poolDao.loadAll())
                .stream()
                .filter(pool -> pool.isActive(timestamp))
                .collect(Collectors.toList());
    }

    @Transactional
    public NodePool createOrUpdate(final NodePoolVO vo) {
        verifyVo(vo);
        return vo.getId() == null ? create(vo) : update(vo);
    }

    public List<NodePool> loadAll() {
        return poolDao.loadAll();
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

    private void verifyVo(final NodePoolVO vo) {
        final boolean poolContainsNoWindowsImages = SetUtils.emptyIfNull(vo.getDockerImages()).stream()
            .map(toolManager::loadByNameOrId)
            .map(tool -> toolManager.loadToolVersionAttributes(tool.getId(), ToolUtils.getImageTag(tool.getImage())))
            .map(ToolVersionAttributes::getAttributes)
            .map(ToolVersion::getPlatform)
            .noneMatch("windows"::equalsIgnoreCase);
        Assert.isTrue(poolContainsNoWindowsImages,
                      messageHelper.getMessage(MessageConstants.ERROR_NODE_POOL_WIN_TOOLS_ARE_NOT_ALLOWED));
    }
}
