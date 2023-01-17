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

package com.epam.pipeline.entity.git;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class GitPushCommitEntry {
    private static final String DEFAULT_BRANCH = "master";

    @JsonProperty("branch_name")
    private String branchName;
    private String branch;
    @JsonProperty("commit_message")
    private String commitMessage;
    @JsonProperty("author_email")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String authorEmail;
    @JsonProperty("author_name")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String authorName;
    private List<GitPushCommitActionEntry> actions;

    public void setBranch(final String branch) {
        this.branch = branch;
        this.branchName = branch;
    }

    public String getBranch() {
        return StringUtils.isBlank(branchName) ? branch : branchName;
    }

    public GitPushCommitEntry() {
        this.branch = DEFAULT_BRANCH;
        this.branchName = DEFAULT_BRANCH;
        this.actions = new ArrayList<>();
    }
}
