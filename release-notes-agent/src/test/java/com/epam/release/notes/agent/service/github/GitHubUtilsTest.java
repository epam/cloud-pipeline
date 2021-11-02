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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubUtilsTest {

    private static final String TEST_ISSUE_NUMBER = "767";
    private static final String ZERO = "0";
    private static final String ISSUE_REGEX = "(?i)\\(?issue #.+";
    private static final String ISSUE_NUMBER_REGEX = ".+#(\\d+).*";

    @Test
    void isIssueRelatedCommit() {
        assertTrue(GitHubUtils.isIssueRelatedCommit(ISSUE_REGEX).test(Commit.builder()
                .commitMessage("issue #7 message").build()));
        assertTrue(GitHubUtils.isIssueRelatedCommit(ISSUE_REGEX).test(Commit.builder()
                .commitMessage("Issue #777 message").build()));
        assertTrue(GitHubUtils.isIssueRelatedCommit(ISSUE_REGEX).test(Commit.builder()
                .commitMessage("(Issue #777) message").build()));
        assertFalse(GitHubUtils.isIssueRelatedCommit(ISSUE_REGEX).test(Commit.builder()
                .commitMessage("message #7").build()));
    }

    @Test
    void mapCommitToIssueNumber() {
        assertEquals(TEST_ISSUE_NUMBER, GitHubUtils.mapCommitToIssueNumber(ISSUE_NUMBER_REGEX).apply(Commit.builder()
                .commitMessage("issue #767 message").build()));
        assertEquals(TEST_ISSUE_NUMBER, GitHubUtils.mapCommitToIssueNumber(ISSUE_NUMBER_REGEX).apply(Commit.builder()
                .commitMessage("Issue #767 message").build()));
        assertEquals(TEST_ISSUE_NUMBER, GitHubUtils.mapCommitToIssueNumber(ISSUE_NUMBER_REGEX).apply(Commit.builder()
                .commitMessage("(Issue #767) message").build()));
        assertEquals(ZERO, GitHubUtils.mapCommitToIssueNumber(ISSUE_NUMBER_REGEX).apply(Commit.builder()
                .commitMessage("(Issue #message)").build()));
    }
}
