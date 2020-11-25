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

package com.epam.pipeline.controller.issue;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.issue.AttachmentFileManager;
import com.epam.pipeline.manager.issue.IssueApiService;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.issue.IssueCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = IssueController.class)
public class IssueControllerTest extends AbstractControllerTest {

    private static final String ISSUES_URL = SERVLET_PATH + "/issues";
    private static final String MY_ISSUES_URL = ISSUES_URL + "/my";
    private static final String ISSUES_ID_URL = ISSUES_URL + "/%d";
    private static final String ISSUES_COMMENTS_URL = ISSUES_ID_URL + "/comments";
    private static final String ISSUES_COMMENTS_ID_URL = ISSUES_COMMENTS_URL + "/%d";
    private static final String ATTACHMENTS_URL = SERVLET_PATH + "/attachment";
    private static final String ATTACHMENTS_ID_URL = ATTACHMENTS_URL + "/%d";


    private static final String ENTITY_ID = "entityId";
    private static final String ENTITY_CLASS = "entityClass";
    private static final String PAGE = "page";
    private static final String PAGE_SIZE = "pageSize";
    private static final String FILE = "file.txt";
    private static final String PATH = "path";

    private final Issue issue = IssueCreatorUtils.getIssue();
    private final IssueVO issueVO = IssueCreatorUtils.getIssueVO();
    private final IssueComment issueComment = IssueCreatorUtils.getIssueComment();
    private final IssueCommentVO issueCommentVO = IssueCreatorUtils.getIssueCommentVO();
    private final Attachment attachment = IssueCreatorUtils.getAttachment();

    @Autowired
    private IssueApiService mockIssueApiService;

    @Autowired
    private AttachmentFileManager mockAttachmentFileManager;


    @Test
    @WithMockUser
    public void shouldCreateIssue() throws Exception {
        final String content = getObjectMapper().writeValueAsString(issueVO);
        doReturn(issue).when(mockIssueApiService).createIssue(issueVO);

        final MvcResult mvcResult = performRequest(post(ISSUES_URL).content(content));

        verify(mockIssueApiService).createIssue(issueVO);
        assertResponse(mvcResult, issue, IssueCreatorUtils.ISSUE_TYPE);
    }

    @Test
    public void shouldFailCreateIssue() {
        performUnauthorizedRequest(post(ISSUES_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadIssue() {
        doReturn(issue).when(mockIssueApiService).loadIssue(ID);

        final MvcResult mvcResult = performRequest(get(String.format(ISSUES_ID_URL, ID)));

        verify(mockIssueApiService).loadIssue(ID);
        assertResponse(mvcResult, issue, IssueCreatorUtils.ISSUE_TYPE);
    }

    @Test
    public void shouldFailLoadIssue() {
        performUnauthorizedRequest(get(String.format(ISSUES_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadIssues() {
        final List<Issue> issues = Collections.singletonList(issue);
        final EntityVO entityVO = new EntityVO(ID, AclClass.DATA_STORAGE);
        doReturn(issues).when(mockIssueApiService).loadIssuesForEntity(entityVO);

        final MvcResult mvcResult = performRequest(get(ISSUES_URL)
                .params(multiValueMapOf(ENTITY_ID, ID, ENTITY_CLASS,
                                        AclClass.DATA_STORAGE)));

        verify(mockIssueApiService).loadIssuesForEntity(entityVO);
        assertResponse(mvcResult, issues, IssueCreatorUtils.ISSUE_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadIssues() {
        performUnauthorizedRequest(get(ISSUES_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateIssue() throws Exception {
        final String content = getObjectMapper().writeValueAsString(issueVO);
        doReturn(issue).when(mockIssueApiService).updateIssue(ID, issueVO);

        final MvcResult mvcResult = performRequest(put(String.format(ISSUES_ID_URL, ID)).content(content));

        verify(mockIssueApiService).updateIssue(ID, issueVO);
        assertResponse(mvcResult, issue, IssueCreatorUtils.ISSUE_TYPE);
    }

    @Test
    public void shouldFailUpdateIssue() {
        performUnauthorizedRequest(put(String.format(ISSUES_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteIssue() {
        doReturn(issue).when(mockIssueApiService).deleteIssue(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(ISSUES_ID_URL, ID)));

        verify(mockIssueApiService).deleteIssue(ID);
        assertResponse(mvcResult, issue, IssueCreatorUtils.ISSUE_TYPE);
    }

    @Test
    public void shouldFailDeleteIssue() {
        performUnauthorizedRequest(delete(String.format(ISSUES_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateIssueComment() throws Exception {
        final String content = getObjectMapper().writeValueAsString(issueCommentVO);
        doReturn(issueComment).when(mockIssueApiService).createComment(ID, issueCommentVO);

        final MvcResult mvcResult = performRequest(post(String.format(ISSUES_COMMENTS_URL, ID)).content(content));

        verify(mockIssueApiService).createComment(ID, issueCommentVO);
        assertResponse(mvcResult, issueComment, IssueCreatorUtils.ISSUE_COMMENT_TYPE);
    }

    @Test
    public void shouldFailCreateIssueComment() {
        performUnauthorizedRequest(post(String.format(ISSUES_COMMENTS_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadIssueComment() {
        doReturn(issueComment).when(mockIssueApiService).loadComment(ID, ID);

        final MvcResult mvcResult = performRequest(get(String.format(ISSUES_COMMENTS_ID_URL, ID, ID)));

        verify(mockIssueApiService).loadComment(ID, ID);
        assertResponse(mvcResult, issueComment, IssueCreatorUtils.ISSUE_COMMENT_TYPE);
    }

    @Test
    public void shouldFailLoadIssueComment() {
        performUnauthorizedRequest(get(String.format(ISSUES_COMMENTS_ID_URL, ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateIssueComment() throws Exception {
        final String content = getObjectMapper().writeValueAsString(issueCommentVO);
        doReturn(issueComment).when(mockIssueApiService).updateComment(ID, ID, issueCommentVO);

        final MvcResult mvcResult = performRequest(put(String.format(ISSUES_COMMENTS_ID_URL, ID, ID)).content(content));

        verify(mockIssueApiService).updateComment(ID, ID, issueCommentVO);
        assertResponse(mvcResult, issueComment, IssueCreatorUtils.ISSUE_COMMENT_TYPE);
    }

    @Test
    public void shouldFailUpdateIssueComment() {
        performUnauthorizedRequest(put(String.format(ISSUES_COMMENTS_ID_URL, ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteIssueComment() {
        doReturn(issueComment).when(mockIssueApiService).deleteComment(ID, ID);

        final MvcResult mvcResult = performRequest(delete(String.format(ISSUES_COMMENTS_ID_URL, ID, ID)));

        verify(mockIssueApiService).deleteComment(ID, ID);
        assertResponse(mvcResult, issueComment, IssueCreatorUtils.ISSUE_COMMENT_TYPE);
    }

    @Test
    public void shouldFailDeleteIssueComment() {
        performUnauthorizedRequest(get(String.format(ISSUES_COMMENTS_ID_URL, ID, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUploadAttachments() {
        final List<Attachment> attachments = Collections.singletonList(attachment);
        doReturn(attachment).when(mockAttachmentFileManager).uploadAttachment(any(), eq(FILE));

        final MvcResult mvcResult = performRequest(post(ATTACHMENTS_URL).content(MULTIPART_CONTENT).param(PATH, FILE),
                MULTIPART_CONTENT_TYPE, EXPECTED_CONTENT_TYPE);

        verify(mockAttachmentFileManager).uploadAttachment(any(), eq(FILE));
        assertResponse(mvcResult, attachments, IssueCreatorUtils.ATTACHMENT_LIST_TYPE);
    }

    @Test
    public void shouldFailUploadAttachments() {
        performUnauthorizedRequest(post(ATTACHMENTS_URL));
    }

    @Test
    @WithMockUser
    public void shouldDownloadAttachment() throws Exception {
        final DataStorageStreamingContent content = DatastorageCreatorUtils.getDataStorageStreamingContent();
        doReturn(content).when(mockAttachmentFileManager).downloadAttachment(ID);

        final MvcResult mvcResult = performRequest(get(String.format(ATTACHMENTS_ID_URL, ID)),
                APPLICATION_OCTET_STREAM_VALUE);

        verify(mockAttachmentFileManager).downloadAttachment(ID);
        Assert.assertEquals(TEST_STRING, mvcResult.getResponse().getContentAsString());
    }

    @Test
    public void shouldFailDownloadAttachment() {
        performUnauthorizedRequest(get(String.format(ATTACHMENTS_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAttachment() {
        doNothing().when(mockAttachmentFileManager).deleteAttachment(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(ATTACHMENTS_ID_URL, ID)));

        verify(mockAttachmentFileManager).deleteAttachment(ID);
        assertResponse(mvcResult, true, CommonCreatorConstants.BOOLEAN_INSTANCE_TYPE);
    }

    @Test
    public void shouldFailDeleteAttachment() {
        performUnauthorizedRequest(delete(String.format(ATTACHMENTS_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadMyIssue() {
        final PagedResult<List<Issue>> pagedResult = IssueCreatorUtils.getPagedResult();
        doReturn(pagedResult).when(mockIssueApiService).loadMy(ID, TEST_INT);

        final MvcResult mvcResult = performRequest(get(MY_ISSUES_URL)
                .params(multiValueMapOf(PAGE, ID,
                                        PAGE_SIZE, TEST_INT)),
                EXPECTED_CONTENT_TYPE);

        verify(mockIssueApiService).loadMy(ID, TEST_INT);
        assertResponse(mvcResult, pagedResult, IssueCreatorUtils.PAGED_RESULT_LIST_ISSUE_TYPE);
    }

    @Test
    public void shouldFailLoadMyIssue() {
        performUnauthorizedRequest(get(MY_ISSUES_URL));
    }
}
