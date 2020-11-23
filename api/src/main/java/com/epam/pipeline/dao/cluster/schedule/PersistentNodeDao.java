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

package com.epam.pipeline.dao.cluster.schedule;

import com.epam.pipeline.dao.cluster.schedule.NodeScheduleDao.NodeScheduleParameters;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.schedule.NodeSchedule;
import com.epam.pipeline.entity.cluster.schedule.PersistentNode;
import com.epam.pipeline.entity.cluster.schedule.ScheduleEntry;
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
public class PersistentNodeDao extends NamedParameterJdbcDaoSupport {

    private final String insertPersistentNodeQuery;
    private final String updatePersistentNodeQuery;
    private final String deletePersistentNodeQuery;
    private final String loadAllPersistentNodesQuery;
    private final String loadPersistentNodeByIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public PersistentNode create(final PersistentNode node) {
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        getNamedParameterJdbcTemplate()
                .update(insertPersistentNodeQuery,
                        PersistentNodeParameters.getParameters(node),
                        keyHolder,
                        new String[]{"id"});
        node.setId(keyHolder.getKey().longValue());
        return node;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PersistentNode update(final PersistentNode node) {
        getNamedParameterJdbcTemplate()
                .update(updatePersistentNodeQuery, PersistentNodeParameters.getParameters(node));
        return node;
    }

    public List<PersistentNode> loadAll() {
        return getJdbcTemplate()
                .query(loadAllPersistentNodesQuery, PersistentNodeParameters.getExtractor());
    }

    public Optional<PersistentNode> find(final Long nodeId) {
        final List<PersistentNode> result = getJdbcTemplate()
                .query(loadPersistentNodeByIdQuery, PersistentNodeParameters.getExtractor(), nodeId);
        return ListUtils.emptyIfNull(result).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final Long nodeId) {
        getJdbcTemplate().update(deletePersistentNodeQuery, nodeId);
    }


    enum PersistentNodeParameters {
        NODE_ID,
        NODE_NAME,
        NODE_CREATED_DATE,
        NODE_REGION_ID,
        NODE_INSTANCE_TYPE,
        NODE_INSTANCE_DISK,
        NODE_PRICE_TYPE,
        NODE_DOCKER_IMAGE,
        NODE_INSTANCE_COUNT;

        public static final String DOCKER_IMAGE_DELIMITER = ",";

        static MapSqlParameterSource getParameters(final PersistentNode node) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(NODE_ID.name(), node.getId());
            params.addValue(NODE_NAME.name(), node.getName());
            params.addValue(NODE_CREATED_DATE.name(), node.getCreated());
            params.addValue(NODE_REGION_ID.name(), node.getRegionId());
            params.addValue(NODE_INSTANCE_TYPE.name(), node.getInstanceType());
            params.addValue(NODE_INSTANCE_DISK.name(), node.getInstanceDisk());
            params.addValue(NODE_PRICE_TYPE.name(), node.getPriceType().name());
            params.addValue(NODE_DOCKER_IMAGE.name(), String.join(DOCKER_IMAGE_DELIMITER,
                    SetUtils.emptyIfNull(node.getDockerImages())));
            params.addValue(NODE_INSTANCE_COUNT.name(), node.getCount());
            params.addValue(NodeScheduleParameters.SCHEDULE_ID.name(),
                    Optional.ofNullable(node.getSchedule())
                            .map(NodeSchedule::getId)
                            .orElse(null));
            return params;
        }

        static ResultSetExtractor<List<PersistentNode>> getExtractor() {
            return (rs) -> {
                final Map<Long, PersistentNode> nodes = new HashMap<>();
                final Map<Long, NodeSchedule> schedules = new HashMap<>();
                while (rs.next()) {
                    final Long nodeId = rs.getLong(NODE_ID.name());
                    if (!nodes.containsKey(nodeId)) {
                        final PersistentNode node = parsePersistentNode(rs);
                        nodes.put(nodeId, node);
                    }
                    final PersistentNode node = nodes.get(nodeId);
                    final Long scheduleId = rs.getLong(NodeScheduleParameters.SCHEDULE_ID.name());
                    if (scheduleId > 0 && !schedules.containsKey(scheduleId)) {
                        final NodeSchedule schedule = NodeScheduleParameters.parseNodeSchedule(rs);
                        schedules.put(scheduleId, schedule);
                    }
                    final NodeSchedule schedule = schedules.get(scheduleId);
                    if (schedule != null) {
                        node.setSchedule(schedule);
                        final ScheduleEntry entry = NodeScheduleParameters.parseScheduleEntry(rs);
                        schedule.getScheduleEntries().add(entry);
                    }
                }
                return new ArrayList<>(nodes.values());
            };
        }

        private static PersistentNode parsePersistentNode(final ResultSet rs) throws SQLException {
            final PersistentNode node = new PersistentNode();
            node.setId(rs.getLong(NODE_ID.name()));
            node.setName(rs.getString(NODE_NAME.name()));
            node.setCreated(rs.getTimestamp(NODE_CREATED_DATE.name()).toLocalDateTime());
            node.setRegionId(rs.getLong(NODE_REGION_ID.name()));
            node.setInstanceType(rs.getString(NODE_INSTANCE_TYPE.name()));
            node.setInstanceDisk(rs.getInt(NODE_INSTANCE_DISK.name()));
            node.setPriceType(PriceType.valueOf(rs.getString(NODE_PRICE_TYPE.name())));
            node.setDockerImages(parseDockerImages(rs.getString(NODE_DOCKER_IMAGE.name())));
            node.setCount(rs.getInt(NODE_INSTANCE_COUNT.name()));
            return node;
        }

        private static Set<String> parseDockerImages(final String line) {
            if (StringUtils.isBlank(line)) {
                return Collections.emptySet();
            }
            return new HashSet<>(Arrays.asList(line.split(DOCKER_IMAGE_DELIMITER)));
        }
    }
}
