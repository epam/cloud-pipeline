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

package com.epam.pipeline.manager.issue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.dao.issue.AttachmentDao;
import com.epam.pipeline.dao.issue.IssueCommentDao;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.IssueMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * {@link IssueManager} provides methods for manipulating issues and issues comments.
 */
@Service
public class IssueManager {

    @Autowired
    private IssueDao issueDao;

    @Autowired
    private IssueCommentDao commentDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private IssueMapper issueMapper;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NotificationManager notificationManager;

    @Autowired
    private AttachmentDao attachmentDao;

    @Autowired
    private AttachmentFileManager attachmentFileManager;

    /**
     * Creates a new issue that refers to existing {@link com.epam.pipeline.entity.AbstractSecuredEntity}.
     * If {@link com.epam.pipeline.entity.AbstractSecuredEntity} doesn't exist an error will be occurred.
     * @param issueVO {@link IssueVO} to create
     * @return create {@link Issue}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Issue createIssue(IssueVO issueVO) {
        validateIssueParameters(issueVO);

        EntityVO entityVO = issueVO.getEntity();
        validateEntityParameters(entityVO);
        AbstractSecuredEntity entity = ensureEntityExists(entityVO);

        Issue issue = issueMapper.toIssue(issueVO);
        issue.setAuthor(authManager.getAuthorizedUser());
        issueDao.createIssue(issue);

        issueVO.getAttachments().forEach(a -> attachmentDao.updateAttachmentIssueId(a.getId(), issue.getId()));

        notificationManager.notifyIssue(issue, entity,
                                        StringUtils.defaultIfBlank(issueVO.getHtmlText(), issue.getText()));

        return issue;
    }

    /**
     * Loads an {@link Issue} specified by ID. If issue doesn't exist exception will be thrown.
     * @param id issue's ID
     * @return issue with comments that belong to it
     */
    public Issue loadIssue(Long id) {
        Issue issue = issueDao.loadIssue(id).orElseThrow(getIssueNotFoundException(id));
        issue.setComments(commentDao.loadCommentsForIssue(id));

        issue.setAttachments(attachmentDao.loadAttachmentsByIssueId(id));
        Map<Long, List<Attachment>> attachmentMap = attachmentDao.loadAttachmentsByCommentIds(
            issue.getComments().stream().map(IssueComment::getId).collect(Collectors.toList()));
        issue.getComments().forEach(c -> c.setAttachments(attachmentMap.get(c.getId())));

        return issue;
    }

    /**
     * Loads Issues with nested comments for current user.
     * @param page page number to load
     * @param pageSize number of issues per page
     * @return a {@link PagedResult} of list of Issues
     */
    public PagedResult<List<Issue>> loadMy(Long page, Integer pageSize) {
        String currentUser = authManager.getAuthorizedUser();

        List<Issue> issues = issueDao.loadIssuesByAuthor(currentUser, page, pageSize);
        Map<Long, List<IssueComment>> commentsMap = commentDao.loadCommentsForIssues(
            issues.stream()
                .map(Issue::getId)
                .collect(Collectors.toList())
        );
        issues.forEach(i -> i.setComments(commentsMap.get(i.getId())));

        return new PagedResult<>(issues, issueDao.countIssuesByAuthor(currentUser));
    }

    /**
     * Loads all issues for specified entity.
     * @param entityVO target {@link EntityVO}
     * @return list of existing issues
     */
    public List<Issue> loadIssuesForEntity(EntityVO entityVO) {
        validateEntityParameters(entityVO);
        return issueDao.loadIssuesForEntity(entityVO);
    }

    /**
     * Updates an {@link Issue} specified by ID. If issue was closed or doesn't exist an error will be occurred.
     * @param issueId issue ID
     * @param issueVO issue content to update
     * @return updated issue
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Issue updateIssue(Long issueId, IssueVO issueVO) {
        validateIssueParameters(issueVO);
        Issue existingIssue = loadIssueAndCheckIfNotClosed(issueId);
        Issue issue = updateIssueObject(existingIssue, issueVO);
        issueDao.updateIssue(issue);

        // Find the attachments that were removed during update and delete them
        HashSet<Attachment> newAttachments = new HashSet<>(issueVO.getAttachments());
        List<Attachment> allAttachments = attachmentDao.loadAttachmentsByIssueId(issueId);
        List<Attachment> toDelete = allAttachments.stream().filter(a -> !newAttachments.contains(a))
            .collect(Collectors.toList());
        attachmentFileManager.deleteAttachments(toDelete);

        // Link new attachments to this issue
        issueVO.getAttachments().forEach(a -> attachmentDao.updateAttachmentIssueId(a.getId(), issueId));

        return issue;
    }

    /**
     * Deletes an {@link Issue} specified by ID. If issue doesn't exist an error will be occurred.
     * @param issueId issue ID
     * @return deleted issue
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Issue deleteIssue(Long issueId) {
        Optional<Issue> issue = issueDao.loadIssue(issueId);
        issue.ifPresent(i -> {
            List<Long> commentIds = commentDao.loadCommentsForIssue(issueId)
                .stream()
                .map(IssueComment::getId)
                .collect(Collectors.toList());

            List<Attachment> attachments = attachmentDao.loadAttachmentsByCommentIds(commentIds).entrySet()
                .stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
            attachments.addAll(attachmentDao.loadAttachmentsByIssueId(issueId));
            attachmentFileManager.deleteAttachments(attachments);

            commentDao.deleteAllCommentsForIssue(issueId);
            issueDao.deleteIssue(issueId);
        });

        return issue.orElseThrow(getIssueNotFoundException(issueId));
    }

    /**
     * Deletes all issues for specified entity. Note: this method doesn't throw if entity doesn't exist.
     * @param entity target entity
     * @return list of issues that were deleted
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public List<Issue> deleteIssuesForEntity(EntityVO entity) {
        validateEntityParameters(entity);
        List<Issue> issuesToDelete = issueDao.loadIssuesForEntity(entity);
        if (CollectionUtils.isNotEmpty(issuesToDelete)) {
            List<Long> issuesIds = issuesToDelete.stream().map(Issue::getId).collect(Collectors.toList());

            List<Long> commentIds = commentDao.loadCommentsForIssues(issuesIds).values().stream()
                .flatMap(l -> l.stream().map(c -> c.getId()))
                .collect(Collectors.toList());

            List<Attachment> attachments = attachmentDao.loadAttachmentsByCommentIds(commentIds).entrySet()
                .stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
            attachments.addAll(attachmentDao.loadAttachmentsByIssueIds(issuesIds));
            attachmentFileManager.deleteAttachments(attachments);

            commentDao.deleteAllCommentsForIssuesList(issuesIds);
            issueDao.deleteIssuesForEntity(entity);
        }
        return issuesToDelete;
    }

    /**
     * Creates comment for issue. If issue was closed or doesn't exist an error will be occurred.
     * @param issueId issue's ID
     * @param commentVO comment text
     * @return created {@link IssueComment}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public IssueComment createComment(Long issueId, IssueCommentVO commentVO) {
        Issue issue = loadIssueAndCheckIfNotClosed(issueId);
        validateComment(commentVO);
        IssueComment comment = issueMapper.toIssueComment(commentVO);
        comment.setIssueId(issueId);
        comment.setAuthor(authManager.getAuthorizedUser());
        commentDao.createComment(comment);

        commentVO.getAttachments().forEach(a -> attachmentDao.updateAttachmentCommentId(a.getId(), comment.getId()));

        notificationManager.notifyIssueComment(comment, issue, StringUtils.defaultIfBlank(commentVO.getHtmlText(),
                                                                                          comment.getText()));
        return comment;
    }

    /**
     * Loads comment specified by ID. If comment doesn't exist an error will be thrown. If {@code issueId} and
     * loaded {@link IssueComment#id} are not equal an error will be occurred.
     * @param issueId issue's ID
     * @param commentId comment's ID
     * @return existing {@link IssueComment}
     */
    public IssueComment loadComment(Long issueId, Long commentId) {
        IssueComment comment = commentDao.loadComment(commentId).orElseThrow(getCommentNotFoundException(commentId));
        comment.setAttachments(attachmentDao.loadAttachmentsByCommentIds(Collections.singletonList(commentId))
                                   .get(commentId));
        validateIssueId(issueId, comment);
        return comment;
    }

    /**
     * Updates comment specified by ID. If issue was closed or issue doesn't exist or comment doesn't exist an error
     * will be thrown. If text for comment is empty exception will be thrown.
     * @param issueId issue's ID
     * @param commentId comment's ID
     * @param commentVO comment's text
     * @return updated {@link IssueComment}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public IssueComment updateComment(Long issueId, Long commentId, IssueCommentVO commentVO) {
        IssueComment issueComment = loadComment(issueId, commentId);
        loadIssueAndCheckIfNotClosed(issueId);
        String text = commentVO.getText();
        ensureNotEmptyString(text, MessageConstants.ERROR_INVALID_COMMENT_TEXT);
        issueComment.setText(text);
        commentDao.updateComment(issueComment);

        // Find the attachments that were removed during update and delete them
        HashSet<Attachment> newAttachments = new HashSet<>(commentVO.getAttachments());
        List<Attachment> allAttachments = attachmentDao.loadAttachmentsByCommentId(commentId);
        List<Attachment> toDelete = allAttachments.stream().filter(a -> !newAttachments.contains(a))
            .collect(Collectors.toList());
        attachmentFileManager.deleteAttachments(toDelete);

        // New attachments might be added so we need to link them to this comment
        commentVO.getAttachments().forEach(a -> attachmentDao.updateAttachmentCommentId(a.getId(), commentId));

        return issueComment;
    }

    /**
     * Deletes comment specified by ID. If issue was closed or issue doesn't exist or comment doesn't exist an error
     * will be thrown.
     * @param issueId issue's ID
     * @param commentId comment's ID
     * @return deleted {@link IssueComment}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public IssueComment deleteComment(Long issueId, Long commentId) {
        loadIssueAndCheckIfNotClosed(issueId);
        Optional<IssueComment> comment = commentDao.loadComment(commentId);
        comment.ifPresent(loadedComment -> {
            validateIssueId(issueId, loadedComment);
            attachmentFileManager.deleteAttachments(attachmentDao.loadAttachmentsByCommentIds(
                Collections.singletonList(commentId)).getOrDefault(commentId, Collections.emptyList()));
            commentDao.deleteComment(commentId);
        });
        return comment.orElseThrow(getCommentNotFoundException(commentId));
    }

    private Issue updateIssueObject(Issue issue, IssueVO issueVO) {
        String name = issueVO.getName();
        ensureNotEmptyString(name, MessageConstants.ERROR_INVALID_ISSUE_NAME);
        String text = issueVO.getText();
        ensureNotEmptyString(text, MessageConstants.ERROR_INVALID_ISSUE_TEXT);
        IssueStatus status = issueVO.getStatus();
        Assert.notNull(status, messageHelper.getMessage(MessageConstants.ERROR_INVALID_ISSUE_STATUS));
        issue.setName(name);
        issue.setText(text);
        issue.setStatus(status);
        issue.setLabels(issueVO.getLabels());
        return issue;
    }

    private void validateIssueParameters(IssueVO issueVO) {
        Assert.notNull(issueVO, messageHelper.getMessage(MessageConstants.ERROR_INVALID_ISSUE_PARAMETERS));
        ensureNotEmptyString(issueVO.getName(), MessageConstants.ERROR_INVALID_ISSUE_NAME);
        ensureNotEmptyString(issueVO.getText(), MessageConstants.ERROR_INVALID_ISSUE_TEXT);
    }

    private Issue loadIssueAndCheckIfNotClosed(Long issueId) {
        Issue issue = issueDao.loadIssue(issueId).orElseThrow(getIssueNotFoundException(issueId));
        Assert.isTrue(!issue.getStatus().equals(IssueStatus.CLOSED),
                messageHelper.getMessage(MessageConstants.ERROR_ISSUE_STATUS_IS_CLOSED));
        return issue;
    }

    private void validateIssueId(Long givenIssueId, IssueComment comment) {
        Long issueId = comment.getIssueId();
        Assert.isTrue(givenIssueId.equals(issueId),
                messageHelper.getMessage(
                        MessageConstants.ERROR_WRONG_ISSUE_ID_OR_COMMENT_ID, givenIssueId, comment.getId()));
    }

    private void validateComment(IssueCommentVO commentVO) {
        ensureNotEmptyString(commentVO.getText(), MessageConstants.ERROR_INVALID_COMMENT_TEXT);
    }

    private void validateEntityParameters(EntityVO entityVO) {
        Assert.notNull(entityVO, messageHelper.getMessage(MessageConstants.ERROR_INVALID_ISSUE_ENTITY_PARAMETERS));
        Long entityId = entityVO.getEntityId();
        AclClass entityClass = entityVO.getEntityClass();
        Assert.notNull(entityId, messageHelper.getMessage(MessageConstants.ERROR_INVALID_ISSUE_ENTITY_ID));
        Assert.notNull(entityClass, messageHelper.getMessage(MessageConstants.ERROR_INVALID_ISSUE_ENTITY_CLASS));
    }

    private AbstractSecuredEntity ensureEntityExists(EntityVO entityVO) {
        AbstractSecuredEntity entity = entityManager.load(entityVO.getEntityClass(), entityVO.getEntityId());
        Assert.notNull(entity, messageHelper.getMessage(MessageConstants.ERROR_ENTITY_NOT_FOUND, entityVO.getEntityId(),
                entityVO.getEntityClass()));
        return entity;
    }

    private void ensureNotEmptyString(String text, String errorMessage) {
        Assert.isTrue(StringUtils.isNotEmpty(text), messageHelper.getMessage(errorMessage));
    }

    private Supplier<IllegalArgumentException> getIssueNotFoundException(Long issueId) {
        return () -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_ISSUE_NOT_FOUND, issueId));
    }

    private Supplier<IllegalArgumentException> getCommentNotFoundException(Long commentId) {
        return () -> new IllegalArgumentException(
                messageHelper.getMessage(MessageConstants.ERROR_COMMENT_NOT_FOUND, commentId));
    }
}
