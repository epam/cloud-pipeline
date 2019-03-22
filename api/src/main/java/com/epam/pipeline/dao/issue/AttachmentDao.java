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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.entity.issue.Attachment;

public class AttachmentDao extends NamedParameterJdbcDaoSupport {
    private static final String LIST_PARAMETER = "list";

    private String attachmentSequenceName;

    private String createAttachmentQuery;
    private String loadAttachmentQuery;
    private String loadAttachmentByNameQuery;
    private String updateAttachmentIssueIdQuery;
    private String updateAttachmentCommentIdQuery;
    private String deleteAttachmentQuery;
    private String deleteAttachmentsQuery;
    private String loadAttachmentsByIssueIdQuery;
    private String loadAttachmentIdsByIssueIdsQuery;
    private String loadAttachmentsByCommentIdsQuery;
    private String deleteAttachmentsByIssueIdQuery;
    private String deleteAttachmentsByCommentIdsQuery;

    @Autowired
    private DaoHelper daoHelper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createAttachment(Attachment attachment) {
        attachment.setId(daoHelper.createId(attachmentSequenceName));
        getNamedParameterJdbcTemplate().update(createAttachmentQuery, AttachmentColumns.getParams(attachment));
    }

    public Optional<Attachment> load(long attachmentId) {
        List<Attachment> attachments = getJdbcTemplate().query(loadAttachmentQuery, AttachmentColumns.rowMapper,
                                                               attachmentId);
        if (attachments.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(attachments.get(0));
        }
    }

    public Optional<Attachment> loadByName(String name) {
        List<Attachment> attachments = getJdbcTemplate().query(loadAttachmentByNameQuery, AttachmentColumns.rowMapper,
                                                               name);
        if (attachments.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(attachments.get(0));
        }
    }

    public List<Attachment> loadAttachmentsByIssueId(long issueId) {
        return getJdbcTemplate().query(loadAttachmentsByIssueIdQuery, AttachmentColumns.rowMapper, issueId);
    }

    public List<Attachment> loadAttachmentsByIssueIds(List<Long> issueIds) {
        if (CollectionUtils.isEmpty(issueIds)) {
            return Collections.emptyList();
        }

        return getNamedParameterJdbcTemplate().query(loadAttachmentIdsByIssueIdsQuery,
                                                     new MapSqlParameterSource(LIST_PARAMETER, issueIds),
                                                     AttachmentColumns.rowMapper);
    }

    public List<Attachment> loadAttachmentsByCommentId(Long commentId) {
        return loadAttachmentsByCommentIds(Collections.singletonList(commentId))
            .getOrDefault(commentId, Collections.emptyList());
    }

    public Map<Long, List<Attachment>> loadAttachmentsByCommentIds(List<Long> commentIds) {
        if (CollectionUtils.isEmpty(commentIds)) {
            return Collections.emptyMap();
        }

        Map<Long, List<Attachment>> attachmentMap = new HashMap<>();
        getNamedParameterJdbcTemplate().query(loadAttachmentsByCommentIdsQuery,
                                              new MapSqlParameterSource(LIST_PARAMETER, commentIds), rs -> {
                long commentId = rs.getLong(AttachmentColumns.COMMENT_ID.name());
                attachmentMap.putIfAbsent(commentId, new ArrayList<>());
                attachmentMap.get(commentId).add(AttachmentColumns.rowMapper.mapRow(rs, 0));
            });
        return attachmentMap;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAttachmentIssueId(Long attachmentId, Long issueId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(AttachmentColumns.ATTACHMENT_ID.name(), attachmentId);
        params.addValue(AttachmentColumns.ISSUE_ID.name(), issueId);

        getNamedParameterJdbcTemplate().update(updateAttachmentIssueIdQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAttachmentCommentId(Long attachmentId, Long commentId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(AttachmentColumns.ATTACHMENT_ID.name(), attachmentId);
        params.addValue(AttachmentColumns.COMMENT_ID.name(), commentId);

        getNamedParameterJdbcTemplate().update(updateAttachmentCommentIdQuery, params);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAttachment(Long attachmentId) {
        getJdbcTemplate().update(deleteAttachmentQuery, attachmentId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAttachments(List<Long> attachmentIds) {
        if (CollectionUtils.isEmpty(attachmentIds)) {
            return;
        }

        getNamedParameterJdbcTemplate().update(deleteAttachmentsQuery,
                                               new MapSqlParameterSource(LIST_PARAMETER, attachmentIds));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAttachmentsByIssueId(Long issueId) {
        getJdbcTemplate().update(deleteAttachmentsByIssueIdQuery, issueId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAttachmentsByCommentIds(List<Long> commentIds) {
        if (CollectionUtils.isEmpty(commentIds)) {
            return;
        }

        getNamedParameterJdbcTemplate().update(deleteAttachmentsByCommentIdsQuery,
                                               new MapSqlParameterSource(LIST_PARAMETER, commentIds));
    }

    private enum AttachmentColumns {
        ATTACHMENT_ID,
        ATTACHMENT_NAME,
        ISSUE_ID,
        COMMENT_ID,
        PATH,
        CREATED_DATE,
        OWNER;

        private static MapSqlParameterSource getParams(Attachment attachment) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue(ATTACHMENT_ID.name(), attachment.getId());
            params.addValue(ATTACHMENT_NAME.name(), attachment.getName());
            params.addValue(PATH.name(), attachment.getPath());
            params.addValue(CREATED_DATE.name(), attachment.getCreatedDate());
            params.addValue(OWNER.name(), attachment.getOwner());

            return params;
        }

        private static RowMapper<Attachment> rowMapper = (rs, i) -> {
            Attachment attachment = new Attachment();
            attachment.setId(rs.getLong(ATTACHMENT_ID.name()));
            attachment.setName(rs.getString(ATTACHMENT_NAME.name()));
            attachment.setPath(rs.getString(PATH.name()));
            attachment.setCreatedDate(new Date(rs.getTimestamp(CREATED_DATE.name()).getTime()));
            attachment.setOwner(rs.getString(OWNER.name()));

            return attachment;
        };
    }

    @Required
    public void setCreateAttachmentQuery(String createAttachmentQuery) {
        this.createAttachmentQuery = createAttachmentQuery;
    }

    @Required
    public void setDeleteAttachmentQuery(String deleteAttachmentQuery) {
        this.deleteAttachmentQuery = deleteAttachmentQuery;
    }

    @Required
    public void setLoadAttachmentsByIssueIdQuery(String loadAttachmentsByIssueIdQuery) {
        this.loadAttachmentsByIssueIdQuery = loadAttachmentsByIssueIdQuery;
    }

    @Required
    public void setLoadAttachmentIdsByIssueIdsQuery(String loadAttachmentIdsByIssueIdsQuery) {
        this.loadAttachmentIdsByIssueIdsQuery = loadAttachmentIdsByIssueIdsQuery;
    }

    @Required
    public void setLoadAttachmentsByCommentIdsQuery(String loadAttachmentsByCommentIdsQuery) {
        this.loadAttachmentsByCommentIdsQuery = loadAttachmentsByCommentIdsQuery;
    }

    @Required
    public void setDeleteAttachmentsByCommentIdsQuery(String deleteAttachmentsByCommentIdsQuery) {
        this.deleteAttachmentsByCommentIdsQuery = deleteAttachmentsByCommentIdsQuery;
    }

    @Required
    public void setDeleteAttachmentsByIssueIdQuery(String deleteAttachmentsByIssueIdQuery) {
        this.deleteAttachmentsByIssueIdQuery = deleteAttachmentsByIssueIdQuery;
    }

    @Required
    public void setAttachmentSequenceName(String attachmentSequenceName) {
        this.attachmentSequenceName = attachmentSequenceName;
    }

    @Required
    public void setUpdateAttachmentIssueIdQuery(String updateAttachmentIssueIdQuery) {
        this.updateAttachmentIssueIdQuery = updateAttachmentIssueIdQuery;
    }

    @Required
    public void setUpdateAttachmentCommentIdQuery(String updateAttachmentCommentIdQuery) {
        this.updateAttachmentCommentIdQuery = updateAttachmentCommentIdQuery;
    }

    @Required
    public void setLoadAttachmentQuery(String loadAttachmentQuery) {
        this.loadAttachmentQuery = loadAttachmentQuery;
    }

    @Required
    public void setDeleteAttachmentsQuery(String deleteAttachmentsQuery) {
        this.deleteAttachmentsQuery = deleteAttachmentsQuery;
    }

    @Required
    public void setLoadAttachmentByNameQuery(String loadAttachmentByNameQuery) {
        this.loadAttachmentByNameQuery = loadAttachmentByNameQuery;
    }
}
