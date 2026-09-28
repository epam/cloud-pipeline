/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.acl.cluster.pool;

import com.epam.pipeline.controller.vo.cluster.pool.NodePoolFilterVO;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolUsage;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolUsageService;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Authorization for node pools.
 */
@Service
@RequiredArgsConstructor
public class NodePoolApiService {

    private final NodePoolManager nodeManager;
    private final NodePoolUsageService nodePoolUsageService;

    @PostFilter(AclExpressions.NODE_POOL_READ_FILTER)
    public List<NodePool> loadAll(final boolean loadStatus) {
        return nodeManager.loadAll(loadStatus);
    }

    /**
     * Filtered the same way {@code loadAll} is: a narrower query must not be a way to see more.
     */
    @PostFilter(AclExpressions.NODE_POOL_READ_FILTER)
    public List<NodePool> filter(final NodePoolFilterVO filter) {
        return nodeManager.filter(filter);
    }

    @PostAuthorize(AclExpressions.NODE_POOL_RETURN_OBJECT_READ)
    public NodePool load(final Long poolId) {
        return nodeManager.load(poolId);
    }

    /**
     * Creating a pool with a capacity reservation request spends money, but the role is only permission to
     * <em>ask</em>: whether the request is granted is decided separately by the approval policy.
     */
    @PreAuthorize(AclExpressions.NODE_POOL_CREATE)
    public NodePool create(final NodePoolVO vo) {
        return nodeManager.create(vo);
    }

    @PreAuthorize(AclExpressions.NODE_POOL_ID_WRITE)
    public NodePool update(final Long id, final NodePoolVO vo) {
        return nodeManager.update(id, vo);
    }

    /**
     * Requires {@code OWNER} rather than {@code WRITE}: deleting a reservation-backed pool is financially
     * consequential, not merely an edit.
     */
    @PreAuthorize(AclExpressions.NODE_POOL_ID_OWNER)
    public NodePool delete(final Long id) {
        return nodeManager.delete(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY + AclExpressions.OR + AclExpressions.RUN_ADMIN_ONLY)
    public List<NodePoolUsage> saveUsage(final List<NodePoolUsage> records) {
        return nodePoolUsageService.save(records);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY + AclExpressions.OR + AclExpressions.RUN_ADMIN_ONLY)
    public Boolean deleteUsage(final LocalDate date) {
        return nodePoolUsageService.deleteExpired(date);
    }
}
