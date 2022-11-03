/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dao.datastorage;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.datastorage.NFSStorageMountStatus;
import com.epam.pipeline.entity.datastorage.StorageQuotaAction;
import com.epam.pipeline.entity.datastorage.StorageQuotaType;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationEntry;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaNotificationRecipient;
import com.epam.pipeline.entity.datastorage.nfs.NFSQuotaTrigger;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class StorageQuotaTriggersDao extends NamedParameterJdbcDaoSupport {

    private String createQuotaTriggerQuery;
    private String updateQuotaTriggerQuery;
    private String findQuotaTriggerQuery;
    private String loadAllQuotaTriggersQuery;
    private String deleteQuotaTriggerQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(final NFSQuotaTrigger triggerEntry) {
        getNamedParameterJdbcTemplate()
            .update(createQuotaTriggerQuery, TriggerDetailsParameters.getParameters(triggerEntry));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(final NFSQuotaTrigger triggerEntry) {
        getNamedParameterJdbcTemplate()
            .update(updateQuotaTriggerQuery, TriggerDetailsParameters.getParameters(triggerEntry));
    }

    public Optional<NFSQuotaTrigger> find(final Long storageId) {
        return getNamedParameterJdbcTemplate()
            .query(findQuotaTriggerQuery,
                   Collections.singletonMap(TriggerDetailsParameters.STORAGE_ID.name(), storageId),
                   TriggerDetailsParameters.getRowMapper())
            .stream()
            .findAny();
    }

    public List<NFSQuotaTrigger> loadAll() {
        return getNamedParameterJdbcTemplate()
            .query(loadAllQuotaTriggersQuery, TriggerDetailsParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final Long storageId) {
        getJdbcTemplate().update(deleteQuotaTriggerQuery, storageId);
    }

    public enum TriggerDetailsParameters {
        STORAGE_ID,
        QUOTA_VALUE,
        QUOTA_TYPE,
        ACTIONS,
        RECIPIENTS,
        UPDATE_DATE,
        TARGET_STATUS,
        STATUS_ACTIVATION_DATE,
        NOTIFICATION_REQUIRED;

        static MapSqlParameterSource getParameters(final NFSQuotaTrigger triggerEntry) {
            final NFSQuotaNotificationEntry quota = triggerEntry.getQuota();
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(STORAGE_ID.name(), triggerEntry.getStorageId());
            params.addValue(QUOTA_VALUE.name(), quota.getValue());
            params.addValue(QUOTA_TYPE.name(), quota.getType().name());
            params.addValue(ACTIONS.name(), JsonMapper.convertDataToJsonStringForQuery(quota.getActions()));
            params.addValue(RECIPIENTS.name(),
                            JsonMapper.convertDataToJsonStringForQuery(triggerEntry.getRecipients()));
            params.addValue(UPDATE_DATE.name(), triggerEntry.getExecutionTime());
            params.addValue(TARGET_STATUS.name(), triggerEntry.getTargetStatus().name());
            params.addValue(STATUS_ACTIVATION_DATE.name(), triggerEntry.getTargetStatusActivationTime());
            params.addValue(NOTIFICATION_REQUIRED.name(), triggerEntry.isNotificationRequired());
            return params;
        }

        static RowMapper<NFSQuotaTrigger> getRowMapper() {
            return (rs, rowNum) -> {
                final long storageId = rs.getLong(STORAGE_ID.name());
                final double quotaValue = rs.getDouble(QUOTA_VALUE.name());
                final StorageQuotaType quotaType = StorageQuotaType.valueOf(rs.getString(QUOTA_TYPE.name()));
                final Set<StorageQuotaAction> actions = JsonMapper.parseData(
                    rs.getString(ACTIONS.name()), new TypeReference<Set<StorageQuotaAction>>() {});
                final List<NFSQuotaNotificationRecipient> recipients = JsonMapper.parseData(
                    rs.getString(RECIPIENTS.name()), new TypeReference<List<NFSQuotaNotificationRecipient>>() {});
                final NFSStorageMountStatus targetStatus =
                    NFSStorageMountStatus.valueOf(rs.getString(TARGET_STATUS.name()));
                final boolean notificationRequired = rs.getBoolean(NOTIFICATION_REQUIRED.name());
                return new NFSQuotaTrigger(storageId,
                                           new NFSQuotaNotificationEntry(quotaValue, quotaType, actions),
                                           recipients,
                                           DaoHelper.parseTimestamp(rs, UPDATE_DATE.name()),
                                           targetStatus,
                                           DaoHelper.parseTimestamp(rs, STATUS_ACTIVATION_DATE.name()),
                                           notificationRequired);
            };
        }
    }

    @Required
    public void setCreateQuotaTriggerQuery(final String createQuotaTriggerQuery) {
        this.createQuotaTriggerQuery = createQuotaTriggerQuery;
    }

    @Required
    public void setUpdateQuotaTriggerQuery(final String updateQuotaTriggerQuery) {
        this.updateQuotaTriggerQuery = updateQuotaTriggerQuery;
    }

    @Required
    public void setFindQuotaTriggerQuery(final String findQuotaTriggerQuery) {
        this.findQuotaTriggerQuery = findQuotaTriggerQuery;
    }

    @Required
    public void setLoadAllQuotaTriggersQuery(final String loadAllQuotaTriggersQuery) {
        this.loadAllQuotaTriggersQuery = loadAllQuotaTriggersQuery;
    }

    @Required
    public void setDeleteQuotaTriggerQuery(String deleteQuotaTriggerQuery) {
        this.deleteQuotaTriggerQuery = deleteQuotaTriggerQuery;
    }
}
