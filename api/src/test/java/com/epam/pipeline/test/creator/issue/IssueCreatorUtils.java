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

package com.epam.pipeline.test.creator.issue;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;

public final class IssueCreatorUtils {

    public static final TypeReference<Result<Issue>> ISSUE_TYPE =
            new TypeReference<Result<Issue>>() {};
    public static final TypeReference<Result<List<Issue>>> ISSUE_LIST_TYPE =
            new TypeReference<Result<List<Issue>>>() {};
    public static final TypeReference<Result<IssueComment>> ISSUE_COMMENT_TYPE =
            new TypeReference<Result<IssueComment>>() {};
    public static final TypeReference<Result<List<Attachment>>> ATTACHMENT_LIST_TYPE =
            new TypeReference<Result<List<Attachment>>>() {};
    public static final TypeReference<Result<PagedResult<List<Issue>>>> PAGED_RESULT_LIST_ISSUE_TYPE =
            new TypeReference<Result<PagedResult<List<Issue>>>>() {};

    private IssueCreatorUtils() {

    }

    public static Issue getIssue() {
        return new Issue();
    }

    public static Issue getIssue(final EntityVO entityVO, final String author) {
        final Issue issue = new Issue();
        issue.setEntity(entityVO);
        issue.setAuthor(author);
        return issue;
    }

    public static IssueVO getIssueVO() {
        return new IssueVO();
    }

    public static IssueVO getIssueVO(final EntityVO entityVO) {
        final IssueVO issueVO = new IssueVO();
        issueVO.setEntity(entityVO);
        return issueVO;
    }

    public static IssueComment getIssueComment() {
        return new IssueComment();
    }

    public static IssueComment getIssueComment(String author) {
        final IssueComment issueComment = new IssueComment();
        issueComment.setAuthor(author);
        return issueComment;
    }

    public static IssueCommentVO getIssueCommentVO() {
        return new IssueCommentVO();
    }

    public static Attachment getAttachment() {
        return new Attachment();
    }

    public static PagedResult<List<Issue>> getPagedListIssue() {
        return new PagedResult<>();
    }

    public static PagedResult<List<Issue>> getPagedResult() {
        return new PagedResult<>(Collections.singletonList(getIssue()), TEST_INT);
    }
}
