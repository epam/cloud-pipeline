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

package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.Commit;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class EntityBuilder {

    private static final String COMMIT = "commit";
    private static final String SHA = "sha";
    private static final String MESSAGE = "message";

    private final List<Map<String, Object>> data;

    public List<Commit> getCommits() {
        return data.stream()
                .map(commit -> Commit.builder()
                        .commitSha(GitHubUtils.getValueFromHierarchicalMap(commit, SHA))
                        .commitMessage(GitHubUtils.getValueFromHierarchicalMap(commit, COMMIT, MESSAGE))
                        .build())
                .collect(Collectors.toList());
    }
}
