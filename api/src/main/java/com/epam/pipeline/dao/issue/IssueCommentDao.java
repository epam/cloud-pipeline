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

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.utils.DateUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IssueCommentDao extends NamedParameterJdbcDaoSupport {

    @Autowired
    private DaoHelper daoHelper;

    private String issueCommentSequence;
    private String createIssueCommentQuery;
    private String loadIssueCommentByIdQuery;
    private String loadAllCommentsForIssueQuery;
    private String loadAllCommentsForIssuesQuery;
    private String updateIssueCommentQuery;
    private String deleteIssueCommentQuery;
    private String deleteAllCommentsForIssueQuery;
    private String deleteCommentsForIssuesListQuery;

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createCommentId() {
        return daoHelper.createId(issueCommentSequence);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createComment(IssueComment comment) {
        comment.setId(createCommentId());
        Date now = DateUtils.now();
        comment.setUpdatedDate(now);
        comment.setCreatedDate(now);
        getNamedParameterJdbcTemplate().update(createIssueCommentQuery, CommentParameters.getParameters(comment));
    }

    public Optional<IssueComment> loadComment(Long id) {
        return getJdbcTemplate().query(loadIssueCommentByIdQuery,
                CommentParameters.getRowMapper(), id).stream().findFirst();
    }

    public List<IssueComment> loadCommentsForIssue(Long issueId) {
        return getJdbcTemplate().query(loadAllCommentsForIssueQuery,
                CommentParameters.getRowMapper(), issueId);
    }

    public Map<Long, List<IssueComment>> loadCommentsForIssues(Collection<Long> issueIds) {
        if (CollectionUtils.isEmpty(issueIds)) {
            return Collections.emptyMap();
        }

        Map<Long, List<IssueComment>> map = new HashMap<>();

        getJdbcTemplate().query(DaoHelper.replaceInClause(loadAllCommentsForIssuesQuery, issueIds.size()), rs -> {
            IssueComment comment = CommentParameters.getRowMapper().mapRow(rs, 0);
            if (!map.containsKey(comment.getIssueId())) {
                map.put(comment.getIssueId(), new ArrayList<>());
            }

            map.get(comment.getIssueId()).add(comment);

        }, (Object[]) issueIds.toArray(new Object[issueIds.size()]));

        return map;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateComment(IssueComment comment) {
        comment.setUpdatedDate(DateUtils.now());
        getNamedParameterJdbcTemplate().update(updateIssueCommentQuery,
                CommentParameters.getParameters(comment));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteComment(Long id) {
        getJdbcTemplate().update(deleteIssueCommentQuery, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllCommentsForIssue(Long issueId) {
        getJdbcTemplate().update(deleteAllCommentsForIssueQuery, issueId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAllCommentsForIssuesList(List<Long> issuesIds) {
        getNamedParameterJdbcTemplate().update(deleteCommentsForIssuesListQuery,
                Collections.singletonMap("ISSUES_IDS", issuesIds));
    }

    @Required
    public void setIssueCommentSequence(String issueCommentSequence) {
        this.issueCommentSequence = issueCommentSequence;
    }

    @Required
    public void setCreateIssueCommentQuery(String createIssueCommentQuery) {
        this.createIssueCommentQuery = createIssueCommentQuery;
    }

    @Required
    public void setLoadIssueCommentByIdQuery(String loadIssueCommentByIdQuery) {
        this.loadIssueCommentByIdQuery = loadIssueCommentByIdQuery;
    }

    @Required
    public void setLoadAllCommentsForIssueQuery(String loadAllCommentsForIssueQuery) {
        this.loadAllCommentsForIssueQuery = loadAllCommentsForIssueQuery;
    }

    @Required
    public void setUpdateIssueCommentQuery(String updateIssueCommentQuery) {
        this.updateIssueCommentQuery = updateIssueCommentQuery;
    }

    @Required
    public void setDeleteIssueCommentQuery(String deleteIssueCommentQuery) {
        this.deleteIssueCommentQuery = deleteIssueCommentQuery;
    }

    @Required
    public void setDeleteAllCommentsForIssueQuery(String deleteAllCommentsForIssueQuery) {
        this.deleteAllCommentsForIssueQuery = deleteAllCommentsForIssueQuery;
    }

    @Required
    public  void setDeleteCommentsForIssuesListQuery(String deleteCommentsForIssuesListQuery) {
        this.deleteCommentsForIssuesListQuery = deleteCommentsForIssuesListQuery;
    }

    @Required
    public void setLoadAllCommentsForIssuesQuery(String loadAllCommentsForIssuesQuery) {
        this.loadAllCommentsForIssuesQuery = loadAllCommentsForIssuesQuery;
    }

    enum CommentParameters {
        COMMENT_ID,
        ISSUE_ID,
        COMMENT_TEXT,
        COMMENT_AUTHOR,
        CREATED_DATE,
        UPDATED_DATE;

        static MapSqlParameterSource getParameters(IssueComment comment) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(COMMENT_ID.name(), comment.getId());
            params.addValue(ISSUE_ID.name(), comment.getIssueId());
            params.addValue(COMMENT_TEXT.name(), comment.getText());
            params.addValue(COMMENT_AUTHOR.name(), comment.getAuthor());
            params.addValue(CREATED_DATE.name(), comment.getCreatedDate());
            params.addValue(UPDATED_DATE.name(), comment.getUpdatedDate());
            return params;
        }

        static RowMapper<IssueComment> getRowMapper() {
            return (rs, rowNum) -> parseParameters(rs);
        }


        private static IssueComment parseParameters(ResultSet rs)  throws SQLException {
            IssueComment comment = new IssueComment();
            comment.setId(rs.getLong(COMMENT_ID.name()));
            comment.setIssueId(rs.getLong(ISSUE_ID.name()));
            comment.setText(rs.getString(COMMENT_TEXT.name()));
            comment.setAuthor(rs.getString(COMMENT_AUTHOR.name()));
            comment.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
            comment.setUpdatedDate(new Date(rs.getTimestamp(UPDATED_DATE.name()).getTime()));
            return comment;
        }

    }
}
