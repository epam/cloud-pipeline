/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.pipeline.issue;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueCommentRequest;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueFilter;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueRequest;
import com.epam.pipeline.entity.git.GitlabIssue;
import com.epam.pipeline.entity.git.GitlabIssueComment;
import com.epam.pipeline.manager.git.GitManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GitlabIssueApiService {

    private final GitManager gitManager;

    public GitlabIssue createIssue(final GitlabIssueRequest issue) {
        return gitManager.createIssue(issue);
    }

    public GitlabIssue updateIssue(final GitlabIssueRequest issue) {
        return gitManager.updateIssue(issue);
    }

    public Boolean deleteIssue(final Long issueId) {
        return gitManager.deleteIssue(issueId);
    }

    public PagedResult<List<GitlabIssue>> getIssues(final Integer page,
                                                    final Integer pageSize,
                                                    final GitlabIssueFilter filter) {
        return gitManager.getIssues(page, pageSize, filter);
    }

    public GitlabIssue getIssue(final Long  issueId) {
        return gitManager.getIssue(issueId);
    }

    public GitlabIssueComment addIssueComment(final Long issueId,
                                              final GitlabIssueCommentRequest comment) {
        return gitManager.addIssueComment(issueId, comment);
    }
}
