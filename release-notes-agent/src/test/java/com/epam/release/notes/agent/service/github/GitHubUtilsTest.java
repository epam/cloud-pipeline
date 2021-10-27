package com.epam.release.notes.agent.service.github;


import com.epam.release.notes.agent.entity.github.Commit;
import org.apache.http.ParseException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubUtilsTest {

    private static final String LEVEL_1 = "level_1";
    private static final String LEVEL_2 = "level_2";
    private static final String LEVEL_3 = "level_3";
    private static final String TEST_VALUE = "testValue";
    private static final String TEST_ISSUE_NUMBER = "767";
    private static final String ZERO = "0";

    @Test
    public void shouldFailOnWrongFieldLocationWhenGetValueFromHierarchicalMap() {
        Map<String, Object> testMap1 = new HashMap<>();
        Map<String, Object> testMap2 = new HashMap<>();
        Map<String, Object> testMap3 = new HashMap<>();
        testMap1.put(LEVEL_1, testMap2);
        testMap2.put(LEVEL_2, testMap3);
        testMap3.put(LEVEL_3, TEST_VALUE);
        assertThrows(ParseException.class,
                () -> GitHubUtils.getValueFromHierarchicalMap(testMap1, LEVEL_3));
    }

    @Test
    public void getValueFromHierarchicalMap() {
        final String expectedValue = TEST_VALUE;
        Map<String, Object> testMap1 = new HashMap<>();
        Map<String, Object> testMap2 = new HashMap<>();
        Map<String, Object> testMap3 = new HashMap<>();
        testMap1.put(LEVEL_1, testMap2);
        testMap2.put(LEVEL_2, testMap3);
        testMap3.put(LEVEL_3, expectedValue);
        assertEquals(expectedValue,
                GitHubUtils.getValueFromHierarchicalMap(testMap1, LEVEL_1, LEVEL_2, LEVEL_3));
    }

    @Test
    void takeWhileNot() {
        List<Integer> testList = IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toList());
        List<Integer> expectedList = IntStream.rangeClosed(1, 10).boxed().collect(Collectors.toList());
        assertEquals(expectedList, GitHubUtils.takeWhileNot(testList, i -> i == 11));
    }

    @Test
    void isIssueRelatedCommit() {
        assertTrue(GitHubUtils.isIssueRelatedCommit().test(Commit.builder()
                .commitMessage("issue #7 message").build()));
        assertTrue(GitHubUtils.isIssueRelatedCommit().test(Commit.builder()
                .commitMessage("Issue #777 message").build()));
        assertTrue(GitHubUtils.isIssueRelatedCommit().test(Commit.builder()
                .commitMessage("(Issue #777) message").build()));
        assertFalse(GitHubUtils.isIssueRelatedCommit().test(Commit.builder()
                .commitMessage("message #7").build()));
    }

    @Test
    void mapCommitToIssueNumber() {
        assertEquals(TEST_ISSUE_NUMBER, GitHubUtils.mapCommitToIssueNumber().apply(Commit.builder()
                .commitMessage("issue #767 message").build()));
        assertEquals(TEST_ISSUE_NUMBER, GitHubUtils.mapCommitToIssueNumber().apply(Commit.builder()
                .commitMessage("Issue #767 message").build()));
        assertEquals(TEST_ISSUE_NUMBER, GitHubUtils.mapCommitToIssueNumber().apply(Commit.builder()
                .commitMessage("(Issue #767) message").build()));
        assertEquals(ZERO, GitHubUtils.mapCommitToIssueNumber().apply(Commit.builder()
                .commitMessage("(Issue #message)").build()));
    }
}
