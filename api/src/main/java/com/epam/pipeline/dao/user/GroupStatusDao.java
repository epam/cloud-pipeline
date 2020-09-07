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

package com.epam.pipeline.dao.user;

import com.epam.pipeline.entity.user.GroupStatus;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class GroupStatusDao extends NamedParameterJdbcDaoSupport {

    private String upsertGroupStatusQuery;
    private String loadGroupsBlockedStatusQuery;
    private String deleteGroupStatusQuery;
    private String loadAllGroupsStatusesQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public GroupStatus upsertGroupBlockingStatusQuery(final GroupStatus groupStatus) {
        getNamedParameterJdbcTemplate().update(upsertGroupStatusQuery,
                GroupParameters.getParameters(groupStatus));
        return groupStatus;
    }

    public List<GroupStatus> loadGroupsBlockingStatus(final List<String> groupNames) {
        return getNamedParameterJdbcTemplate().query(loadGroupsBlockedStatusQuery,
                GroupParameters.getNamesListParameters(groupNames),
                GroupParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteGroupBlockingStatus(final String groupName) {
        getJdbcTemplate().update(deleteGroupStatusQuery, groupName);
    }

    public List<GroupStatus> loadAllGroupsBlockingStatuses() {
        return getJdbcTemplate().query(loadAllGroupsStatusesQuery, GroupParameters.getRowMapper());
    }

    enum GroupParameters {
        GROUP_NAME,
        GROUP_BLOCKED_STATUS,
        GROUPS;

        private static MapSqlParameterSource getParameters(final GroupStatus groupStatus) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(GROUP_NAME.name(), groupStatus.getGroupName());
            params.addValue(GROUP_BLOCKED_STATUS.name(), groupStatus.isBlocked());
            return params;
        }

        private static RowMapper<GroupStatus> getRowMapper() {
            return (rs, rowNum) -> new GroupStatus(rs.getString(GROUP_NAME.name()),
                    rs.getBoolean(GROUP_BLOCKED_STATUS.name()));
        }

        private static MapSqlParameterSource getNamesListParameters(final List<String> groupNames) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(GROUPS.name(), groupNames);
            return params;
        }
    }

    @Required
    public void setUpsertGroupStatusQuery(final String upsertGroupStatusQuery) {
        this.upsertGroupStatusQuery = upsertGroupStatusQuery;
    }

    @Required
    public void setLoadGroupsBlockedStatusQuery(final String loadGroupsBlockedStatusQuery) {
        this.loadGroupsBlockedStatusQuery = loadGroupsBlockedStatusQuery;
    }

    @Required
    public void setDeleteGroupStatusQuery(final String deleteGroupStatusQuery) {
        this.deleteGroupStatusQuery = deleteGroupStatusQuery;
    }

    @Required
    public void setLoadAllGroupsStatusesQuery(final String loadAllGroupsStatusesQuery) {
        this.loadAllGroupsStatusesQuery = loadAllGroupsStatusesQuery;
    }
}
