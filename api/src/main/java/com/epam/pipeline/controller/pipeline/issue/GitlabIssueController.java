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
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueCommentRequest;
import com.epam.pipeline.controller.vo.pipeline.issue.GitlabIssueRequest;
import com.epam.pipeline.entity.git.GitlabIssue;
import com.epam.pipeline.entity.git.GitlabIssueComment;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/issue/gitlab")
@Api(value = "Gitlab Issues")
@RequiredArgsConstructor
public class GitlabIssueController extends AbstractRestController {

    private static final String ISSUE_ID = "issue_id";
    private final GitlabIssueApiService gitlabIssueApiService;

    @PostMapping
    @ApiOperation(
            value = "Creates Issue in System Gitlab project.",
            notes = "Creates Issue in System Gitlab project.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitlabIssue> createIssue(@RequestBody final GitlabIssueRequest issue) {
        return Result.success(gitlabIssueApiService.createOrUpdateIssue(issue));
    }

    @PutMapping
    @ApiOperation(
            value = "Updates Issue in System Gitlab project.",
            notes = "Updates Issue in System Gitlab project.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitlabIssue> updateIssue(@RequestBody final GitlabIssueRequest issue) {
        return Result.success(gitlabIssueApiService.createOrUpdateIssue(issue));
    }

    @DeleteMapping(value = "/{issue_id}")
    @ApiOperation(
            value = "Deletes Issue in System Gitlab project.",
            notes = "Deletes Issue in System Gitlab project.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Boolean> deleteIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(gitlabIssueApiService.deleteIssue(issueId));
    }

    @GetMapping
    @ApiOperation(
            value = "Gets all users issues. ",
            notes = "Gets all users issues. " +
                    "Attachments should be specified as list of files paths.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<GitlabIssue>> getIssues() {
        return Result.success(gitlabIssueApiService.getIssues());
    }

    @GetMapping(value = "/{issue_id}")
    @ApiOperation(
            value = "Gets Gitlab project issue.",
            notes = "Gets Gitlab project issue.",
            produces = MediaType.APPLICATION_JSON_VALUE)

    public Result<GitlabIssue> getIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(gitlabIssueApiService.getIssue(issueId));
    }

    @PostMapping(value = "/{issue_id}/comment")
    @ApiOperation(
            value = "Adds comment to Gitlab project issue.",
            notes = "Adds comment to Gitlab project issue.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<GitlabIssueComment> addIssueComment(@PathVariable(value = ISSUE_ID) final Long issueId,
                                                      @RequestBody final GitlabIssueCommentRequest comment) {
        return Result.success(gitlabIssueApiService.addIssueComment(issueId, comment));
    }
}
