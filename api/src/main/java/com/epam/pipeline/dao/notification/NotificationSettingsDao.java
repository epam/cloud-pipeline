/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.notification.NotificationSettings;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.Array;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.notification.NotificationSettings.NotificationType;

public class NotificationSettingsDao extends NamedParameterJdbcDaoSupport {

    private String createNotificationSettingsQuery;
    private String updateNotificationSettingsQuery;
    private String loadNotificationSettingsQuery;
    private String loadAllNotificationSettingsQuery;
    private String deleteNotificationSettingsQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationSettings createNotificationSettings(NotificationSettings settings) {
        settings.setId(settings.getType().getId());
        getNamedParameterJdbcTemplate().update(createNotificationSettingsQuery,
                NotificationSettingsParameters.getParameters(settings, getConnection()));
        return settings;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationSettings updateNotificationSettings(NotificationSettings settings) {
        getNamedParameterJdbcTemplate().update(updateNotificationSettingsQuery,
                NotificationSettingsParameters.getParameters(settings, getConnection()));
        return settings;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public NotificationSettings loadNotificationSettings(Long id) {
        List<NotificationSettings> items = getJdbcTemplate().query(loadNotificationSettingsQuery,
                NotificationSettingsParameters.getRowMapper(), id);
        return !items.isEmpty() ? items.get(0) : null;
    }

    public List<NotificationSettings> loadAllNotificationsSettings() {
        return getJdbcTemplate().query(loadAllNotificationSettingsQuery,
                NotificationSettingsParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteNotificationSettingsById(long id) {
        getJdbcTemplate().update(deleteNotificationSettingsQuery, id);
    }

    @Required
    public void setCreateNotificationSettingsQuery(String createNotificationSettingsQuery) {
        this.createNotificationSettingsQuery = createNotificationSettingsQuery;
    }

    @Required
    public void setLoadNotificationSettingsQuery(String loadNotificationSettingsQuery) {
        this.loadNotificationSettingsQuery = loadNotificationSettingsQuery;
    }

    @Required
    public void setLoadAllNotificationSettingsQuery(String loadAllNotificationSettingsQuery) {
        this.loadAllNotificationSettingsQuery = loadAllNotificationSettingsQuery;
    }

    @Required
    public void setDeleteNotificationSettingsQuery(String deleteNotificationSettingsQuery) {
        this.deleteNotificationSettingsQuery = deleteNotificationSettingsQuery;
    }

    @Required
    public void setUpdateNotificationSettingsQuery(String updateNotificationSettingsQuery) {
        this.updateNotificationSettingsQuery = updateNotificationSettingsQuery;
    }

    enum NotificationSettingsParameters {
        ID,
        TEMPLATE_ID,
        THRESHOLD,
        RESEND_DELAY,
        KEEP_INFORMED_ADMINS,
        STATUSES_TO_INFORM,
        INFORMED_USER_IDS,
        ENABLED,
        KEEP_INFORMED_OWNER;

        static MapSqlParameterSource getParameters(NotificationSettings settings, Connection connection) {
            MapSqlParameterSource params = new MapSqlParameterSource();

            params.addValue(ID.name(), settings.getId());
            params.addValue(TEMPLATE_ID.name(), settings.getTemplateId());
            params.addValue(RESEND_DELAY.name(), settings.getResendDelay());
            params.addValue(THRESHOLD.name(), settings.getThreshold());
            params.addValue(KEEP_INFORMED_ADMINS.name(), settings.isKeepInformedAdmins());
            params.addValue(INFORMED_USER_IDS.name(),
                    DaoHelper.mapListLongToSqlArray(settings.getInformedUserIds(), connection));
            params.addValue(KEEP_INFORMED_OWNER.name(), settings.isKeepInformedOwner());
            params.addValue(ENABLED.name(), settings.isEnabled());
            params.addValue(STATUSES_TO_INFORM.name(),
                    DaoHelper.mapListLongToSqlArray(
                            CollectionUtils.emptyIfNull(settings.getStatusesToInform()).stream()
                            .map(TaskStatus::getId).collect(Collectors.toList()), connection));
            return params;
        }

        static RowMapper<NotificationSettings> getRowMapper() {
            return (rs, rowNum) -> {
                NotificationSettings settings = new NotificationSettings();

                long settingsId = rs.getLong(ID.name());
                NotificationType notificationType = NotificationType.getById(settingsId);
                Assert.notNull(notificationType, String.format("There is no settings with id: %d", settingsId));

                settings.setId(settingsId);
                settings.setType(notificationType);
                settings.setTemplateId(rs.getLong(TEMPLATE_ID.name()));

                long longVal = rs.getLong(THRESHOLD.name());
                if (rs.wasNull()) {
                    settings.setThreshold(-1L);
                } else {
                    settings.setThreshold(longVal);
                }

                longVal = rs.getLong(RESEND_DELAY.name());
                if (rs.wasNull()) {
                    settings.setResendDelay(-1L);
                } else {
                    settings.setResendDelay(longVal);
                }

                settings.setKeepInformedAdmins(rs.getBoolean(KEEP_INFORMED_ADMINS.name()));
                settings.setKeepInformedOwner(rs.getBoolean(KEEP_INFORMED_OWNER.name()));
                settings.setEnabled(rs.getBoolean(ENABLED.name()));

                Array userIdsSqlArray = rs.getArray(INFORMED_USER_IDS.name());
                if (userIdsSqlArray != null) {
                    List<Long> userIds = Arrays.asList((Long[]) userIdsSqlArray.getArray());
                    settings.setInformedUserIds(userIds);
                }
                Array statusesSqlArray = rs.getArray(STATUSES_TO_INFORM.name());
                if (statusesSqlArray != null) {
                    List<TaskStatus> statusesToInform = Arrays.stream((Long[]) statusesSqlArray.getArray())
                            .map(TaskStatus::getById)
                            .collect(Collectors.toList());
                    settings.setStatusesToInform(statusesToInform);
                }
                return settings;
            };
        }



    }
}
