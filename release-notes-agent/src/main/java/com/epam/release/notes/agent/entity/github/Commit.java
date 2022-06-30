/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.release.notes.agent.entity.github;

import com.epam.release.notes.agent.service.github.GitHubCommitDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;

@Value
@Builder
@JsonDeserialize(using = GitHubCommitDeserializer.class)
public class Commit {
    String commitSha;
    String commitMessage;
    String author;
    String authorEmail;
    Date authorDate;
    String committer;
    String committerEmail;
    Date committerDate;
    List<String> parentShas;
}
