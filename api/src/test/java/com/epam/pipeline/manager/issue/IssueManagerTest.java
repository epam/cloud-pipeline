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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Date;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.dao.issue.AttachmentDao;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.preference.Preference;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.mapper.IssueMapper;
import org.apache.commons.collections.CollectionUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class IssueManagerTest extends AbstractSpringTest {
    private static final String TEST_SYSTEM_DATA_STORAGE = "testStorage";

    private static final String FOLDER_NAME = "Folder";
    private static final String FOLDER_NAME_2 = "Folder2";
    private static final String ISSUE_NAME = "Issue name";
    private static final String ISSUE_NAME2 = "Issue name2";
    private static final String LABEL1 = "Label1";
    private static final String LABEL2 = "Label2";
    private static final String LABEL3 = "Label3";
    private static final String ISSUE_TEXT = "Issue text";
    private static final String ISSUE_TEXT2 = "Issue text2";
    private static final String COMMENT_TEXT = "Comment text";
    private static final String COMMENT_TEXT2 = "Comment text2";
    private static final String AUTHOR = "author";
    private static final int TIMEOUT = 500;
    private static EntityVO entityVO;

    @Autowired
    private IssueManager issueManager;
    @Autowired
    private IssueMapper issueMapper;
    @Autowired
    private FolderDao folderDao;
    @Autowired
    private FolderManager folderManager;

    @Autowired
    private AttachmentDao attachmentDao;
    @SpyBean
    private PreferenceManager preferenceManager;

    @SpyBean
    private AuthManager authManager;
    @MockBean
    private NotificationManager notificationManager;
    @MockBean
    private DataStorageManager dataStorageManager;

    private Attachment testAttachment;
    private S3bucketDataStorage testSystemDataStorage = new S3bucketDataStorage(1L, TEST_SYSTEM_DATA_STORAGE, "//");

    @Before
    public void setUp() throws Exception {
        Folder folder = new Folder();
        folder.setName(FOLDER_NAME);
        folder.setOwner(AUTHOR);
        folderDao.createFolder(folder);
        folderDao.loadFolder(folder.getId());
        entityVO = new EntityVO(folder.getId(), AclClass.FOLDER);

        testAttachment = new Attachment();
        testAttachment.setName("test");
        testAttachment.setPath("///");
        testAttachment.setCreatedDate(new Date());
        testAttachment.setOwner(AUTHOR);
        attachmentDao.createAttachment(testAttachment);

        Preference systemDataStorage = SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME.toPreference();
        systemDataStorage.setName(TEST_SYSTEM_DATA_STORAGE);
        when(preferenceManager.getPreference(SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME))
            .thenReturn(TEST_SYSTEM_DATA_STORAGE);

        when(dataStorageManager.loadByNameOrId(TEST_SYSTEM_DATA_STORAGE)).thenReturn(testSystemDataStorage);
        when(dataStorageManager.deleteDataStorageItems(any(), any(), any())).thenReturn(1);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateIssue() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        Issue saved = issueManager.createIssue(issueVO);
        Issue loaded = issueManager.loadIssue(saved.getId());
        compareIssues(saved, loaded);

        ArgumentCaptor<Issue> captor = ArgumentCaptor.forClass(Issue.class);
        ArgumentCaptor<String> htmlTextCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationManager).notifyIssue(captor.capture(), any(), htmlTextCaptor.capture());

        assertEquals(ISSUE_TEXT, captor.getValue().getText());
        assertEquals(ISSUE_TEXT, htmlTextCaptor.getValue());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void creatingIssueForNonExistentEntityShouldThrowException() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        Long nonExistentEntityId = 1L;
        EntityVO nonExistentEntity = new EntityVO(nonExistentEntityId, AclClass.PIPELINE);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, nonExistentEntity);
        issueManager.createIssue(issueVO);

        verify(notificationManager, Mockito.never()).notifyIssue(any(), any(), any());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void creatingIssueWithEmptyNameShouldThrowException() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO("", ISSUE_TEXT, entityVO);
        issueManager.createIssue(issueVO);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateIssue() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        Issue saved = issueManager.createIssue(issueVO);
        issueVO.setName(ISSUE_NAME2);
        issueVO.setText(ISSUE_TEXT2);
        issueVO.setStatus(IssueStatus.OPEN);
        issueVO.setLabels(Arrays.asList(LABEL1, LABEL2, LABEL3));
        Long issueId = saved.getId();
        issueManager.updateIssue(issueId, issueVO);
        Issue updated = issueMapper.toIssue(issueVO);
        updated.setAuthor(AUTHOR);
        compareIssues(issueManager.loadIssue(issueId), updated);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updatingClosedIssueShouldThrowException() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        Issue closedIssue = getClosedIssue();
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        issueVO.setLabels(Collections.singletonList(LABEL1));
        issueManager.updateIssue(closedIssue.getId(), issueVO);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteIssueWithComment() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        issueManager.createComment(issueId, commentVO);
        issueManager.deleteIssue(issueId);
        issueManager.loadIssue(issueId);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testCreateComment() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        IssueComment comment = issueManager.createComment(issueId, commentVO);
        IssueComment loaded = issueManager.loadComment(issueId, comment.getId());
        compareComments(comment, loaded);

        ArgumentCaptor<IssueComment> captor = ArgumentCaptor.forClass(IssueComment.class);
        ArgumentCaptor<String> htmlTextCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationManager).notifyIssueComment(captor.capture(), any(), htmlTextCaptor.capture());
        assertEquals(COMMENT_TEXT, captor.getValue().getText());
        assertEquals(COMMENT_TEXT, htmlTextCaptor.getValue());

    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void creatingCommentForNonExistentIssueShouldThrowException() {
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        Long nonExistentIssueId = 1L;
        issueManager.createComment(nonExistentIssueId, commentVO);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void creatingCommentForClosedIssueShouldThrowException() {
        Issue issue = getClosedIssue();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        issueManager.createComment(issue.getId(), commentVO);
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void creatingCommentWithEmptyTextShouldThrowException() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO("");
        issueManager.createComment(issueId, commentVO);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadIssueWithComments() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO1 = getCommentVO(COMMENT_TEXT);
        IssueCommentVO commentVO2 = getCommentVO(COMMENT_TEXT2);
        issueManager.createComment(issueId, commentVO1);
        issueManager.createComment(issueId, commentVO2);
        assertEquals(2, issueManager.loadIssue(issueId).getComments().size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testUpdateComment() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        IssueComment comment = issueManager.createComment(issueId, commentVO);
        IssueCommentVO updated = new IssueCommentVO();
        updated.setText(COMMENT_TEXT2);
        Long commentId = comment.getId();
        issueManager.updateComment(issueId, comment.getId(), updated);
        assertEquals(COMMENT_TEXT2, issueManager.loadComment(issueId, commentId).getText());
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteComment() {
        IssueComment comment = registerComment();
        Long issueId = comment.getIssueId();
        Long commentId = comment.getId();
        issueManager.deleteComment(issueId, commentId);
        issueManager.loadComment(issueId, commentId);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testLoadAndDeleteIssuesForEntity() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        issueVO.setAttachments(Collections.singletonList(testAttachment));
        Issue issue = issueManager.createIssue(issueVO);
        Issue issue2 = issueManager.createIssue(getIssueVO(ISSUE_NAME2, ISSUE_TEXT, entityVO));
        // load
        List<Issue> actualIssues = issueManager.loadIssuesForEntity(entityVO);
        assertEquals(2, actualIssues.size());
        List<Issue> expectedIssues = Stream.of(issue, issue2).collect(Collectors.toList());
        Map<Long, Issue> expectedMap = expectedIssues.stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));
        actualIssues.forEach(i -> compareIssues(expectedMap.get(i.getId()), i));
        // delete
        issueManager.deleteIssuesForEntity(entityVO);
        actualIssues = issueManager.loadIssuesForEntity(entityVO);
        assertEquals(0, actualIssues.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteIssuesForNonExistingEntity() {
        List<Issue> issues = issueManager.deleteIssuesForEntity(new EntityVO(1L, AclClass.FOLDER));
        assertTrue(CollectionUtils.isEmpty(issues));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void testDeleteIssuesWhenEntityWasDeleted() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        Folder folder = new Folder();
        folder.setName(FOLDER_NAME_2);
        folder.setOwner(AUTHOR);
        folderDao.createFolder(folder);
        EntityVO entityVO = new EntityVO(folder.getId(), AclClass.FOLDER);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        issueManager.createIssue(issueVO);
        folderManager.delete(folder.getId());
        List<Issue> issues = issueManager.loadIssuesForEntity(entityVO);
        assertTrue(CollectionUtils.isEmpty(issues));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void createIssueWithAttachments() throws InterruptedException {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        issueVO.setAttachments(Collections.singletonList(testAttachment));

        Issue saved = issueManager.createIssue(issueVO);
        Issue loaded = issueManager.loadIssue(saved.getId());

        assertFalse(loaded.getAttachments().isEmpty());

        issueManager.deleteIssue(loaded.getId());
        assertFalse(attachmentDao.load(testAttachment.getId()).isPresent());

        Thread.sleep(10);
        verify(dataStorageManager).deleteDataStorageItems(Mockito.eq(testSystemDataStorage.getId()), Mockito.anyList(),
                                                          Mockito.anyBoolean());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void createCommentWithAttachments() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        commentVO.setAttachments(Collections.singletonList(testAttachment));
        IssueComment comment = issueManager.createComment(issueId, commentVO);
        IssueComment loaded = issueManager.loadComment(issueId, comment.getId());

        Issue loadedIssue = issueManager.loadIssue(issueId);
        assertEquals(1, loadedIssue.getComments().stream().mapToLong(c -> c.getAttachments().size()).sum());

        assertFalse(loaded.getAttachments().isEmpty());

        issueManager.deleteIssue(issueId);
        assertFalse(attachmentDao.load(testAttachment.getId()).isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void updateIssueWithAttachments() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        issueVO.setStatus(IssueStatus.OPEN);
        issueVO.setAttachments(Collections.singletonList(testAttachment));

        Issue saved = issueManager.createIssue(issueVO);

        Attachment newAttachment = new Attachment();
        newAttachment.setPath("///");
        newAttachment.setName("newTestAttachment");
        newAttachment.setCreatedDate(new Date());
        newAttachment.setOwner(AUTHOR);
        attachmentDao.createAttachment(newAttachment);

        issueVO.setAttachments(Collections.singletonList(newAttachment));

        issueManager.updateIssue(saved.getId(), issueVO);

        Issue loaded = issueManager.loadIssue(saved.getId());
        assertEquals(1, loaded.getAttachments().size());
        assertTrue(loaded.getAttachments().stream().allMatch(a -> a.getName().equals(newAttachment.getName())));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateCommentWithAttachments() throws InterruptedException {
        Attachment newAttachment = new Attachment();
        newAttachment.setPath("///");
        newAttachment.setName("newTestAttachment");
        newAttachment.setCreatedDate(new Date());
        newAttachment.setOwner(AUTHOR);
        attachmentDao.createAttachment(newAttachment);

        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        IssueComment comment = issueManager.createComment(issueId, commentVO);
        IssueCommentVO updated = new IssueCommentVO();
        updated.setText(COMMENT_TEXT2);
        updated.setAttachments(Collections.singletonList(newAttachment));

        Long commentId = comment.getId();
        issueManager.updateComment(issueId, comment.getId(), updated);
        IssueComment loaded = issueManager.loadComment(issueId, commentId);

        assertEquals(1, loaded.getAttachments().size());
        assertTrue(loaded.getAttachments().stream().allMatch(a -> a.getName().equals(newAttachment.getName())));

        issueManager.deleteComment(issueId, commentId);
        assertFalse(attachmentDao.load(newAttachment.getId()).isPresent());

        Thread.sleep(TIMEOUT);
        verify(dataStorageManager, Mockito.times(2)).deleteDataStorageItems(Mockito.eq(testSystemDataStorage.getId()),
                                                                            Mockito.anyList(), Mockito.anyBoolean());
    }

    private IssueCommentVO getCommentVO(String text) {
        IssueCommentVO commentVO = new IssueCommentVO();
        commentVO.setText(text);
        return commentVO;
    }

    private IssueVO getIssueVO(String name, String text, EntityVO entity) {
        IssueVO issueVO = new IssueVO();
        issueVO.setName(name);
        issueVO.setEntity(entity);
        issueVO.setText(text);
        issueVO.setLabels(Arrays.asList(LABEL1, LABEL2));
        return issueVO;
    }

    private Issue getClosedIssue() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        Issue issue = issueManager.createIssue(issueVO);
        issueVO.setStatus(IssueStatus.CLOSED);
        return issueManager.updateIssue(issue.getId(), issueVO);
    }

    private Issue registerIssue() {
        when(authManager.getAuthorizedUser()).thenReturn(AUTHOR);
        IssueVO issueVO = getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO);
        return issueManager.createIssue(issueVO);
    }

    private IssueComment registerComment() {
        Issue issue = registerIssue();
        Long issueId = issue.getId();
        IssueCommentVO commentVO = getCommentVO(COMMENT_TEXT);
        return issueManager.createComment(issueId, commentVO);
    }

    private void compareComments(IssueComment expected, IssueComment actual) {
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getIssueId(), actual.getIssueId());
    }

    private void compareIssues(Issue expected, Issue actual) {
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getEntity().getEntityId(), actual.getEntity().getEntityId());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getLabels(), actual.getLabels());
        assertEquals(expected.getStatus(), actual.getStatus());
    }
}
