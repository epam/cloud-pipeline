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

import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

/**
 * A pool's launch configuration names files the launch reads on the API host and hands to the node, and goes to the
 * cloud's launch request as it is - so replacing it is for administrators only, whoever else may manage the pool.
 */
public class NodePoolApiServiceTest extends AbstractAclTest {

    private static final String NODE_POOL_MANAGER_ROLE = "NODE_POOL_MANAGER";

    private final AMIConfiguration configuration = new AMIConfiguration();
    private final NodePool pool = new NodePool();

    @Autowired
    private NodePoolApiService poolApiService;

    @Autowired
    private NodePoolManager mockPoolManager;

    @Before
    public void setUp() {
        pool.setId(ID);
        pool.setOwner(SIMPLE_USER);
        doReturn(pool).when(mockPoolManager).updateAmiConfiguration(ID, configuration);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLetAnAdministratorReplaceTheLaunchConfiguration() {
        assertThat(poolApiService.updateAmiConfiguration(ID, configuration)).isEqualTo(pool);
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldRefuseAPoolManager() {
        assertThatThrownBy(() -> poolApiService.updateAmiConfiguration(ID, configuration))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Owning, or being allowed to edit, a pool is not a grant over how its nodes launch.
     */
    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRefuseTheOwnerOfThePool() {
        initAclEntity(pool, AclPermission.OWNER);

        assertThatThrownBy(() -> poolApiService.updateAmiConfiguration(ID, configuration))
                .isInstanceOf(AccessDeniedException.class);
    }
}
