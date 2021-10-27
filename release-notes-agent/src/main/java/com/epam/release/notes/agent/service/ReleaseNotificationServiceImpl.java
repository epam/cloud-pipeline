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

package com.epam.release.notes.agent.service;

import com.epam.release.notes.agent.entity.github.Commit;
import com.epam.release.notes.agent.entity.github.GitHubIssue;
import com.epam.release.notes.agent.service.github.GitHubService;
import com.epam.release.notes.agent.service.github.GitHubUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ReleaseNotificationServiceImpl implements ReleaseNotificationService{

    private final GitHubService gitHubService;

    @Override
    public void perform() {
        List<Commit> commits = gitHubService.fetchCommits(null, "975c422a796a56bb6d8bcbfd7dc50dd89ecbb5fe");
        List<GitHubIssue> list = getCommitRelatedIssueList(commits);
        System.out.println(list);
    }

    private List<GitHubIssue> getCommitRelatedIssueList(final List<Commit> commits) {
        return commits.stream()
                .filter(GitHubUtils.isIssueRelatedCommit())
                .map(GitHubUtils.mapCommitToIssueNumber())
                .distinct()
                .map(gitHubService::fetchIssue)
                .collect(Collectors.toList());
    }
}
