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
import com.epam.release.notes.agent.entity.github.GitHubIssue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GitHubServiceImpl implements GitHubService {

    private static final int START_PAGE = 1;

    private final String issueRegex;
    private final String issueNumberRegex;
    private final GitHubApiClient client;

    public GitHubServiceImpl(final GitHubApiClient client,
                             @Value("${github.issue.regex:(?i)\\(?issue #.+}") final String issueRegex,
                             @Value("${github.issue.number.regex:#\\d+}") final String issueNumberRegex) {
        this.issueRegex = issueRegex;
        this.issueNumberRegex = issueNumberRegex;
        this.client = client;
    }

    /**
     * Returns a commit list that starts with the {@code shaFrom} (newer) commit
     * to the {@code shaTo} (older) commit exclusively.
     *
     * @param shaFrom the first latest commit (e.g. the commit if the actual newest project version)
     * @param shaTo   the oldest commit - it isn't included in the result list
     *                (e.g. the commit if the previous project version)
     * @return the result commit list
     */
    @Override
    public List<Commit> fetchCommits(final String shaFrom, final String shaTo) {
        final List<Commit> resultList = new ArrayList<>();
        int currentPage = START_PAGE;
        List<Commit> commits = client.listCommits(shaFrom, currentPage);
        while (!commits.isEmpty()) {
            for (Commit commit : commits) {
                if (commit.getCommitSha().equals(shaTo)) {
                    return resultList;
                }
                resultList.add(commit);
            }
            commits = client.listCommits(shaFrom, ++currentPage);
        }
        return resultList;
    }

    @Override
    public List<GitHubIssue> fetchIssues(final String shaCommitFrom, final String shaCommitTo) {
        return fetchCommits(shaCommitFrom, shaCommitTo).stream()
                .filter(GitHubUtils.isIssueRelatedCommit(issueRegex))
                .map(GitHubUtils.mapCommitToIssueNumber(issueNumberRegex))
                .distinct()
                .map(this::fetchIssue)
                .collect(Collectors.toList());
    }

    @Override
    public GitHubIssue fetchIssue(final String number) {
        return client.getIssue(Long.parseLong(number));
    }
}
