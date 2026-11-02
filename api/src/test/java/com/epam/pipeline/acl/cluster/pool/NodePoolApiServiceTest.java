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

import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

public class NodePoolApiServiceTest extends AbstractAclTest {

    private static final String NODE_POOL_MANAGER_ROLE = "NODE_POOL_MANAGER";

    private final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();

    @Autowired
    private NodePoolApiService nodePoolApiService;

    @Autowired
    private NodePoolManager mockNodePoolManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateAnyPoolForAnAdministrator() {
        doReturn(pool).when(mockNodePoolManager).create(any(NodePoolVO.class));

        assertThat(nodePoolApiService.create(new NodePoolVO())).isEqualTo(pool);
    }

    @Test
    @WithMockUser(roles = RUN_ADMIN_ROLE)
    public void shouldCreateAnyPoolForARunAdministrator() {
        doReturn(pool).when(mockNodePoolManager).create(any(NodePoolVO.class));

        assertThat(nodePoolApiService.create(new NodePoolVO())).isEqualTo(pool);
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldCreateAReservationBackedPoolForANodePoolManager() {
        doReturn(pool).when(mockNodePoolManager).create(any(NodePoolVO.class));

        assertThat(nodePoolApiService.create(reservationPoolVO(CapacityReservationType.FUTURE_DATED)))
                .isEqualTo(pool);
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldCreateACapacityBlockBackedPoolForANodePoolManager() {
        doReturn(pool).when(mockNodePoolManager).create(any(NodePoolVO.class));

        assertThat(nodePoolApiService.create(reservationPoolVO(CapacityReservationType.CAPACITY_BLOCK)))
                .isEqualTo(pool);
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldDenyANodePoolManagerAPoolThatNoReservationBacks() {
        assertThrows(AccessDeniedException.class, () -> nodePoolApiService.create(new NodePoolVO()));
    }

    @Test
    @WithMockUser
    public void shouldDenyAnOrdinaryUserAReservationBackedPool() {
        assertThrows(AccessDeniedException.class,
            () -> nodePoolApiService.create(reservationPoolVO(CapacityReservationType.FUTURE_DATED)));
    }

    private static NodePoolVO reservationPoolVO(final CapacityReservationType type) {
        final NodePoolVO vo = new NodePoolVO();
        final CapacityReservationRequest request = new CapacityReservationRequest();
        request.setReservationType(type);
        vo.setCapacityReservationRequest(request);
        return vo;
    }
}
