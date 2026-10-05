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

package com.epam.pipeline.dao.cluster.pool;

import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodePoolLaunchConfig;
import com.epam.pipeline.entity.cluster.pool.NodePoolType;
import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.entity.cluster.pool.PoolLabel;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodeScheduleCreatorUtils;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class NodePoolDaoTest extends AbstractJdbcTest {

    private static final String NEW_VALUE = "new";
    private static final String OWNER = "pool-owner";
    private static final LocalDateTime START_DATE = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final LocalDateTime END_DATE = LocalDateTime.of(2026, 10, 3, 12, 0);
    private static final int CPU_RESERVED = 4;
    private static final String RAM_RESERVED = "2GiB";
    private static final int RESERVED_COUNT = 3;
    private static final String RESERVATION_LABEL = "cp-capacity-reservation";

    @Autowired
    private NodePoolDao poolDao;

    @Autowired
    private NodeScheduleDao scheduleDao;

    @Test
    public void shouldCreateAndLoadNewPoolWithoutSchedule() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePool created = poolDao.create(pool);
        assertThat(created.getId()).isNotNull();
        findAndAssertPool(created);
    }

    @Test
    public void shouldCreateAndLoadNewPoolWithFilter() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setFilter(NodePoolCreatorUtils.getAllFilters());
        final NodePool created = poolDao.create(pool);
        assertThat(created.getId()).isNotNull();
        findAndAssertPool(created);
    }

    @Test
    public void shouldCreateAndLoadNewPoolWithSchedule() {
        final NodePool created = createPoolWithSchedule();
        assertThat(created.getId()).isNotNull();
        findAndAssertPool(created);
    }

    /**
     * The locking load joins the same schedule tables, outer, as the plain one - and the pool it returns must be
     * the same, schedule included, or a write through it would drop what it did not read.
     */
    @Test
    public void shouldLoadAPoolForUpdateExactlyAsItLoadsIt() {
        final NodePool created = createPoolWithSchedule();

        final Optional<NodePool> locked = poolDao.findForUpdate(created.getId());

        assertThat(locked).isEqualTo(poolDao.find(created.getId()));
        assertThat(locked.orElseThrow(AssertionError::new).getSchedule()).isNotNull();
    }

    @Test
    public void shouldUpdatePool() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePool created = poolDao.create(pool);
        created.setCount(2);
        created.setName(NEW_VALUE);
        poolDao.update(created);
        findAndAssertPool(created);
    }

    @Test
    public void shouldDeletePool() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        final NodePool created = poolDao.create(pool);
        poolDao.delete(created.getId());
        assertThat(poolDao.find(created.getId()).isPresent()).isFalse();
        assertThat(poolDao.loadAll()).isEmpty();
    }

    @Test
    public void shouldLoadAllPools() {
        final NodePool first = createPoolWithSchedule();
        final NodePool second = createPoolWithSchedule();
        final List<NodePool> pools = poolDao.loadAll();
        assertThat(pools).containsExactlyInAnyOrder(first, second);
    }

    /**
     * The columns added for capacity reservation. Equality alone cannot carry this: {@code owner} is not part
     * of {@code NodePool}'s equality contract (it lives on {@code AbstractSecuredEntity}, which compares only
     * ACL class and id), so each is asserted by value.
     */
    @Test
    public void shouldRoundTripReservationColumns() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setOwner(OWNER);
        pool.setPoolType(NodePoolType.SHARABLE_NODE);
        pool.setCapacityReservation(true);
        pool.setStartDate(START_DATE);
        pool.setEndDate(END_DATE);
        final NodePool created = poolDao.create(pool);

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);

        assertThat(loaded.getOwner()).isEqualTo(OWNER);
        assertThat(loaded.getPoolType()).isEqualTo(NodePoolType.SHARABLE_NODE);
        assertThat(loaded.isCapacityReservation()).isTrue();
        assertThat(loaded.getStartDate()).isEqualTo(START_DATE);
        assertThat(loaded.getEndDate()).isEqualTo(END_DATE);
    }

    /**
     * The ordinary update leaves the reservation window out on purpose, so this statement is the only way the
     * window reaches the database - without it a reservation-backed pool would never stop being schedulable.
     */
    @Test
    public void shouldWriteTheColumnsTheReservationLifecycleOwnsAndNothingElse() {
        final NodePool created = poolDao.create(NodePoolCreatorUtils.getPoolWithoutSchedule());
        final String originalName = created.getName();
        created.setName("not written by this statement");
        created.setCount(RESERVED_COUNT);
        created.setStartDate(START_DATE);
        created.setEndDate(END_DATE);
        created.setKubeLabels(Collections.singletonMap(RESERVATION_LABEL, new PoolLabel("7", false)));
        created.setAmiConfiguration(amiConfiguration());

        poolDao.updateReservationState(created);

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getCount()).isEqualTo(RESERVED_COUNT);
        assertThat(loaded.getStartDate()).isEqualTo(START_DATE);
        assertThat(loaded.getEndDate()).isEqualTo(END_DATE);
        assertThat(MapUtils.emptyIfNull(loaded.getKubeLabels())).doesNotContainKey(RESERVATION_LABEL);
        assertThat(loaded.getAmiConfiguration()).isEqualTo(amiConfiguration());
        assertThat(loaded.getName()).isEqualTo(originalName);
    }

    /**
     * Every field the launch scripts read survives the round trip, in the names they read it by.
     */
    @Test
    public void shouldRoundTripTheAmiConfiguration() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setAmiConfiguration(amiConfiguration());

        final NodePool created = poolDao.create(pool);

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getAmiConfiguration())
                .isEqualTo(amiConfiguration());
    }

    /**
     * An update writes the configuration it carries - the manager resolves it, the pool's current one included,
     * before it updates.
     */
    @Test
    public void shouldWriteTheAmiConfigurationThroughAnUpdate() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setAmiConfiguration(amiConfiguration());
        final NodePool created = poolDao.create(pool);

        final AMIConfiguration edited = amiConfiguration();
        edited.setSubnet("subnet-edited");
        created.setAmiConfiguration(edited);
        created.setName(NEW_VALUE);
        poolDao.update(created);
        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getAmiConfiguration())
                .isEqualTo(edited);
    }

    @Test
    public void shouldRoundTripLaunchConfigAsJson() {
        final NodePoolLaunchConfig config = new NodePoolLaunchConfig();
        config.setCpuRequestsEnabled(true);
        config.setGpuRequestsEnabled(true);
        config.setCpuRequestsReserved(CPU_RESERVED);
        config.setRamRequestsReserved(RAM_RESERVED);
        config.setAdditionalParameters(Collections.singletonMap("CP_CAP_SCHEDULING", "CAPACITY_BLOCK"));
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setLaunchConfig(config);
        final NodePool created = poolDao.create(pool);

        final NodePoolLaunchConfig loaded = poolDao.find(created.getId())
                .orElseThrow(AssertionError::new)
                .getLaunchConfig();

        assertThat(loaded).isNotNull();
        assertThat(loaded.isCpuRequestsEnabled()).isTrue();
        assertThat(loaded.isRamRequestsEnabled()).isFalse();
        assertThat(loaded.getCpuRequestsReserved()).isEqualTo(CPU_RESERVED);
        assertThat(loaded.getRamRequestsReserved()).isEqualTo(RAM_RESERVED);
        assertThat(loaded.getAdditionalParameters()).containsEntry("CP_CAP_SCHEDULING", "CAPACITY_BLOCK");
    }

    @Test
    public void shouldDefaultPoolTypeToStandardAndReservationToFalse() {
        final NodePool created = poolDao.create(NodePoolCreatorUtils.getPoolWithoutSchedule());

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);

        assertThat(loaded.getPoolType()).isEqualTo(NodePoolType.STANDARD);
        assertThat(loaded.isCapacityReservation()).isFalse();
    }

    /**
     * {@code capacity_reservation} is write-once and {@code owner} is only writable through
     * {@link NodePoolDao#updateOwner}, so an ordinary update must leave both untouched - a pool cannot gain
     * or lose a reservation, or change hands, as a side effect of an edit.
     */
    @Test
    public void shouldNotChangeReservationFlagOrOwnerOnUpdate() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setOwner(OWNER);
        pool.setCapacityReservation(true);
        final NodePool created = poolDao.create(pool);

        created.setOwner(NEW_VALUE);
        created.setCapacityReservation(false);
        created.setCount(2);
        poolDao.update(created);

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getCount()).isEqualTo(2);
        assertThat(loaded.isCapacityReservation()).isTrue();
        assertThat(loaded.getOwner()).isEqualTo(OWNER);
    }

    /**
     * The reservation lifecycle owns these, and the only caller of {@code update} builds its entity from a VO
     * that carries none of them - so an ordinary edit must not null a pool's reservation window.
     */
    @Test
    public void shouldNotClearLifecycleColumnsOnUpdate() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setPoolType(NodePoolType.SHARABLE_NODE);
        pool.setStartDate(START_DATE);
        pool.setEndDate(END_DATE);
        final NodePool created = poolDao.create(pool);

        final NodePool edit = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        edit.setPoolType(null);
        edit.setStartDate(null);
        edit.setEndDate(null);
        edit.setName(NEW_VALUE);
        poolDao.update(edit);

        final NodePool loaded = poolDao.find(created.getId()).orElseThrow(AssertionError::new);
        assertThat(loaded.getName()).isEqualTo(NEW_VALUE);
        assertThat(loaded.getPoolType()).isEqualTo(NodePoolType.SHARABLE_NODE);
        assertThat(loaded.getStartDate()).isEqualTo(START_DATE);
        assertThat(loaded.getEndDate()).isEqualTo(END_DATE);
    }

    @Test
    public void shouldUpdateOwnerThroughItsOwnStatement() {
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setOwner(OWNER);
        final NodePool created = poolDao.create(pool);

        created.setOwner(NEW_VALUE);
        poolDao.updateOwner(created);

        assertThat(poolDao.find(created.getId()).orElseThrow(AssertionError::new).getOwner())
                .isEqualTo(NEW_VALUE);
    }

    private static AMIConfiguration amiConfiguration() {
        final AMIConfiguration configuration = new AMIConfiguration();
        configuration.setAmi("ami-0123456789abcdef0");
        configuration.setInitScript("/opt/api/scripts/init_multicloud.sh");
        configuration.setFsType("ext4");
        configuration.setEmbeddedScripts(Collections.singletonMap("fsautoscale", "/opt/api/scripts/fsautoscale.sh"));
        configuration.setAdditionalSpec(Collections.singletonMap("IamInstanceProfile",
                Collections.singletonMap("Arn", "arn:aws:iam::123456789012:instance-profile/node")));
        configuration.setAvailabilityZone("eu-central-1b");
        configuration.setSubnet("subnet-in-zone");
        return configuration;
    }

    private void findAndAssertPool(final NodePool expected) {
        final Optional<NodePool> loaded = poolDao.find(expected.getId());
        assertThat(loaded.isPresent()).isTrue();
        final NodePool actual = loaded.get();
        assertThat(actual).isEqualTo(expected);
    }

    private NodePool createPoolWithSchedule() {
        final NodeSchedule schedule = NodeScheduleCreatorUtils.getWorkDaySchedule();
        schedule.getScheduleEntries()
                .add(NodeScheduleCreatorUtils.createScheduleEntry(
                        DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.TEN_AM,
                        DayOfWeek.SUNDAY, NodeScheduleCreatorUtils.SIX_PM));
        final NodeSchedule dbSchedule = scheduleDao.create(schedule);
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule();
        pool.setSchedule(dbSchedule);
        return poolDao.create(pool);
    }
}
