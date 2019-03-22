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

import com.epam.pipeline.controller.vo.SystemNotificationFilterVO;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.notification.SystemNotification;
import com.epam.pipeline.entity.notification.SystemNotificationSeverity;
import com.epam.pipeline.entity.notification.SystemNotificationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NotificationDao extends NamedParameterJdbcDaoSupport {
    private Pattern wherePattern = Pattern.compile("@WHERE@");
    private static final String AND = " AND ";
    @Autowired
    private DaoHelper daoHelper;

    private String notificationSequence;
    private String createNotificationQuery;
    private String updateNotificationQuery;
    private String deleteNotificationQuery;
    private String listNotificationsQuery;
    private String filterNotificationsQuery;
    private String loadNotificationQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createNotificationId() {
        return daoHelper.createId(notificationSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SystemNotification createNotification(SystemNotification notification) {
        notification.setNotificationId(this.createNotificationId());
        getNamedParameterJdbcTemplate().update(
                createNotificationQuery,
                NotificationParameters.getParameters(notification)
        );
        return notification;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SystemNotification updateNotification(SystemNotification notification) {
        getNamedParameterJdbcTemplate().update(
                updateNotificationQuery,
                NotificationParameters.getParameters(notification)
        );
        return notification;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteNotification(Long id) {
        getJdbcTemplate().update(deleteNotificationQuery, id);
    }

    public List<SystemNotification> loadAllNotifications() {
        return getNamedParameterJdbcTemplate().query(listNotificationsQuery,
                NotificationParameters.getRowMapper());
    }

    public List<SystemNotification> filterNotifications(SystemNotificationFilterVO systemNotificationFilterVO) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = wherePattern.matcher(filterNotificationsQuery)
                .replaceFirst(makeFilterCondition(systemNotificationFilterVO, params));
        return getNamedParameterJdbcTemplate().query(query, params, NotificationParameters.getRowMapper());
    }

    private String makeFilterCondition(SystemNotificationFilterVO filter, MapSqlParameterSource params) {
        StringBuilder whereBuilder = new StringBuilder();

        if (!filter.isEmpty()) {
            int clausesCount = 0;
            whereBuilder.append(" WHERE ");

            if (filter.getSeverityList() != null && !filter.getSeverityList().isEmpty()) {
                whereBuilder.append(" n.severity in (:")
                        .append(NotificationParameters.SEVERITY.name())
                        .append(')');
                params.addValue(
                        NotificationParameters.SEVERITY.name(),
                        filter.getSeverityList()
                                .stream()
                                .map(SystemNotificationSeverity::getId)
                                .collect(Collectors.toList())
                );
                clausesCount++;
            }
            if (filter.getStateList() != null && !filter.getStateList().isEmpty()) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" n.state in (:")
                        .append(NotificationParameters.STATE.name())
                        .append(')');
                params.addValue(
                        NotificationParameters.STATE.name(),
                        filter.getStateList()
                                .stream()
                                .map(SystemNotificationState::getId)
                                .collect(Collectors.toList())
                );
                clausesCount++;
            }
            if (filter.getCreatedDateAfter() != null) {
                if (clausesCount > 0) {
                    whereBuilder.append(AND);
                }
                whereBuilder.append(" n.created_date > :").append(NotificationParameters.CREATED_DATE.name());
                params.addValue(NotificationParameters.CREATED_DATE.name(), filter.getCreatedDateAfter());
            }
        }
        return whereBuilder.toString();
    }

    public SystemNotification loadNotification(Long id) {
        List<SystemNotification> items = getJdbcTemplate().query(
                loadNotificationQuery,
                NotificationParameters.getRowMapper(),
                id
        );
        return !items.isEmpty() ? items.get(0) : null;
    }

    enum NotificationParameters {
        NOTIFICATION_ID,
        SEVERITY,
        TITLE,
        BODY,
        STATE,
        CREATED_DATE,
        BLOCKING;

        static MapSqlParameterSource getParameters(SystemNotification systemNotification) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(NOTIFICATION_ID.name(), systemNotification.getNotificationId());
            params.addValue(SEVERITY.name(), systemNotification.getSeverity().getId());
            params.addValue(TITLE.name(), systemNotification.getTitle());
            params.addValue(BODY.name(), systemNotification.getBody());
            params.addValue(STATE.name(), systemNotification.getState().getId());
            params.addValue(CREATED_DATE.name(), systemNotification.getCreatedDate());
            params.addValue(BLOCKING.name(), systemNotification.getBlocking());
            return params;
        }

        static RowMapper<SystemNotification> getRowMapper() {
            return (rs, rowNum) -> {
                SystemNotification systemNotification = new SystemNotification();
                systemNotification.setNotificationId(rs.getLong(NOTIFICATION_ID.name()));
                systemNotification.setSeverity(SystemNotificationSeverity.getById(rs.getLong(SEVERITY.name())));
                systemNotification.setTitle(rs.getString(TITLE.name()));
                systemNotification.setBody(rs.getString(BODY.name()));
                systemNotification.setState(SystemNotificationState.getById(rs.getLong(STATE.name())));
                systemNotification.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                systemNotification.setBlocking(rs.getBoolean(BLOCKING.name()));
                return systemNotification;
            };
        }
    }

    @Required
    public void setNotificationSequence(String notificationSequence) {
        this.notificationSequence = notificationSequence;
    }

    @Required
    public void setCreateNotificationQuery(String query) {
        this.createNotificationQuery = query;
    }

    @Required
    public void setUpdateNotificationQuery(String query) {
        this.updateNotificationQuery = query;
    }

    @Required
    public void setDeleteNotificationQuery(String query) {
        this.deleteNotificationQuery = query;
    }

    @Required
    public void setListNotificationsQuery(String query) {
        this.listNotificationsQuery = query;
    }

    @Required
    public void setFilterNotificationsQuery(String query) {
        this.filterNotificationsQuery = query;
    }

    @Required
    public void setLoadNotificationQuery(String query) {
        this.loadNotificationQuery = query;
    }
}
