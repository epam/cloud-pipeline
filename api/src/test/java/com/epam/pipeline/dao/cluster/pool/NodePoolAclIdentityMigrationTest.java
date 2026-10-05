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

package com.epam.pipeline.dao.cluster.pool;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The migration giving pools that predate their ACL identity one, owned by the pool's owner. Flyway has already run
 * it on an empty table by the time a test starts, so it is run again here over a pool that has none.
 */
@Transactional
public class NodePoolAclIdentityMigrationTest extends AbstractJdbcTest {

    private static final String MIGRATION =
            "db/migration/v2026.10.01_12.00__issue_4591_node_pool_acl_identities.sql";
    private static final String OWNER = "legacy-pool-owner";
    private static final String INHERITING_QUERY = "SELECT oi.entries_inheriting FROM pipeline.acl_object_identity oi"
            + " JOIN pipeline.acl_class c ON oi.object_id_class = c.id"
            + " WHERE c.class = ? AND oi.object_id_identity = ?";
    private static final String IDENTITY_OWNER_QUERY = "SELECT s.sid FROM pipeline.acl_object_identity oi"
            + " JOIN pipeline.acl_class c ON oi.object_id_class = c.id"
            + " JOIN pipeline.acl_sid s ON oi.owner_sid = s.id"
            + " WHERE c.class = ? AND oi.object_id_identity = ?";

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private DataSource dataSource;

    @Test
    public void shouldGiveAPoolWithoutAnAclIdentityOneOwnedByItsOwner() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setOwner(OWNER);
        final NodePool created = poolDao.create(pool);
        assertThat(identityOwners(created)).isEmpty();

        migrate();

        assertThat(identityOwners(created)).containsExactly(OWNER.toUpperCase());
        // As the ACL service writes one: the pool has no parent, but its entries are inheriting all the same.
        assertThat(new JdbcTemplate(dataSource).queryForList(INHERITING_QUERY, Boolean.class,
                NodePool.class.getName(), String.valueOf(created.getId()))).containsExactly(true);
    }

    @Test
    public void shouldNotDuplicateAnIdentityWhenRunAgain() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setOwner(OWNER);
        final NodePool created = poolDao.create(pool);

        migrate();
        migrate();

        assertThat(identityOwners(created)).hasSize(1);
    }

    private void migrate() {
        new JdbcTemplate(dataSource).execute((Connection connection) -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION));
            return null;
        });
    }

    private List<String> identityOwners(final NodePool pool) {
        return new JdbcTemplate(dataSource).queryForList(IDENTITY_OWNER_QUERY, String.class,
                NodePool.class.getName(), String.valueOf(pool.getId()));
    }
}
