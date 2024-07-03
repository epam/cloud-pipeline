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

package com.epam.pipeline.mapper;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.IssueCommentVO;
import com.epam.pipeline.controller.vo.IssueVO;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.MapUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface IssueMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Issue toIssue(IssueVO issueVO);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "issueId", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    IssueComment toIssueComment(IssueCommentVO issueCommentVO);

    static Map<String, Object> map(final Issue issue, final JsonMapper mapper) {
        return MapUtils.emptyIfNull(mapper.convertValue(issue, new TypeReference<Map<String, Object>>() {}));
    }

    static Map<String, Object> map(final IssueComment comment, final JsonMapper mapper) {
        return MapUtils.emptyIfNull(mapper.convertValue(comment, new TypeReference<Map<String, Object>>() {}));
    }
}
