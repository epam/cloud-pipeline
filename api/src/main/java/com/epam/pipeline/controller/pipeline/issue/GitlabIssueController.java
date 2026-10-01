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

package com.epam.pipeline.controller.pipeline.issue;

import com.epam.pipeline.acl.pipeline.issue.GitlabIssueApiService;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueCommentRequest;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueFilter;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueVO;
import com.epam.pipeline.entity.git.GitlabIssue;
import com.epam.pipeline.entity.git.GitlabIssueComment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/issue/gitlab")
@Tag(name = "gitlab-issue-controller", description = "Gitlab Issues Controller")
@RequiredArgsConstructor
public class GitlabIssueController extends AbstractRestController {

    private static final String ISSUE_ID = "issue_id";
    private final GitlabIssueApiService gitlabIssueApiService;

    @PostMapping
    @Operation(
            summary = "Creates Issue in System Gitlab project.",
            description = "Creates Issue in System Gitlab project." +
                    "Attachments should be specified as list of files paths.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitlabIssue> createIssue(@RequestBody final GitlabIssueVO issue) {
        return Result.success(gitlabIssueApiService.createIssue(issue));
    }

    @PutMapping
    @Operation(
            summary = "Updates Issue in System Gitlab project.",
            description = "Updates Issue in System Gitlab project." +
                    "Attachments should be specified as list of files paths.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitlabIssue> updateIssue(@RequestBody final GitlabIssueVO issue) {
        return Result.success(gitlabIssueApiService.updateIssue(issue));
    }

    @DeleteMapping(value = "/{issue_id}")
    @Operation(
            summary = "Deletes Issue in System Gitlab project.",
            description = "Deletes Issue in System Gitlab project.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Boolean> deleteIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(gitlabIssueApiService.deleteIssue(issueId));
    }

    @PostMapping(value = "/filter")
    @Operation(
            summary = "Gets all users issues.",
            description = "Gets all users issues.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<PagedResult<List<GitlabIssue>>> getIssues(@RequestParam final Integer page,
                                                            @RequestParam final Integer pageSize,
                                                            @RequestBody final GitlabIssueFilter filter) {
        return Result.success(gitlabIssueApiService.getIssues(page, pageSize, filter));
    }

    @GetMapping(value = "/{issue_id}")
    @Operation(
            summary = "Gets Gitlab project issue.",
            description = "Gets Gitlab project issue.")

    public Result<GitlabIssue> getIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(gitlabIssueApiService.getIssue(issueId));
    }

    @GetMapping(value = "/attachment")
    @Operation(
            summary = "Downloads Gitlab project attachment.",
            description = "Downloads Gitlab project attachment.")
    void downloadAttachment(@RequestParam final String secret,
                            final HttpServletResponse response) throws IOException {
        byte[] bytes = gitlabIssueApiService.downloadAttachment(secret);
        writeFileToResponse(response, bytes, FilenameUtils.getName(secret));
    }

    @PostMapping(value = "/{issue_id}/comment")
    @Operation(
            summary = "Adds comment to Gitlab project issue.",
            description = "Adds comment to Gitlab project issue.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<GitlabIssueComment> addIssueComment(@PathVariable(value = ISSUE_ID) final Long issueId,
                                                      @RequestBody final GitlabIssueCommentRequest comment) {
        return Result.success(gitlabIssueApiService.addIssueComment(issueId, comment));
    }
}
