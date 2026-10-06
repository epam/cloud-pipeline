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

package com.epam.pipeline.manager.cluster.pool;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NodePoolSecuredEntityManagerTest {

    private static final Long POOL_ID = 1L;
    private static final String NEW_OWNER = "new-owner";

    @Mock
    private NodePoolDao poolDao;

    @Mock
    private MessageHelper messageHelper;

    @InjectMocks
    private NodePoolManager nodePoolManager;

    private NodePool pool;

    @BeforeEach
    public void setUp() {
        pool = NodePoolCreatorUtils.getPoolWithoutSchedule(POOL_ID);
        when(messageHelper.getMessage(any(), any())).thenReturn("not found");
    }

    @Test
    public void shouldSupportNodePoolAclClass() {
        assertThat(nodePoolManager.getSupportedClass()).isEqualTo(AclClass.NODE_POOL);
    }

    @Test
    public void shouldChangeOwner() {
        when(poolDao.findForUpdate(POOL_ID)).thenReturn(Optional.of(pool));
        when(poolDao.updateOwner(any(NodePool.class))).thenAnswer(invocation -> invocation.getArguments()[0]);

        final AbstractSecuredEntity updated = nodePoolManager.changeOwner(POOL_ID, NEW_OWNER);

        assertThat(updated.getOwner()).isEqualTo(NEW_OWNER);
    }

    @Test
    public void shouldFailToChangeOwnerOfMissingPool() {
        when(poolDao.findForUpdate(POOL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nodePoolManager.changeOwner(POOL_ID, NEW_OWNER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldLoadByNumericIdentifier() {
        when(poolDao.find(POOL_ID)).thenReturn(Optional.of(pool));

        assertThat(nodePoolManager.loadByNameOrId(String.valueOf(POOL_ID))).isEqualTo(pool);
    }

    @Test
    public void shouldLoadByNameIdentifier() {
        when(poolDao.loadAll()).thenReturn(Collections.singletonList(pool));

        assertThat(nodePoolManager.loadByNameOrId(NodePoolCreatorUtils.POOL_NAME)).isEqualTo(pool);
    }

    @Test
    public void shouldFailToLoadByUnknownName() {
        when(poolDao.loadAll()).thenReturn(Collections.singletonList(pool));

        assertThatThrownBy(() -> nodePoolManager.loadByNameOrId("no-such-pool"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotSupportHierarchyOperations() {
        assertThatThrownBy(() -> nodePoolManager.loadTotalCount())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> nodePoolManager.loadAllWithParents(1, 10))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> nodePoolManager.loadWithParents(POOL_ID))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void shouldExposeNodePoolAsRootLevelSecuredEntity() {
        assertThat(pool.getAclClass()).isEqualTo(AclClass.NODE_POOL);
        assertThat(pool.getParent()).isNull();
    }

    @Test
    public void shouldMatchPoolByNameAmongSeveral() {
        final NodePool other = NodePoolCreatorUtils.getPoolWithoutSchedule(2L);
        other.setName("another-pool");
        when(poolDao.loadAll()).thenReturn(Arrays.asList(other, pool));

        assertThat(nodePoolManager.loadByNameOrId(NodePoolCreatorUtils.POOL_NAME)).isEqualTo(pool);
    }
}
