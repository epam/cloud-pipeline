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

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.security.acl.AclExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IssueApiService {

    @Autowired
    private IssueManager issueManager;

    @PreAuthorize(AclExpressions.ISSUE_CREATE)
    public Issue createIssue(IssueVO issueVO) {
        return issueManager.createIssue(issueVO);
    }

    @PostAuthorize(AclExpressions.ISSUE_READ)
    public Issue loadIssue(Long issueId) {
        return issueManager.loadIssue(issueId);
    }

    public PagedResult<List<Issue>> loadMy(long page, Integer pageSize) {
        return issueManager.loadMy(page, pageSize);
    }

    @PreAuthorize(AclExpressions.ENTITY_READ)
    public List<Issue> loadIssuesForEntity(EntityVO entityVO) {
        return issueManager.loadIssuesForEntity(entityVO);
    }

    @PreAuthorize(AclExpressions.ISSUE_AUTHOR)
    public Issue updateIssue(Long issueId, IssueVO issueVO) {
        return issueManager.updateIssue(issueId, issueVO);
    }

    @PreAuthorize(AclExpressions.ISSUE_AUTHOR)
    public Issue deleteIssue(Long issueId) {
        return issueManager.deleteIssue(issueId);
    }

    @PreAuthorize(AclExpressions.ISSUE_READ)
    public IssueComment createComment(Long issueId, IssueCommentVO commentVO) {
        return issueManager.createComment(issueId, commentVO);
    }

    @PreAuthorize(AclExpressions.ISSUE_READ)
    public IssueComment loadComment(Long issueId, Long commentId) {
        return issueManager.loadComment(issueId, commentId);
    }

    @PreAuthorize(AclExpressions.COMMENT_AUTHOR)
    public IssueComment updateComment(Long issueId, Long commentId, IssueCommentVO commentVO) {
        return issueManager.updateComment(issueId, commentId, commentVO);
    }

    @PreAuthorize(AclExpressions.COMMENT_AUTHOR)
    public IssueComment deleteComment(Long issueId, Long commentId) {
        return issueManager.deleteComment(issueId, commentId);
    }
}
