package com.epam.release.notes.agent.service.github;


import com.epam.release.notes.agent.entity.github.Commit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubUtilsTest {

    private static final String TEST_ISSUE_NUMBER = "767";
    private static final String ZERO = "0";
    private static final String ISSUE_REGEX = "(?i)\\(?issue #.+";
    private static final String ISSUE_NUMBER_REGEX = "#\\d+";

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
