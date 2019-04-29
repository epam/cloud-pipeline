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

package com.epam.pipeline.dao.notification;

import static com.epam.pipeline.config.JsonMapper.parseData;
import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.epam.pipeline.entity.notification.NotificationTimestamp;
import com.epam.pipeline.entity.utils.DateUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.notification.NotificationMessage;
import com.epam.pipeline.entity.notification.NotificationTemplate;
import com.fasterxml.jackson.core.type.TypeReference;

public class MonitoringNotificationDao extends NamedParameterJdbcDaoSupport {
    @Autowired
    private DaoHelper daoHelper;

    private String notificationQueueSequence;
    private String createMonitoringNotificationQuery;
    private String loadMonitoringNotificationQuery;
    private String loadAllMonitoringNotificationsQuery;
    private String deleteNotificationsByTemplateIdQuery;
    private String updateNotificationTimestampQuery;
    private String loadNotificationTimestampQuery;
    private String deleteNotificationTimestampsByRunIdQuery;
    private String deleteNotificationTimestampsByPipelineIdQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createMonitoringNotification(NotificationMessage notificationMessage) {
        notificationMessage.setId(daoHelper.createId(notificationQueueSequence));
        getNamedParameterJdbcTemplate().update(createMonitoringNotificationQuery,
                MonitoringNotificationParameters.getParameters(notificationMessage));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createMonitoringNotifications(List<NotificationMessage> notificationMessages) {
        List<Long> ids = daoHelper.createIds(notificationQueueSequence, notificationMessages.size());

        MapSqlParameterSource[] params = IntStream.range(0, notificationMessages.size())
            .mapToObj(i -> {
                NotificationMessage message = notificationMessages.get(i);
                message.setId(ids.get(i));
                return MonitoringNotificationParameters.getParameters(message);
            })
            .toArray(MapSqlParameterSource[]::new);

        getNamedParameterJdbcTemplate().batchUpdate(createMonitoringNotificationQuery, params);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public NotificationMessage loadMonitoringNotification(Long id) {
        List<NotificationMessage> items = getJdbcTemplate().query(loadMonitoringNotificationQuery,
                MonitoringNotificationParameters.getRowMapper(), id);
        return !items.isEmpty() ? items.get(0) : null;
    }

    public List<NotificationMessage> loadAllNotifications() {
        return getJdbcTemplate().query(loadAllMonitoringNotificationsQuery,
                                       MonitoringNotificationParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteNotificationsByTemplateId(long templateId) {
        getJdbcTemplate().update(deleteNotificationsByTemplateIdQuery, templateId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateNotificationTimestamp(final List<Long> ids, final NotificationType notificationType) {
        final MapSqlParameterSource[] params = ids.stream()
                .map(value -> {
                    MapSqlParameterSource param = new MapSqlParameterSource();
                    param.addValue(NotificationTimestampParameters.RUN_ID.name(), value);
                    param.addValue(NotificationTimestampParameters.NOTIFICATION_TYPE.name(), notificationType.name());
                    param.addValue(NotificationTimestampParameters.TIMESTAMP.name(), DateUtils.nowUTC());
                    return param;
                }).toArray(MapSqlParameterSource[]::new);

        getNamedParameterJdbcTemplate().batchUpdate(updateNotificationTimestampQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<NotificationTimestamp> loadNotificationTimestamp(final Long runId, final NotificationType type) {
        final MapSqlParameterSource param = new MapSqlParameterSource();
        param.addValue(NotificationTimestampParameters.RUN_ID.name(), runId);
        param.addValue(NotificationTimestampParameters.NOTIFICATION_TYPE.name(), type.name());
        final List<NotificationTimestamp> items = getNamedParameterJdbcTemplate().query(loadNotificationTimestampQuery,
                param, NotificationTimestampParameters.getRowMapper());
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteNotificationTimestampsForRun(final Long runId) {
        getJdbcTemplate().update(deleteNotificationTimestampsByRunIdQuery, runId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteNotificationTimestampsForPipeline(final Long pipelineId) {
        getJdbcTemplate().update(deleteNotificationTimestampsByPipelineIdQuery, pipelineId);
    }

    enum MonitoringNotificationParameters {
        ID,
        TO_USER_ID,
        USER_IDS,
        SUBJECT,
        BODY,
        TEMPLATE_ID,
        TEMPLATE_PARAMETERS;

        static MapSqlParameterSource getParameters(final NotificationMessage notificationMessage) {
            final MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ID.name(), notificationMessage.getId());
            params.addValue(SUBJECT.name(), notificationMessage.getSubject());
            params.addValue(BODY.name(), notificationMessage.getBody());
            params.addValue(TO_USER_ID.name(), notificationMessage.getToUserId());
            params.addValue(USER_IDS.name(), StringUtils.join(notificationMessage.getCopyUserIds(), ","));
            params.addValue(TEMPLATE_ID.name(),
                    notificationMessage.getTemplate() != null ? notificationMessage.getTemplate().getId() : null);
            params.addValue(TEMPLATE_PARAMETERS.name(),
                    JsonMapper.convertDataToJsonStringForQuery(notificationMessage.getTemplateParameters()));
            return params;
        }

        static RowMapper<NotificationMessage> getRowMapper() {
            return (rs, rowNum) -> {
                final NotificationMessage notificationMessage = new NotificationMessage();
                notificationMessage.setId(rs.getLong(ID.name()));
                notificationMessage.setSubject(rs.getString(SUBJECT.name()));
                notificationMessage.setBody(rs.getString(BODY.name()));

                final long longVal = rs.getLong(TO_USER_ID.name());
                if (!rs.wasNull()) {
                    notificationMessage.setToUserId(longVal);
                }

                notificationMessage.setCopyUserIds(mapStringToListLong(rs.getString(USER_IDS.name())));
                long templateId = rs.getLong(TEMPLATE_ID.name());
                if (!rs.wasNull()) {
                    notificationMessage.setTemplate(new NotificationTemplate(templateId));
                }
                notificationMessage.setTemplateParameters(parseData(rs.getString(TEMPLATE_PARAMETERS.name()),
                        new TypeReference<Map<String, Object>>(){}));
                return notificationMessage;
            };
        }

    }
    enum NotificationTimestampParameters {
        RUN_ID,
        NOTIFICATION_TYPE,
        TIMESTAMP;

        static RowMapper<NotificationTimestamp> getRowMapper() {
            return (rs, rowNum) -> {
                NotificationTimestamp timestamp = new NotificationTimestamp();
                timestamp.setRunId(rs.getLong(RUN_ID.name()));
                timestamp.setType(NotificationType.valueOf(rs.getString(NOTIFICATION_TYPE.name())));
                timestamp.setTimestamp(rs.getTimestamp(TIMESTAMP.name()).toLocalDateTime());

                return timestamp;
            };
        }

    }
    private static List<Long> mapStringToListLong(final String userIds) {
        if (StringUtils.isBlank(userIds)) {
            return Collections.emptyList();
        }

        return Stream.of(userIds.split(",")).map(Long::parseLong).collect(Collectors.toList());
    }

    @Required
    public void setNotificationQueueSequence(String notificationQueueSequence) {
        this.notificationQueueSequence = notificationQueueSequence;
    }

    @Required
    public void setCreateMonitoringNotificationQuery(String createMonitoringNotificationQuery) {
        this.createMonitoringNotificationQuery = createMonitoringNotificationQuery;
    }

    @Required
    public void setLoadMonitoringNotificationQuery(String loadMonitoringNotificationQuery) {
        this.loadMonitoringNotificationQuery = loadMonitoringNotificationQuery;
    }

    @Required
    public void setDeleteNotificationsByTemplateIdQuery(String deleteNotificationsByTemplateIdQuery) {
        this.deleteNotificationsByTemplateIdQuery = deleteNotificationsByTemplateIdQuery;
    }

    @Required
    public void setLoadAllMonitoringNotificationsQuery(String loadAllMonitoringNotificationsQuery) {
        this.loadAllMonitoringNotificationsQuery = loadAllMonitoringNotificationsQuery;
    }

    @Required
    public void setUpdateNotificationTimestampQuery(String updateNotificationTimestampQuery) {
        this.updateNotificationTimestampQuery = updateNotificationTimestampQuery;
    }

    @Required
    public void setLoadNotificationTimestampQuery(String loadNotificationTimestampQuery) {
        this.loadNotificationTimestampQuery = loadNotificationTimestampQuery;
    }

    @Required
    public void setDeleteNotificationTimestampsByRunIdQuery(String deleteNotificationTimestampsByRunIdQuery) {
        this.deleteNotificationTimestampsByRunIdQuery = deleteNotificationTimestampsByRunIdQuery;
    }

    @Required
    public void setDeleteNotificationTimestampsByPipelineIdQuery(String deleteNotificationTimestampsByPipelineIdQuery) {
        this.deleteNotificationTimestampsByPipelineIdQuery = deleteNotificationTimestampsByPipelineIdQuery;
    }
}
