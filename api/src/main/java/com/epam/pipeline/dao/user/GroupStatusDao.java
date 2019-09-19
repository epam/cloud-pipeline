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
    private String loadGroupsBlockedStatusByNameQuery;
    private String loadGroupBlockedStatusQuery;
    private String deleteGroupStatusQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public GroupStatus upsertGroupBlockingStatusQuery(final GroupStatus groupStatus) {
        getNamedParameterJdbcTemplate().update(upsertGroupStatusQuery,
                GroupParameters.getParameters(groupStatus));
        return groupStatus;
    }

    public GroupStatus loadGroupBlockingStatus(final String groupName, final boolean external) {
        return getJdbcTemplate().query(loadGroupBlockedStatusQuery, GroupParameters.getRowMapper(), groupName, external)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<GroupStatus> loadGroupsBlockingStatus(final List<String> groupNames) {
        return getNamedParameterJdbcTemplate().query(loadGroupsBlockedStatusByNameQuery,
                GroupParameters.getNamesListParameters(groupNames),
                GroupParameters.getRowMapper());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteGroupBlockingStatus(final String groupName, final boolean external) {
        getJdbcTemplate().update(deleteGroupStatusQuery, groupName, external);
    }


    enum GroupParameters {
        GROUP_NAME,
        GROUP_BLOCKED_STATUS,
        GROUP_EXTERNAL,
        GROUPS;

        private static MapSqlParameterSource getParameters(final GroupStatus groupStatus) {
            final MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(GROUP_NAME.name(), groupStatus.getGroupName());
            params.addValue(GROUP_BLOCKED_STATUS.name(), groupStatus.isBlocked());
            params.addValue(GROUP_EXTERNAL.name(), groupStatus.isExternal());
            return params;
        }

        private static RowMapper<GroupStatus> getRowMapper() {
            return (rs, rowNum) -> new GroupStatus(
                    rs.getString(GROUP_NAME.name()),
                    rs.getBoolean(GROUP_BLOCKED_STATUS.name()),
                    rs.getBoolean(GROUP_EXTERNAL.name()));
        }

        private static MapSqlParameterSource getNamesListParameters(List<String> groupNames) {
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
    public void setLoadGroupBlockedStatusQuery(final String loadGroupStatusQuery) {
        this.loadGroupBlockedStatusQuery = loadGroupStatusQuery;
    }

    @Required
    public void setLoadGroupsBlockedStatusByNameQuery(final String loadGroupsBlockedStatusByNameQuery) {
        this.loadGroupsBlockedStatusByNameQuery = loadGroupsBlockedStatusByNameQuery;
    }

    @Required
    public void setDeleteGroupStatusQuery(final String deleteGroupStatusQuery) {
        this.deleteGroupStatusQuery = deleteGroupStatusQuery;
    }
}
