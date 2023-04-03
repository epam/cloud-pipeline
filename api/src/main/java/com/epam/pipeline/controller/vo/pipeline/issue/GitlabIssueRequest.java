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

package com.epam.pipeline.controller.vo.pipeline.issue;

import com.epam.pipeline.entity.git.GitlabIssue;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GitlabIssueRequest {

    @JsonProperty("iid")
    private Long id;
    private String title;
    private String description;
    private Map<String, String> attachments;
    private List<String> labels;

    public GitlabIssue toIssue() {
        final GitlabIssue gitlabIssue = new GitlabIssue();
        gitlabIssue.setId(id);
        gitlabIssue.setTitle(title);
        gitlabIssue.setDescription(description);
        gitlabIssue.setLabels(labels);
        return gitlabIssue;
    }
}
