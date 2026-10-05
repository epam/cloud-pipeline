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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * A pool is a secured entity: its ACL identity - what its permissions hang off - comes and goes with the pool, or a
 * deleted pool leaves its permission entries behind. Run in the context that keeps the ACL aspect, which the default
 * test context leaves out.
 */
@Transactional
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class NodePoolAclSyncTest extends AbstractManagerTest {

    private static final long REGION_ID = 1L;
    /** In upper case, as user names are stored as ACL principals. */
    private static final String OWNER = "POOL-OWNER";
    private static final String POOL_NAME = "acl pool";
    private static final String INSTANCE_TYPE = "m5.large";
    private static final int INSTANCE_DISK = 50;
    /**
     * Read from the tables, so what is checked is what was written rather than what the ACL cache holds.
     */
    private static final String IDENTITY_OWNER_QUERY = "SELECT s.sid FROM pipeline.acl_object_identity oi"
            + " JOIN pipeline.acl_class c ON oi.object_id_class = c.id"
            + " LEFT JOIN pipeline.acl_sid s ON oi.owner_sid = s.id"
            + " WHERE c.class = ? AND oi.object_id_identity = ?";

    @Autowired
    private NodePoolManager poolManager;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private CloudRegionManager regionManager;

    @MockBean
    private InstanceOfferManager instanceOfferManager;

    @BeforeEach
    public void setUp() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setRegionCode("eu-central-1");
        when(regionManager.load(any())).thenReturn((AbstractCloudRegion) region);
        when(instanceOfferManager.isPriceTypeAllowed(any())).thenReturn(true);
        when(instanceOfferManager.isToolInstanceAllowed(any(), any(), anyBoolean())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldCreateThePoolsAclIdentityOwnedByItsOwner() {
        final NodePool created = poolManager.create(pool());

        assertThat(identityOwners(created)).containsExactly(OWNER);
    }

    @Test
    @WithMockUser(username = OWNER)
    public void shouldRemoveThePoolsAclIdentityWithThePool() {
        final NodePool created = poolManager.create(pool());

        poolManager.delete(created.getId());

        assertThat(identityOwners(created)).isEmpty();
    }

    /**
     * The autoscaler resizes a pool through an ordinary update, on a scheduler thread with nobody signed in. With the
     * identity already there, the ACL sync after it only reads it.
     */
    @Test
    @WithMockUser(username = OWNER)
    public void shouldUpdateAPoolWithNobodySignedIn() {
        final NodePool created = poolManager.create(pool());
        final NodePoolVO resize = pool();
        resize.setCount(2);
        SecurityContextHolder.clearContext();

        assertThat(poolManager.update(created.getId(), resize).getCount()).isEqualTo(2);
        assertThat(identityOwners(created)).containsExactly(OWNER);
    }

    private List<String> identityOwners(final NodePool pool) {
        return new JdbcTemplate(dataSource).queryForList(IDENTITY_OWNER_QUERY, String.class,
                NodePool.class.getName(), String.valueOf(pool.getId()));
    }

    private static NodePoolVO pool() {
        final NodePoolVO vo = new NodePoolVO();
        vo.setName(POOL_NAME);
        vo.setRegionId(REGION_ID);
        vo.setInstanceType(INSTANCE_TYPE);
        vo.setInstanceDisk(INSTANCE_DISK);
        vo.setPriceType(PriceType.ON_DEMAND);
        vo.setCount(1);
        return vo;
    }
}
