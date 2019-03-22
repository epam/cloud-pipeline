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

package com.epam.pipeline.dao.issue;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.epam.pipeline.dao.DaoHelper.mapListToSqlArray;

public class IssueDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String issueSequence;
    private String createIssueQuery;
    private String loadIssueByIdQuery;
    private String loadAllIssuesForEntityQuery;
    private String loadIssuesByAuthorQuery;
    private String countIssuesByAuthorQuery;
    private String updateIssueQuery;
    private String deleteIssueQuery;
    private String deleteIssuesForEntityQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createIssueId() {
        return daoHelper.createId(issueSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createIssue(Issue issue) {
        issue.setId(createIssueId());
        Date now = DateUtils.now();
        issue.setUpdatedDate(now);
        issue.setCreatedDate(now);
        issue.setStatus(IssueStatus.getById(0L));
        getNamedParameterJdbcTemplate().update(createIssueQuery, IssueParameters.getParameters(issue, getConnection()));
    }

    public Optional<Issue> loadIssue(Long id) {
        return getJdbcTemplate().query(loadIssueByIdQuery, IssueParameters.getRowMapper(), id).stream().findFirst();
    }

    public List<Issue> loadIssuesForEntity(EntityVO entity) {
        return getJdbcTemplate().query(loadAllIssuesForEntityQuery,
                IssueParameters.getRowMapper(), entity.getEntityId(), entity.getEntityClass().name());
    }

    public List<Issue> loadIssuesByAuthor(String author, Long page, Integer pageSize) {
        long offset = (page - 1) * pageSize;
        return getJdbcTemplate().query(loadIssuesByAuthorQuery, IssueParameters.getRowMapper(), author, pageSize,
                                       offset);
    }

    public int countIssuesByAuthor(String author) {
        return getJdbcTemplate().queryForObject(countIssuesByAuthorQuery, Integer.class, author);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateIssue(Issue issue) {
        issue.setUpdatedDate(DateUtils.now());
        getNamedParameterJdbcTemplate().update(updateIssueQuery, IssueParameters.getParameters(issue, getConnection()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteIssue(Long id) {
        getJdbcTemplate().update(deleteIssueQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteIssuesForEntity(EntityVO entity) {
        getJdbcTemplate().update(deleteIssuesForEntityQuery, entity.getEntityId(), entity.getEntityClass().name());
    }

    @Required
    public void setIssueSequence(String issueSequence) {
        this.issueSequence = issueSequence;
    }

    @Required
    public void setCreateIssueQuery(String createIssueQuery) {
        this.createIssueQuery = createIssueQuery;
    }

    @Required
    public void setLoadIssueByIdQuery(String loadIssueByIdQuery) {
        this.loadIssueByIdQuery = loadIssueByIdQuery;
    }

    @Required
    public void setLoadAllIssuesForEntityQuery(String loadAllIssuesForEntityQuery) {
        this.loadAllIssuesForEntityQuery = loadAllIssuesForEntityQuery;
    }

    @Required
    public void setUpdateIssueQuery(String updateIssueQuery) {
        this.updateIssueQuery = updateIssueQuery;
    }

    @Required
    public void setDeleteIssueQuery(String deleteIssueQuery) {
        this.deleteIssueQuery = deleteIssueQuery;
    }

    @Required
    public void setDeleteIssuesForEntityQuery(String deleteIssuesForEntityQuery) {
        this.deleteIssuesForEntityQuery = deleteIssuesForEntityQuery;
    }

    @Required
    public void setLoadIssuesByAuthorQuery(String loadIssuesByAuthorQuery) {
        this.loadIssuesByAuthorQuery = loadIssuesByAuthorQuery;
    }

    @Required
    public void setCountIssuesByAuthorQuery(String countIssuesByAuthorQuery) {
        this.countIssuesByAuthorQuery = countIssuesByAuthorQuery;
    }

    enum IssueParameters {
        ISSUE_ID,
        ISSUE_NAME,
        ISSUE_TEXT,
        ISSUE_AUTHOR,
        ENTITY_ID,
        ENTITY_CLASS,
        CREATED_DATE,
        UPDATED_DATE,
        ISSUE_STATUS,
        LABELS;

        static MapSqlParameterSource getParameters(Issue issue, Connection connection) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ISSUE_ID.name(), issue.getId());
            params.addValue(ISSUE_NAME.name(), issue.getName());
            params.addValue(ISSUE_TEXT.name(), issue.getText());
            params.addValue(ISSUE_AUTHOR.name(), issue.getAuthor());
            params.addValue(ENTITY_ID.name(), issue.getEntity().getEntityId());
            params.addValue(ENTITY_CLASS.name(), issue.getEntity().getEntityClass().name());
            params.addValue(CREATED_DATE.name(), issue.getCreatedDate());
            params.addValue(UPDATED_DATE.name(), issue.getUpdatedDate());
            params.addValue(ISSUE_STATUS.name(), issue.getStatus().getId());
            Array labelsSqlArray = mapListToSqlArray(issue.getLabels(), connection);
            params.addValue(LABELS.name(), labelsSqlArray);
            return params;
        }

        static RowMapper<Issue> getRowMapper() {
            return (rs, rowNum) -> {
                Issue issue = new Issue();
                issue.setId(rs.getLong(ISSUE_ID.name()));
                issue.setName(rs.getString(ISSUE_NAME.name()));
                issue.setText(rs.getString(ISSUE_TEXT.name()));
                issue.setAuthor(rs.getString(ISSUE_AUTHOR.name()));
                EntityVO issueEntity = new EntityVO(
                        rs.getLong(ENTITY_ID.name()),
                        AclClass.valueOf(rs.getString(ENTITY_CLASS.name()))
                );
                issue.setEntity(issueEntity);
                issue.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
                issue.setUpdatedDate(new Date(rs.getTimestamp(UPDATED_DATE.name()).getTime()));
                issue.setStatus(IssueStatus.getById(rs.getLong(ISSUE_STATUS.name())));
                Array labelsSqlArray = rs.getArray(LABELS.name());
                if(labelsSqlArray != null) {
                    List<String> labelsList = Arrays.asList((String[]) labelsSqlArray.getArray());
                    issue.setLabels(labelsList);
                }
                return issue;
            };
        }
    }
}
