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

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.dao.issue.AttachmentDao;
import com.epam.pipeline.dao.issue.IssueCommentDao;
import com.epam.pipeline.dao.issue.IssueDao;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.notification.NotificationManager;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class IssueApiServiceTest extends AbstractManagerTest {

    private static final String USER_OWNER = "USER";
    private static final String NO_PERMISSION_USER = "NO_PERMISSION_USER";
    private static final String CAN_READ_USER = "CAN_READ";
    private static final String TEST_FOLDER_NAME = "folder";
    private static final String ISSUE_NAME = "Issue name";
    private static final String ISSUE_TEXT = "Issue text";
    private static final String COMMENT_TEXT = "Comment text";
    private static final String TEST_USER = "test";
    private static final String TEST_USER2 = "USER2";

    @Autowired
    private IssueApiService issueApiService;
    @Autowired
    private IssueDao issueDao;
    @Autowired
    private IssueCommentDao issueCommentDao;
    @Autowired
    private AclTestDao aclTestDao;
    @Autowired
    private FolderDao folderDao;
    @Autowired
    private AttachmentDao attachmentDao;


    @MockBean
    private NotificationManager notificationManager;
    @SpyBean
    private AttachmentFileManager attachmentFileManager;

    private Attachment attachment;
    private EntityVO entityVO;
    private Issue createdIssue;
    private IssueComment createdIssueComment;

    @BeforeEach
    public void setUp() {
        Folder folder = new Folder();
        folder.setName(TEST_FOLDER_NAME);
        folder.setOwner(USER_OWNER);
        folderDao.createFolder(folder);

        // Mock ACL
        AclTestDao.AclSid testUserSid = new AclTestDao.AclSid(true, USER_OWNER);
        aclTestDao.createAclSid(testUserSid);

        AclTestDao.AclClass folderAclClass = new AclTestDao.AclClass(Folder.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(folderAclClass);

        AclTestDao.AclObjectIdentity objectIdentity = new AclTestDao.AclObjectIdentity(testUserSid, folder.getId(),
                folderAclClass.getId(), null, true);
        aclTestDao.createObjectIdentity(objectIdentity);

        AclTestDao.AclEntry aclEntry = new AclTestDao.AclEntry(objectIdentity, 1, testUserSid,
                AclPermission.READ.getMask(), true);
        aclTestDao.createAclEntry(aclEntry);

        AclTestDao.AclSid noPermissionUserSid = new AclTestDao.AclSid(true, NO_PERMISSION_USER);
        aclTestDao.createAclSid(noPermissionUserSid);

        entityVO = new EntityVO(folder.getId(), AclClass.FOLDER);

        createdIssue = new Issue();
        createdIssue.setName(ISSUE_NAME);
        createdIssue.setText(ISSUE_TEXT);
        createdIssue.setAuthor(USER_OWNER);
        createdIssue.setEntity(entityVO);
        createdIssue.setStatus(IssueStatus.OPEN);
        issueDao.createIssue(createdIssue);

        createdIssueComment = new IssueComment();
        createdIssueComment.setIssueId(createdIssue.getId());
        createdIssueComment.setAuthor(USER_OWNER);
        createdIssueComment.setText(COMMENT_TEXT);
        issueCommentDao.createComment(createdIssueComment);

        AclTestDao.AclSid canReadUserSid = new AclTestDao.AclSid(true, CAN_READ_USER);
        aclTestDao.createAclSid(canReadUserSid);

        aclEntry = new AclTestDao.AclEntry(objectIdentity, 2, canReadUserSid,
                AclPermission.READ.getMask(), true);
        aclTestDao.createAclEntry(aclEntry);

        verify(notificationManager, Mockito.never()).notifyIssue(any(), any(), any());
        Mockito.doNothing().when(attachmentFileManager).deleteAttachments(Mockito.anyList());

        attachment = new Attachment();
        attachment.setName("testAttachment");
        attachment.setCreatedDate(new Date());
        attachment.setPath("///");
        attachment.setOwner(TEST_USER);

        attachmentDao.createAttachment(attachment);
        Mockito.doNothing().when(attachmentFileManager).deleteAttachments(Mockito.anyList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_OWNER)
    @Test
    public void testIssueOwnerCRUD() {
        Issue issue = issueApiService.createIssue(getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO));

        Issue loaded = issueApiService.loadIssue(issue.getId());
        assertNotNull(loaded);

        List<Issue> loadedIssues = issueApiService.loadIssuesForEntity(entityVO);
        assertNotNull(loadedIssues);

        issueApiService.updateIssue(issue.getId(), getIssueVO(ISSUE_NAME, ISSUE_TEXT + "!", entityVO));

        IssueCommentVO issueCommentVO = new IssueCommentVO();
        issueCommentVO.setText(COMMENT_TEXT);
        IssueComment comment = issueApiService.createComment(issue.getId(), issueCommentVO);

        IssueComment loadedComment = issueApiService.loadComment(issue.getId(), comment.getId());
        assertNotNull(loadedComment);

        issueCommentVO.setText(COMMENT_TEXT + "!");
        issueApiService.updateComment(issue.getId(), comment.getId(), issueCommentVO);
        issueApiService.deleteComment(issue.getId(), comment.getId());
        issueApiService.deleteIssue(issue.getId());
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testCreateIssueAccessDenied() {
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.createIssue(getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO)));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testLoadIssueAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> issueApiService.loadIssue(createdIssue.getId()));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testLoadIssuesAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> issueApiService.loadIssuesForEntity(entityVO));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testUpdateIssueAccessDenied() {
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.updateIssue(createdIssue.getId(), getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO)));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testDeleteIssueAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> issueApiService.deleteIssue(createdIssue.getId()));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testCreateCommentAccessDenied() {
        IssueCommentVO issueCommentVO = new IssueCommentVO();
        issueCommentVO.setText(COMMENT_TEXT);
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.createComment(createdIssue.getId(), issueCommentVO));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testLoadCommentAccessDenied() {
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.loadComment(createdIssue.getId(), createdIssueComment.getId()));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testUpdateCommentAccessDenied() {
        IssueCommentVO issueCommentVO = new IssueCommentVO();
        issueCommentVO.setText(COMMENT_TEXT);
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.updateComment(createdIssue.getId(), createdIssueComment.getId(), issueCommentVO));
    }

    @WithMockUser(username = NO_PERMISSION_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testDeleteCommentAccessDenied() {
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.deleteComment(createdIssue.getId(), createdIssueComment.getId()));
    }

    @WithMockUser(username = CAN_READ_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testUpdateIssueAccessDeniedForNotOwner() {
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.updateIssue(createdIssue.getId(), getIssueVO(ISSUE_NAME, ISSUE_TEXT, entityVO)));
    }

    @WithMockUser(username = CAN_READ_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testDeleteIssueAccessDeniedForNotOwner() {
        assertThrows(AccessDeniedException.class, () -> issueApiService.deleteIssue(createdIssue.getId()));
    }

    @WithMockUser(username = CAN_READ_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testUpdateCommentAccessDeniedForNotOwner() {
        IssueCommentVO issueCommentVO = new IssueCommentVO();
        issueCommentVO.setText(COMMENT_TEXT);
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.updateComment(createdIssue.getId(), createdIssueComment.getId(), issueCommentVO));
    }

    @WithMockUser(username = CAN_READ_USER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @Test
    public void testDeleteCommentAccessDeniedForNotOwner() {
        assertThrows(AccessDeniedException.class,
            () -> issueApiService.deleteComment(createdIssue.getId(), createdIssueComment.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = CAN_READ_USER)
    @Test
    public void testLoadIssueForNotOwner() {
        Issue loaded = issueApiService.loadIssue(createdIssue.getId());
        assertNotNull(loaded);

        List<Issue> loadedIssues = issueApiService.loadIssuesForEntity(entityVO);
        assertNotNull(loadedIssues);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = CAN_READ_USER)
    @Test
    public void testLoadCommentForNotOwner() {
        IssueComment loadedComment = issueApiService.loadComment(createdIssue.getId(), createdIssueComment.getId());
        assertNotNull(loadedComment);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    @WithMockUser(username = TEST_USER2)
    public void testDeleteAttachmentFail() {
        assertThrows(AccessDeniedException.class, () -> attachmentFileManager.deleteAttachment(attachment.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    @WithMockUser(username = TEST_USER)
    public void testDeleteAttachment() {
        attachmentFileManager.deleteAttachment(attachment.getId());
        verify(attachmentFileManager).deleteAttachment(attachment.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @WithMockUser(username = USER_OWNER)
    @Test
    public void testLoadMy() {
        PagedResult<List<Issue>> result = issueApiService.loadMy(1, 10);

        assertFalse(result.getElements().isEmpty());
        assertTrue(result.getElements().stream().anyMatch(i -> CollectionUtils.isNotEmpty(i.getComments())));
        assertEquals(result.getElements().size(), result.getTotalCount());
    }

    private IssueVO getIssueVO(String name, String text, EntityVO entity) {
        IssueVO issueVO = new IssueVO();
        issueVO.setName(name);
        issueVO.setEntity(entity);
        issueVO.setText(text);
        issueVO.setStatus(IssueStatus.OPEN);
        return issueVO;
    }
}
