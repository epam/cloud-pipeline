/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.cluster.pool.NodeScheduleDao.NodeScheduleParameters;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.NodeSchedule;
import com.epam.pipeline.entity.cluster.pool.ScheduleEntry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class NodePoolDao extends NamedParameterJdbcDaoSupport {

    private final String insertNodePoolQuery;
    private final String updateNodePoolQuery;
    private final String deleteNodePoolQuery;
    private final String loadAllNodePoolsQuery;
    private final String loadNodePoolByIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public NodePool create(final NodePool pool) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(insertNodePoolQuery,
                        NodePoolParameters.getParameters(pool),
                        keyHolder,
                        new String[]{"id"});
        pool.setId(keyHolder.getKey().longValue());
        return pool;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public NodePool update(final NodePool pool) {
        getNamedParameterJdbcTemplate()
                .update(updateNodePoolQuery, NodePoolParameters.getParameters(pool));
        return pool;
    }

    public List<NodePool> loadAll() {
        return getJdbcTemplate()
                .query(loadAllNodePoolsQuery, NodePoolParameters.getExtractor());
    }

    public Optional<NodePool> find(final Long poolId) {
        final List<NodePool> result = getJdbcTemplate()
                .query(loadNodePoolByIdQuery, NodePoolParameters.getExtractor(), poolId);
        return ListUtils.emptyIfNull(result).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final Long poolId) {
        getJdbcTemplate().update(deleteNodePoolQuery, poolId);
    }


    enum NodePoolParameters {
        POOL_ID,
        POOL_NAME,
        POOL_CREATED_DATE,
        POOL_REGION_ID,
        POOL_INSTANCE_TYPE,
        POOL_INSTANCE_DISK,
        POOL_PRICE_TYPE,
        POOL_DOCKER_IMAGE,
        POOL_INSTANCE_IMAGE,
        POOL_INSTANCE_COUNT;

        public static final String DOCKER_IMAGE_DELIMITER = ",";

        static MapSqlParameterSource getParameters(final NodePool pool) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(POOL_ID.name(), pool.getId());
            params.addValue(POOL_NAME.name(), pool.getName());
            params.addValue(POOL_CREATED_DATE.name(), pool.getCreated());
            params.addValue(POOL_REGION_ID.name(), pool.getRegionId());
            params.addValue(POOL_INSTANCE_TYPE.name(), pool.getInstanceType());
            params.addValue(POOL_INSTANCE_DISK.name(), pool.getInstanceDisk());
            params.addValue(POOL_PRICE_TYPE.name(), pool.getPriceType().name());
            params.addValue(POOL_DOCKER_IMAGE.name(), String.join(DOCKER_IMAGE_DELIMITER,
                    SetUtils.emptyIfNull(pool.getDockerImages())));
            params.addValue(POOL_INSTANCE_IMAGE.name(), pool.getInstanceImage());
            params.addValue(POOL_INSTANCE_COUNT.name(), pool.getCount());
            params.addValue(NodeScheduleParameters.SCHEDULE_ID.name(),
                    Optional.ofNullable(pool.getSchedule())
                            .map(NodeSchedule::getId)
                            .orElse(null));
            return params;
        }

        static ResultSetExtractor<List<NodePool>> getExtractor() {
            return (rs) -> {
                final Map<Long, NodePool> pools = new HashMap<>();
                final Map<Long, NodeSchedule> schedules = new HashMap<>();
                while (rs.next()) {
                    final Long poolId = rs.getLong(POOL_ID.name());
                    if (!pools.containsKey(poolId)) {
                        pools.put(poolId, parseNodePool(rs));
                    }
                    final NodePool pool = pools.get(poolId);
                    final Long scheduleId = rs.getLong(NodeScheduleParameters.SCHEDULE_ID.name());
                    if (scheduleId > 0 && !schedules.containsKey(scheduleId)) {
                        final NodeSchedule schedule = NodeScheduleParameters.parseNodeSchedule(rs);
                        schedules.put(scheduleId, schedule);
                    }
                    final NodeSchedule schedule = schedules.get(scheduleId);
                    if (schedule != null) {
                        pool.setSchedule(schedule);
                        final ScheduleEntry entry = NodeScheduleParameters.parseScheduleEntry(rs);
                        schedule.getScheduleEntries().add(entry);
                    }
                }
                return new ArrayList<>(pools.values());
            };
        }

        private static NodePool parseNodePool(final ResultSet rs) throws SQLException {
            final NodePool pool = new NodePool();
            pool.setId(rs.getLong(POOL_ID.name()));
            pool.setName(rs.getString(POOL_NAME.name()));
            pool.setCreated(rs.getTimestamp(POOL_CREATED_DATE.name()).toLocalDateTime());
            pool.setRegionId(rs.getLong(POOL_REGION_ID.name()));
            pool.setInstanceType(rs.getString(POOL_INSTANCE_TYPE.name()));
            pool.setInstanceDisk(rs.getInt(POOL_INSTANCE_DISK.name()));
            pool.setPriceType(PriceType.valueOf(rs.getString(POOL_PRICE_TYPE.name())));
            pool.setDockerImages(parseDockerImages(rs.getString(POOL_DOCKER_IMAGE.name())));
            pool.setInstanceImage(rs.getString(POOL_INSTANCE_IMAGE.name()));
            pool.setCount(rs.getInt(POOL_INSTANCE_COUNT.name()));
            return pool;
        }

        private static Set<String> parseDockerImages(final String line) {
            if (StringUtils.isBlank(line)) {
                return Collections.emptySet();
            }
            return new HashSet<>(Arrays.asList(line.split(DOCKER_IMAGE_DELIMITER)));
        }
    }
}
