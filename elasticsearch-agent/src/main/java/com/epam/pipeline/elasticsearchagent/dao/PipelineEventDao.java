/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.elasticsearchagent.dao;

import com.epam.pipeline.elasticsearchagent.model.EventType;
import com.epam.pipeline.elasticsearchagent.model.PipelineEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
public class PipelineEventDao extends NamedParameterJdbcDaoSupport {

    private String createEventQuery;
    private String loadAllEventsByObjectTypeQuery;
    private String deleteEventQuery;

    @Transactional(propagation = Propagation.REQUIRED)
    public void createPipelineEvent(final PipelineEvent pipelineEvent) {
        log.debug("Creating event: {}", pipelineEvent);
        getNamedParameterJdbcTemplate().update(createEventQuery, getParameters(pipelineEvent));
        log.debug("Created event: {}", pipelineEvent);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineEvent> loadPipelineEventsByObjectType(final PipelineEvent.ObjectType objectType,
                                                              final LocalDateTime before) {
        return loadPipelineEventsByObjectType(objectType, before, -1);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<PipelineEvent> loadPipelineEventsByObjectType(final PipelineEvent.ObjectType objectType,
                                                              final LocalDateTime before, final int rowLimit) {
        final MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue(PipelineEventsParameters.OBJECT_TYPE.name(), objectType.getDbName());
        parameterSource.addValue(PipelineEventsParameters.STAMP.name(),
                OffsetDateTime.of(before, ZoneOffset.ofHours(0)));
        final NamedParameterJdbcTemplate template = getNamedParameterJdbcTemplate();
        ((JdbcTemplate) template.getJdbcOperations()).setMaxRows(rowLimit);
        final List<PipelineEvent> pipelineEvents =
            template.query(loadAllEventsByObjectTypeQuery, parameterSource, PipelineEventsParameters.getRowMapper());
        return ListUtils.emptyIfNull(pipelineEvents);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteEventByObjectId(final Long id, final PipelineEvent.ObjectType objectType,
                                      final LocalDateTime before) {
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue(PipelineEventsParameters.OBJECT_ID.name(), id);
        parameterSource.addValue(PipelineEventsParameters.STAMP.name(), before);
        parameterSource.addValue(PipelineEventsParameters.OBJECT_TYPE.name(), objectType.getDbName());
        getNamedParameterJdbcTemplate().update(deleteEventQuery, parameterSource);
    }

    private MapSqlParameterSource getParameters(final PipelineEvent pipelineEvent) {
        MapSqlParameterSource params = new MapSqlParameterSource();

        params.addValue(PipelineEventsParameters.OPERATION.name(), pipelineEvent.getEventType().getCode());
        params.addValue(PipelineEventsParameters.STAMP.name(), pipelineEvent.getCreatedDate());
        params.addValue(PipelineEventsParameters.OBJECT_TYPE.name(), pipelineEvent.getObjectType().getDbName());
        params.addValue(PipelineEventsParameters.OBJECT_ID.name(), pipelineEvent.getObjectId());
        params.addValue(PipelineEventsParameters.DATA.name(), pipelineEvent.getData());
        return params;
    }

    enum PipelineEventsParameters {
        OPERATION,
        STAMP,
        OBJECT_TYPE,
        OBJECT_ID,
        DATA;


        static RowMapper<PipelineEvent> getRowMapper() {
            return (rs, rowNum) -> parsePipelineEvent(rs);
        }

        private static PipelineEvent parsePipelineEvent(ResultSet rs) throws SQLException {
            PipelineEvent event = new PipelineEvent();
            event.setEventType(EventType.fromCode(rs.getString(OPERATION.name()).charAt(0)));
            event.setCreatedDate(rs.getTimestamp(STAMP.name()).toLocalDateTime());
            event.setObjectType(PipelineEvent.ObjectType.valueOf(rs.getString(OBJECT_TYPE.name()).toUpperCase()));
            event.setObjectId(rs.getLong(OBJECT_ID.name()));
            String data = rs.getString(DATA.name());
            if (!rs.wasNull()) {
                event.setData(data);
            }
            return event;
        }
    }

    @Required
    public void setCreateEventQuery(String createEventQuery) {
        this.createEventQuery = createEventQuery;
    }

    @Required
    public void setLoadAllEventsByObjectTypeQuery(String loadAllEventsByObjectTypeQuery) {
        this.loadAllEventsByObjectTypeQuery = loadAllEventsByObjectTypeQuery;
    }

    @Required
    public void setDeleteEventQuery(String deleteEventQuery) {
        this.deleteEventQuery = deleteEventQuery;
    }
}
