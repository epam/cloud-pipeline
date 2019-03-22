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

import java.util.List;

import com.epam.pipeline.entity.notification.NotificationTemplate;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class NotificationTemplateDao extends NamedParameterJdbcDaoSupport {

    private String createNotificationTemplateQuery;
    private String updateNotificationTemplateQuery;
    private String deleteNotificationTemplateQuery;
    private String loadNotificationTemplateQuery;
    private String loadAllNotificationTemplatesQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationTemplate createNotificationTemplate(NotificationTemplate notificationTemplate) {
        getNamedParameterJdbcTemplate().update(createNotificationTemplateQuery,
                NotificationTemplateParameters.getParameters(notificationTemplate));
        return notificationTemplate;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public NotificationTemplate loadNotificationTemplate(Long id) {
        List<NotificationTemplate> items = getJdbcTemplate().query(loadNotificationTemplateQuery,
                NotificationTemplateParameters.getRowMapper(), id);
        return !items.isEmpty() ? items.get(0) : null;
    }

    public List<NotificationTemplate> loadAllNotificationTemplates() {
        return getJdbcTemplate().query(loadAllNotificationTemplatesQuery,
                                       NotificationTemplateParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationTemplate updateNotificationTemplate(NotificationTemplate template) {
        getNamedParameterJdbcTemplate().update(updateNotificationTemplateQuery,
                                               NotificationTemplateParameters.getParameters(template));
        return template;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteNotificationTemplate(long templateId) {
        getJdbcTemplate().update(deleteNotificationTemplateQuery, templateId);
    }

    enum NotificationTemplateParameters {
        ID,
        NAME,
        SUBJECT,
        BODY;

        static MapSqlParameterSource getParameters(NotificationTemplate template) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ID.name(), template.getId());
            params.addValue(NAME.name(), template.getName());
            params.addValue(SUBJECT.name(), template.getSubject());
            params.addValue(BODY.name(), template.getBody());
            return params;
        }

        static RowMapper<NotificationTemplate> getRowMapper() {
            return (rs, rowNum) -> {
                NotificationTemplate template = new NotificationTemplate();
                template.setId(rs.getLong(ID.name()));
                template.setName(rs.getString(NAME.name()));
                template.setSubject(rs.getString(SUBJECT.name()));
                template.setBody(rs.getString(BODY.name()));
                return template;
            };
        }

    }

    @Required
    public void setCreateNotificationTemplateQuery(String createNotificationTemplateQuery) {
        this.createNotificationTemplateQuery = createNotificationTemplateQuery;
    }

    @Required
    public void setLoadNotificationTemplateQuery(String loadNotificationTemplateQuery) {
        this.loadNotificationTemplateQuery = loadNotificationTemplateQuery;
    }

    @Required
    public void setLoadAllNotificationTemplatesQuery(String loadAllNotificationTemplatesQuery) {
        this.loadAllNotificationTemplatesQuery = loadAllNotificationTemplatesQuery;
    }

    @Required
    public void setUpdateNotificationTemplateQuery(String updateNotificationTemplateQuery) {
        this.updateNotificationTemplateQuery = updateNotificationTemplateQuery;
    }

    @Required
    public void setDeleteNotificationTemplateQuery(String deleteNotificationTemplateQuery) {
        this.deleteNotificationTemplateQuery = deleteNotificationTemplateQuery;
    }
}
