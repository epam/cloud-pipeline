/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.git;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitlabIssue {

    private Long id;
    @JsonProperty("project_id")
    private Long projectId;
    private String title;
    private String description;
    private String state;
    private String type;
    private String severity;
    @JsonProperty("created_at")
    private ZonedDateTime createdAt;
    @JsonProperty("updated_at")
    private ZonedDateTime updatedAt;
    @JsonProperty("closed_at")
    private ZonedDateTime closedAt;
    private String confidential;
    @JsonProperty("milestone_id")
    private String milestoneId;
    private List<String> labels;
    private GitlabUser author;
    @JsonProperty("closed_by")
    private GitlabUser closedBy;
    private List<String> attachments;
    private List<GitlabUser> assignees;
    @JsonIgnoreProperties
    private List<GitlabIssueComment> comments;
}
