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

package com.epam.pipeline.controller.issue;

import java.io.IOException;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.epam.pipeline.controller.AbstractRestController;
import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.issue.AttachmentFileManager;
import com.epam.pipeline.acl.issue.IssueApiService;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "issue-controller", description = "Issues Controller")
public class IssueController extends AbstractRestController {

    private static final String ISSUE_ID = "issueId";
    private static final String COMMENT_ID = "commentId";

    @Autowired
    private IssueApiService issueApiService;

    @Autowired
    private AttachmentFileManager attachmentFileManager;

    @PostMapping(value = "/issues")
    @ResponseBody
    @Operation(
            summary = "Registers a new issue.",
            description = "Registers a new issue.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> createIssue(@RequestBody IssueVO issue) {
        return Result.success(issueApiService.createIssue(issue));
    }

    @GetMapping(value = "/issues/{issueId}")
    @ResponseBody
    @Operation(
            summary = "Returns an issue, specified by id.",
            description = "Returns an issue, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> loadIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(issueApiService.loadIssue(issueId));
    }

    @GetMapping(value = "/issues")
    @ResponseBody
    @Operation(
            summary = "Returns all issues for particular entity.",
            description = "Returns all issues for particular entity.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<List<Issue>> loadIssues(@RequestParam Long entityId, @RequestParam AclClass entityClass) {
        return Result.success(issueApiService.loadIssuesForEntity(new EntityVO(entityId, entityClass)));
    }

    @PutMapping(value = "/issues/{issueId}")
    @ResponseBody
    @Operation(
            summary = "Updates an issue, specified by id.",
            description = "Updates an issue, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> updateIssue(
            @PathVariable(value = ISSUE_ID) final Long issueId, @RequestBody IssueVO issueVO) {
        return Result.success(issueApiService.updateIssue(issueId, issueVO));
    }

    @DeleteMapping(value = "/issues/{issueId}")
    @ResponseBody
    @Operation(
            summary = "Deletes an issue, specified by id.",
            description = "Deletes an issue, specified by id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> deleteIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(issueApiService.deleteIssue(issueId));
    }

    @PostMapping(value = "/issues/{issueId}/comments")
    @ResponseBody
    @Operation(
            summary = "Registers a new comment.",
            description = "Registers a new comment.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> createComment(
            @PathVariable(value = ISSUE_ID) final Long issueId, @RequestBody IssueCommentVO commentVO) {
        return Result.success(issueApiService.createComment(issueId, commentVO));
    }

    @GetMapping(value = "/issues/{issueId}/comments/{commentId}")
    @ResponseBody
    @Operation(
            summary = "Returns a comment, specified by issue id and comment id.",
            description = "Returns a comment, specified by issue id and comment id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> loadComment(
            @PathVariable(value = ISSUE_ID) final Long issueId,
            @PathVariable(value = COMMENT_ID) final Long commentId) {
        return Result.success(issueApiService.loadComment(issueId, commentId));
    }

    @PutMapping(value = "/issues/{issueId}/comments/{commentId}")
    @ResponseBody
    @Operation(
            summary = "Updates a comment, specified by issue id and comment id.",
            description = "Updates a comment, specified by issue id and comment id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> updateComment(
            @PathVariable(value = ISSUE_ID) final Long issueId,
            @PathVariable(value = COMMENT_ID) final Long commentId,
            @RequestBody IssueCommentVO commentVO) {
        return Result.success(issueApiService.updateComment(issueId, commentId, commentVO));
    }

    @DeleteMapping(value = "/issues/{issueId}/comments/{commentId}")
    @ResponseBody
    @Operation(
            summary = "Deletes a comment, specified by issue id and comment id.",
            description = "Deletes a comment, specified by issue id and comment id.")
    @ApiResponses(
            value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> deleteComment(
            @PathVariable(value = ISSUE_ID) final Long issueId,
            @PathVariable(value = COMMENT_ID) final Long commentId) {
        return Result.success(issueApiService.deleteComment(issueId, commentId));
    }

    @PostMapping(value = "/attachment")
    @ResponseBody
    @Operation(
        summary = "Uploads a list of files as attachments.",
        description = "Uploads a list of files as attachments to issues or comments.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<List<Attachment>> uploadAttachments(final HttpServletRequest request)
        throws IOException, FileUploadException {
        return Result.success(processStreamingUpload(consumeMultipartFiles(request),
                attachmentFileManager::uploadAttachment));
    }

    @GetMapping(value = "/attachment/{id}")
    @Operation(
        summary = "Downloads an attachment.",
        description = "Downloads an attachment by specified ID.")
    public void downloadAttachment(HttpServletResponse response, @PathVariable Long id) throws IOException {
        DataStorageStreamingContent content = attachmentFileManager.downloadAttachment(id);
        writeStreamToResponse(response, content.getContent(), content.getName(), guessMediaType(content.getName()));
    }

    @DeleteMapping(value = "/attachment/{id}")
    @ResponseBody
    @Operation(
        summary = "Deletes an attachment.",
        description = "Deletes an attachment. Use this endpoint to remove an attachment, "
                + "that hasn't been attached to an issue or comment yet."
                + "To remove an attachment, that has been already submitted (e.g. when editing an issue), use "
                + "<b>PUT /issues/{issueId}</b>")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<Boolean> deleteAttachment(@PathVariable Long id) {
        attachmentFileManager.deleteAttachment(id);
        return Result.success(true);
    }

    @GetMapping("/issues/my")
    @ResponseBody
    @Operation(
        summary = "Loads current user's issues.",
        description = "Loads current user issues with nested comments.")
    @ApiResponses(
        value = {@ApiResponse(description = API_STATUS_DESCRIPTION)
        })
    public Result<PagedResult<List<Issue>>> loadMy(@RequestParam Long page,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(issueApiService.loadMy(page, pageSize));
    }
}
