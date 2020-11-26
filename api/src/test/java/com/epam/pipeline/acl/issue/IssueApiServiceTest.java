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

package com.epam.pipeline.acl.issue;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.issue.IssueManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.issue.IssueCreatorUtils;
import com.epam.pipeline.test.creator.security.SecurityCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class IssueApiServiceTest extends AbstractAclTest {

    private static final AclClass ENTITY_ACL_CLASS = AclClass.DATA_STORAGE;

    private final EntityVO entityVO = SecurityCreatorUtils.getEntityVO(ID, ENTITY_ACL_CLASS);
    private final Issue issue = IssueCreatorUtils.getIssue(entityVO, SIMPLE_USER);
    private final IssueVO issueVO = IssueCreatorUtils.getIssueVO(entityVO);
    private final AbstractSecuredEntity s3bucket = DatastorageCreatorUtils.getS3bucketDataStorage(ID, OWNER_USER);
    private final List<Issue> issueList = Collections.singletonList(issue);
    private final IssueComment issueComment = IssueCreatorUtils.getIssueComment(SIMPLE_USER);
    private final IssueCommentVO issueCommentVO = IssueCreatorUtils.getIssueCommentVO();

    @Autowired
    private IssueApiService issueApiService;

    @Autowired
    private IssueManager mockIssueManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private AuthManager mockAuthManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateIssueForAdmin() {
        doReturn(issue).when(mockIssueManager).createIssue(issueVO);

        assertThat(issueApiService.createIssue(issueVO)).isEqualTo(issue);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateIssueWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(issue).when(mockIssueManager).createIssue(issueVO);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThat(issueApiService.createIssue(issueVO)).isEqualTo(issue);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateIssueWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(issue).when(mockIssueManager).createIssue(issueVO);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> issueApiService.createIssue(issueVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadIssueForAdmin() {
        doReturn(issue).when(mockIssueManager).loadIssue(ID);

        assertThat(issueApiService.loadIssue(ID)).isEqualTo(issue);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadIssueWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThat(issueApiService.loadIssue(ID)).isEqualTo(issue);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadIssueWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> issueApiService.loadIssue(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadIssuesForEntityForAdmin() {
        doReturn(issueList).when(mockIssueManager).loadIssuesForEntity(entityVO);

        assertThat(issueApiService.loadIssuesForEntity(entityVO)).isEqualTo(issueList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadIssuesForEntityWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(issueList).when(mockIssueManager).loadIssuesForEntity(entityVO);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThat(issueApiService.loadIssuesForEntity(entityVO)).isEqualTo(issueList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadIssuesForEntityWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(issueList).when(mockIssueManager).loadIssuesForEntity(entityVO);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> issueApiService.loadIssuesForEntity(entityVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateIssueForAdmin() {
        doReturn(issue).when(mockIssueManager).updateIssue(ID, issueVO);

        assertThat(issueApiService.updateIssue(ID, issueVO)).isEqualTo(issue);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateIssueWhenPermissionIsGranted() {
        doReturn(issue).when(mockIssueManager).updateIssue(ID, issueVO);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(issueApiService.updateIssue(ID, issueVO)).isEqualTo(issue);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateIssueWhenPermissionIsNotGranted() {
        doReturn(issue).when(mockIssueManager).updateIssue(ID, issueVO);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);

        assertThrows(AccessDeniedException.class, () -> issueApiService.updateIssue(ID, issueVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteIssueForAdmin() {
        doReturn(issue).when(mockIssueManager).deleteIssue(ID);

        assertThat(issueApiService.deleteIssue(ID)).isEqualTo(issue);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteIssueWhenPermissionIsGranted() {
        doReturn(issue).when(mockIssueManager).deleteIssue(ID);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(issueApiService.deleteIssue(ID)).isEqualTo(issue);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteIssueWhenPermissionIsNotGranted() {
        doReturn(issue).when(mockIssueManager).deleteIssue(ID);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);

        assertThrows(AccessDeniedException.class, () -> issueApiService.deleteIssue(ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateCommentForAdmin() {
        doReturn(issueComment).when(mockIssueManager).createComment(ID, issueCommentVO);

        assertThat(issueApiService.createComment(ID, issueCommentVO)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateCommentWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(issueComment).when(mockIssueManager).createComment(ID, issueCommentVO);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThat(issueApiService.createComment(ID, issueCommentVO)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateCommentWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(issueComment).when(mockIssueManager).createComment(ID, issueCommentVO);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> issueApiService.createComment(ID, issueCommentVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadCommentForAdmin() {
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);

        assertThat(issueApiService.loadComment(ID, ID)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadCommentWhenPermissionIsGranted() {
        initAclEntity(s3bucket, AclPermission.READ);
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThat(issueApiService.loadComment(ID, ID)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadCommentWhenPermissionIsNotGranted() {
        initAclEntity(s3bucket);
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);
        doReturn(issue).when(mockIssueManager).loadIssue(ID);
        doReturn(s3bucket).when(mockEntityManager).load(ENTITY_ACL_CLASS, ID);
        mockSecurityContext();

        assertThrows(AccessDeniedException.class, () -> issueApiService.loadComment(ID, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateCommentForAdmin() {
        doReturn(issueComment).when(mockIssueManager).updateComment(ID, ID, issueCommentVO);

        assertThat(issueApiService.updateComment(ID, ID, issueCommentVO)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldUpdateCommentWhenPermissionIsGranted() {
        doReturn(issueComment).when(mockIssueManager).updateComment(ID, ID, issueCommentVO);
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(issueApiService.updateComment(ID, ID, issueCommentVO)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser
    public void shouldDenyUpdateCommentWhenPermissionIsNotGranted() {
        doReturn(issueComment).when(mockIssueManager).updateComment(ID, ID, issueCommentVO);
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);

        assertThrows(AccessDeniedException.class, () -> issueApiService.updateComment(ID, ID, issueCommentVO));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteCommentForAdmin() {
        doReturn(issueComment).when(mockIssueManager).deleteComment(ID, ID);

        assertThat(issueApiService.deleteComment(ID, ID)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteCommentWhenPermissionIsGranted() {
        doReturn(issueComment).when(mockIssueManager).deleteComment(ID, ID);
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);
        doReturn(SIMPLE_USER).when(mockAuthManager).getAuthorizedUser();

        assertThat(issueApiService.deleteComment(ID, ID)).isEqualTo(issueComment);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteCommentWhenPermissionIsNotGranted() {
        doReturn(issueComment).when(mockIssueManager).deleteComment(ID, ID);
        doReturn(issueComment).when(mockIssueManager).loadComment(ID, ID);

        assertThrows(AccessDeniedException.class, () -> issueApiService.deleteComment(ID, ID));
    }

    @Test
    @WithMockUser
    public void shouldLoadMyPagedResultListIssue() {
        final PagedResult<List<Issue>> pagedResult = IssueCreatorUtils.getPagedListIssue();
        doReturn(pagedResult).when(mockIssueManager).loadMy(ID, TEST_INT);
        assertThat(issueApiService.loadMy(ID, TEST_INT)).isEqualTo(pagedResult);
    }
}
