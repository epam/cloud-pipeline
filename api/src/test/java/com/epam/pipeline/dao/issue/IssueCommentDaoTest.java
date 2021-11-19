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

package com.epam.pipeline.dao.issue;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IssueCommentDaoTest extends AbstractJdbcTest {

    private static final Long ENTITY_ID = 1L;
    private static final AclClass ENTITY_CLASS = AclClass.PIPELINE;
    private static final String ISSUE_NAME = "Issue name";
    private static final String ISSUE_NAME1 = "Issue name1";
    private static final String ISSUE_AUTHOR = "Issue author";
    private static final String ISSUE_TEXT = "Issue text";
    private static final String COMMENT_AUTHOR = "Comment author";
    private static final String COMMENT_TEXT = "Comment text";
    private static final String COMMENT_TEXT2 = "Comment text2";
    private static final String COMMENT_TEXT3 = "Comment text3";

    @Autowired
    private IssueDao issueDao;
    @Autowired
    private IssueCommentDao commentDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCRUD() {
        Issue issue = createIssue(ISSUE_NAME);
        IssueComment comment = getComment(issue, COMMENT_TEXT);

        //create
        commentDao.createComment(comment);
        Long commentId = comment.getId();

        //load
        Optional<IssueComment> loaded = commentDao.loadComment(commentId);
        assertTrue(loaded.isPresent());
        verifyComment(comment, loaded.get());

        IssueComment comment2 = getComment(issue, COMMENT_TEXT2);
        commentDao.createComment(comment2);
        assertEquals(2, commentDao.loadCommentsForIssue(issue.getId()).size());

        // Load by multiple issue IDs
        Map<Long, List<IssueComment>> commentMap = commentDao.loadCommentsForIssues(
            Collections.singleton(issue.getId()));
        assertEquals(1, commentMap.size());
        assertEquals(2, commentMap.get(issue.getId()).size());

        //update
        comment.setText(COMMENT_TEXT3);
        commentDao.updateComment(comment);
        Optional<IssueComment> updated = commentDao.loadComment(commentId);
        assertTrue(updated.isPresent());
        assertEquals(COMMENT_TEXT3, updated.get().getText());

        //delete
        commentDao.deleteComment(comment.getId());
        Optional<IssueComment> deleted = commentDao.loadComment(commentId);
        assertFalse(deleted.isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteCommentsForIssuesList() {
        Issue issue = createIssueComment(ISSUE_NAME);
        Issue issue1 = createIssueComment(ISSUE_NAME1);
        List<Long> issuesToDelete = Stream.of(issue.getId(), issue1.getId()).collect(Collectors.toList());
        commentDao.deleteAllCommentsForIssuesList(issuesToDelete);
        assertEquals(0, commentDao.loadCommentsForIssue(issue.getId()).size());
        assertEquals(0, commentDao.loadCommentsForIssue(issue1.getId()).size());
    }

    private Issue createIssue(String name) {
        EntityVO entityVO = new EntityVO(ENTITY_ID, ENTITY_CLASS);
        Issue issue = new Issue();
        issue.setName(name);
        issue.setAuthor(ISSUE_AUTHOR);
        issue.setEntity(entityVO);
        issue.setText(ISSUE_TEXT);
        issueDao.createIssue(issue);
        return issue;
    }

    public static IssueComment getComment(Issue issue, String commentText) {
        IssueComment comment = new IssueComment();
        comment.setIssueId(issue.getId());
        comment.setAuthor(COMMENT_AUTHOR);
        comment.setText(commentText);
        return comment;
    }

    private void verifyComment(IssueComment expected, IssueComment actual) {
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getIssueId(), actual.getIssueId());
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getCreatedDate(), actual.getCreatedDate());
        assertEquals(expected.getUpdatedDate(), actual.getUpdatedDate());
    }

    private Issue createIssueComment(String issueName) {
        Issue issue = createIssue(issueName);
        IssueComment comment = getComment(issue, COMMENT_TEXT);
        commentDao.createComment(comment);
        return issue;
    }
}
