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

import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.pool.NodePoolDao;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * An existing pool's {@code poolType} can be switched between {@code STANDARD} and {@code SHAREABLE_NODE} by an
 * update request, and its {@code launchConfig} follows the requested type rather than the pool's own prior one.
 */
@Transactional
public class NodePoolTypeReconfigurationTest extends AbstractManagerTest {

    private static final long REGION_ID = 1L;
    private static final int INSTANCE_DISK = 50;
    private static final String INSTANCE_TYPE = "m5.large";
    private static final String OWNER = "pool-owner";
    private static final String ADMIN = "ADMIN";

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private NodePoolDao poolDao;

    @MockBean
    private CloudRegionManager regionManager;

    @MockBean
    private InstanceOfferManager instanceOfferManager;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        when(regionManager.load(any())).thenReturn(region);
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldBuildALaunchConfigWhenSwitchingToShareable() {
        final NodePool created = poolManager.create(pool(NodePoolType.STANDARD));

        poolManager.update(created.getId(), pool(NodePoolType.SHAREABLE_NODE));

        final NodePool loaded = loaded(created);
        assertThat(loaded.getPoolType()).isEqualTo(NodePoolType.SHAREABLE_NODE);
        assertThat(loaded.getLaunchConfig()).isNotNull();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldDropTheLaunchConfigWhenSwitchingToStandard() {
        final NodePool created = poolManager.create(pool(NodePoolType.SHAREABLE_NODE));
        assertThat(loaded(created).getLaunchConfig()).isNotNull();

        poolManager.update(created.getId(), pool(NodePoolType.STANDARD));

        final NodePool loaded = loaded(created);
        assertThat(loaded.getPoolType()).isEqualTo(NodePoolType.STANDARD);
        assertThat(loaded.getLaunchConfig()).isNull();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldKeepTheLaunchConfigWhenStayingShareableWithNoneRequested() {
        final NodePool created = poolManager.create(pool(NodePoolType.SHAREABLE_NODE));
        final NodePoolLaunchConfig original = loaded(created).getLaunchConfig();
        final NodePoolVO edit = pool(NodePoolType.SHAREABLE_NODE);
        edit.setCount(2);

        poolManager.update(created.getId(), edit);

        assertThat(loaded(created).getLaunchConfig()).isEqualTo(original);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldReplaceTheLaunchConfigWhenStayingShareableWithANewOneRequested() {
        final NodePool created = poolManager.create(pool(NodePoolType.SHAREABLE_NODE));
        final NodePoolVO edit = pool(NodePoolType.SHAREABLE_NODE);
        final NodePoolLaunchConfig replacement = new NodePoolLaunchConfig();
        replacement.setCpuRequestsEnabled(false);
        edit.setLaunchConfig(replacement);

        poolManager.update(created.getId(), edit);

        assertThat(loaded(created).getLaunchConfig().isCpuRequestsEnabled()).isFalse();
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseAnUpdateWithNoPoolType() {
        final NodePool created = poolManager.create(pool(NodePoolType.STANDARD));
        final NodePoolVO edit = pool(NodePoolType.STANDARD);
        edit.setPoolType(null);

        assertThatThrownBy(() -> poolManager.update(created.getId(), edit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = OWNER, roles = ADMIN)
    public void shouldRefuseACreateWithNoPoolType() {
        final NodePoolVO vo = pool(NodePoolType.STANDARD);
        vo.setPoolType(null);

        assertThatThrownBy(() -> poolManager.create(vo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NodePool loaded(final NodePool pool) {
        return poolDao.find(pool.getId()).orElseThrow(AssertionError::new);
    }

    private static NodePoolVO pool(final NodePoolType type) {
        final NodePoolVO vo = new NodePoolVO();
        vo.setName("reconfigurable pool");
        vo.setRegionId(REGION_ID);
        vo.setInstanceType(INSTANCE_TYPE);
        vo.setInstanceDisk(INSTANCE_DISK);
        vo.setPriceType(PriceType.ON_DEMAND);
        vo.setCount(1);
        vo.setPoolType(type);
        return vo;
    }
}
