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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.fileupload.FileUploadException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
@Api(value = "Issues")
public class IssueController extends AbstractRestController {

    private static final String ISSUE_ID = "issueId";
    private static final String COMMENT_ID = "commentId";

    @Autowired
    private IssueApiService issueApiService;

    @Autowired
    private AttachmentFileManager attachmentFileManager;

    @PostMapping(value = "/issues")
    @ResponseBody
    @ApiOperation(
            value = "Registers a new issue.",
            notes = "Registers a new issue.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> createIssue(@RequestBody IssueVO issue) {
        return Result.success(issueApiService.createIssue(issue));
    }

    @GetMapping(value = "/issues/{issueId}")
    @ResponseBody
    @ApiOperation(
            value = "Returns an issue, specified by id.",
            notes = "Returns an issue, specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> loadIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(issueApiService.loadIssue(issueId));
    }

    @GetMapping(value = "/issues")
    @ResponseBody
    @ApiOperation(
            value = "Returns all issues for particular entity.",
            notes = "Returns all issues for particular entity.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<List<Issue>> loadIssues(@RequestParam Long entityId, @RequestParam AclClass entityClass) {
        return Result.success(issueApiService.loadIssuesForEntity(new EntityVO(entityId, entityClass)));
    }

    @PutMapping(value = "/issues/{issueId}")
    @ResponseBody
    @ApiOperation(
            value = "Updates an issue, specified by id.",
            notes = "Updates an issue, specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> updateIssue(
            @PathVariable(value = ISSUE_ID) final Long issueId, @RequestBody IssueVO issueVO) {
        return Result.success(issueApiService.updateIssue(issueId, issueVO));
    }

    @DeleteMapping(value = "/issues/{issueId}")
    @ResponseBody
    @ApiOperation(
            value = "Deletes an issue, specified by id.",
            notes = "Deletes an issue, specified by id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<Issue> deleteIssue(@PathVariable(value = ISSUE_ID) final Long issueId) {
        return Result.success(issueApiService.deleteIssue(issueId));
    }

    @PostMapping(value = "/issues/{issueId}/comments")
    @ResponseBody
    @ApiOperation(
            value = "Registers a new comment.",
            notes = "Registers a new comment.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> createComment(
            @PathVariable(value = ISSUE_ID) final Long issueId, @RequestBody IssueCommentVO commentVO) {
        return Result.success(issueApiService.createComment(issueId, commentVO));
    }

    @GetMapping(value = "/issues/{issueId}/comments/{commentId}")
    @ResponseBody
    @ApiOperation(
            value = "Returns a comment, specified by issue id and comment id.",
            notes = "Returns a comment, specified by issue id and comment id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> loadComment(
            @PathVariable(value = ISSUE_ID) final Long issueId,
            @PathVariable(value = COMMENT_ID) final Long commentId) {
        return Result.success(issueApiService.loadComment(issueId, commentId));
    }

    @PutMapping(value = "/issues/{issueId}/comments/{commentId}")
    @ResponseBody
    @ApiOperation(
            value = "Updates a comment, specified by issue id and comment id.",
            notes = "Updates a comment, specified by issue id and comment id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> updateComment(
            @PathVariable(value = ISSUE_ID) final Long issueId,
            @PathVariable(value = COMMENT_ID) final Long commentId,
            @RequestBody IssueCommentVO commentVO) {
        return Result.success(issueApiService.updateComment(issueId, commentId, commentVO));
    }

    @DeleteMapping(value = "/issues/{issueId}/comments/{commentId}")
    @ResponseBody
    @ApiOperation(
            value = "Deletes a comment, specified by issue id and comment id.",
            notes = "Deletes a comment, specified by issue id and comment id.",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
            value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
            })
    public Result<IssueComment> deleteComment(
            @PathVariable(value = ISSUE_ID) final Long issueId,
            @PathVariable(value = COMMENT_ID) final Long commentId) {
        return Result.success(issueApiService.deleteComment(issueId, commentId));
    }

    @PostMapping(value = "/attachment")
    @ResponseBody
    @ApiOperation(
        value = "Uploads a list of files as attachments.",
        notes = "Uploads a list of files as attachments to issues or comments.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<List<Attachment>> uploadAttachments(HttpServletRequest request)
        throws IOException, FileUploadException {
        return Result.success(processStreamingUpload(request, attachmentFileManager::uploadAttachment));
    }

    @GetMapping(value = "/attachment/{id}")
    @ApiOperation(
        value = "Downloads an attachment.",
        notes = "Downloads an attachment by specified ID.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public void downloadAttachment(HttpServletResponse response, @PathVariable Long id) throws IOException {
        DataStorageStreamingContent content = attachmentFileManager.downloadAttachment(id);
        writeStreamToResponse(response, content.getContent(), content.getName(), guessMediaType(content.getName()));
    }

    @DeleteMapping(value = "/attachment/{id}")
    @ResponseBody
    @ApiOperation(
        value = "Deletes an attachment.",
        notes = "Deletes an attachment. Use this endpoint to remove an attachment, that hasn't been attached to an "
                + "issue or comment yet."
                + "To remove an attachment, that has been already submitted (e.g. when editing an issue), use "
                + "<b>PUT /issues/{issueId}</b>",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<Boolean> deleteAttachment(@PathVariable Long id) {
        attachmentFileManager.deleteAttachment(id);
        return Result.success(true);
    }

    @GetMapping("/issues/my")
    @ResponseBody
    @ApiOperation(
        value = "Loads current user's issues.",
        notes = "Loads current user issues with nested comments.",
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(
        value = {@ApiResponse(code = HTTP_STATUS_OK, message = API_STATUS_DESCRIPTION)
        })
    public Result<PagedResult<List<Issue>>> loadMy(@RequestParam Long page,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(issueApiService.loadMy(page, pageSize));
    }
}
