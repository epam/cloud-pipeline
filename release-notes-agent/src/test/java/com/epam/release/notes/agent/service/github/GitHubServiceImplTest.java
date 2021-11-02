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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubServiceImplTest {

    private static final int MESSAGE_MAX_LENGTH = 100;
    private static final int COMMIT_PAGE_SIZE = 100;
    private static final int COMMIT_PAGES = 10;
    private static final String[] ISSUE_TEGS = {"issue #777", "Issue #888", "(issue #999)", "(Issue #7)"};
    private static final String ISSUE_REGEX = "(?i)\\(?issue #.+";
    private static final String ISSUE_NUMBER_REGEX = ".+#(\\d+).*";
    private static final String EMPTY_VALUE = "";

    private List<List<Commit>> randomCommitSource;
    private GitHubServiceImpl gitHubService;


    @BeforeEach
    public void populateCommitSource() {
        GitHubApiClient gitHubApiClient = Mockito.mock(GitHubApiClient.class);

        randomCommitSource = Stream.generate(ArrayList<Commit>::new)
                .peek(list -> list.addAll(generateTestCommitsFlow()
                                .map(commit -> addIssueTeg(commit, getNextTeg()))
                        .limit(COMMIT_PAGE_SIZE).collect(Collectors.toList())))
                .limit(COMMIT_PAGES - 1)
                .collect(Collectors.toList());

        randomCommitSource.add(generateTestCommitsFlow()
                .limit(getRandomFromTo(COMMIT_PAGE_SIZE / 2, COMMIT_PAGE_SIZE))
                .collect(Collectors.toList()));

        Mockito.when(gitHubApiClient.listCommits(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(invocation -> randomCommitSource.get((int)invocation.getArgument(1) - 1));

        Mockito.when(gitHubApiClient.getIssue(Mockito.anyLong()))
                .thenAnswer(invocation -> GitHubIssue.builder().number(invocation.getArgument(0)).build());

        gitHubService = new GitHubServiceImpl(gitHubApiClient, ISSUE_REGEX, ISSUE_NUMBER_REGEX);
    }

    @Test
    void fetchCommitsFromOnePages() {
        final int expectedCommitAmount = COMMIT_PAGE_SIZE - 1;
        final int lastElementIndex = COMMIT_PAGE_SIZE - 1;
        final String lastSha = randomCommitSource.get(0).get(lastElementIndex).getCommitSha();
        List<Commit> commits = gitHubService.fetchCommits(EMPTY_VALUE, lastSha);
        assertIterableEquals(new ArrayList<>(randomCommitSource.get(0).stream()
                        .limit(expectedCommitAmount)
                        .collect(Collectors.toList())),
                commits);
    }

    @Test
    void fetchCommitsFromSeveralPages() {
        final int theLastPageCommitAmount = randomCommitSource.get(COMMIT_PAGES - 1).size();
        final int lastElementIndexOfTheLastPage = theLastPageCommitAmount - 1;
        final int expectedCommitAmount = (COMMIT_PAGES - 1) * COMMIT_PAGE_SIZE
                + theLastPageCommitAmount - 1;
        final String lastSha = randomCommitSource.get(COMMIT_PAGES - 1)
                .get(lastElementIndexOfTheLastPage).getCommitSha();

        List<Commit> commits = gitHubService.fetchCommits(EMPTY_VALUE, lastSha);

        assertIterableEquals(new ArrayList<>(randomCommitSource.stream().flatMap(Collection::stream)
                        .limit(expectedCommitAmount).collect(Collectors.toList())), commits);
    }

    @Test
    void fetchIssuesWhenFirstCommitGiven() {
        final String commitSha = randomCommitSource.get(0).get(0).getCommitSha();
        final List<GitHubIssue> issues = gitHubService.fetchIssues(EMPTY_VALUE, commitSha);
        assertTrue(issues.isEmpty());
    }

    @Test
    void fetchIssuesWhenSecondCommitGiven() {
        final String commitSha = randomCommitSource.get(0).get(1).getCommitSha();
        final String commitMessage = randomCommitSource.get(0).get(0).getCommitMessage();
        final List<GitHubIssue> issues = gitHubService.fetchIssues(EMPTY_VALUE, commitSha);
        assertEquals(1, issues.size());
        assertEquals(extractIssueNumber(commitMessage), issues.get(0).getNumber());
    }

    @Test
    void fetchIssuesWhenTheLastCommitGiven() {
        final int theLastPageCommitAmount = randomCommitSource.get(COMMIT_PAGES - 1).size();
        final int lastElementIndexOfTheLastPage = theLastPageCommitAmount - 1;
        final String commitSha = randomCommitSource.get(COMMIT_PAGES - 1).get(lastElementIndexOfTheLastPage)
                .getCommitSha();
        final List<GitHubIssue> issues = gitHubService.fetchIssues(EMPTY_VALUE, commitSha);
        assertEquals(ISSUE_TEGS.length, issues.size());
        assertIterableEquals(
                Stream.of(ISSUE_TEGS).map(GitHubServiceImplTest::extractIssueNumber).collect(Collectors.toList()),
                issues.stream().map(GitHubIssue::getNumber).collect(Collectors.toList()));
    }

    private static Stream<Commit> generateTestCommitsFlow() {
        return Stream.generate(Commit::builder)
                .map(builder -> builder.commitMessage(createRandomMessage()))
                .map(builder -> builder.commitSha(createRandomSha()).build());
    }

    private static String createRandomMessage() {
        final String[] alphabet = IntStream.rangeClosed('a', 'z')
                .mapToObj(i -> String.valueOf((char) i))
                .toArray(String[]::new);
        return Stream.generate(() -> alphabet[new Random().nextInt(alphabet.length)])
                .limit(new Random().nextInt(MESSAGE_MAX_LENGTH))
                .collect(Collectors.joining());
    }

    private static String createRandomSha() {
        return IntStream.rangeClosed(1, 40)
                .map(i -> new Random().nextInt(0xf))
                .mapToObj(i -> String.format("%x", i))
                .collect(Collectors.joining());
    }

    private static Commit addIssueTeg(final Commit in, final String teg) {
        return Commit.builder().commitMessage(teg + in.getCommitMessage()).commitSha(in.getCommitSha()).build();
    }

    private static String getNextTeg() {
        final String firstTeg = ISSUE_TEGS[0];
        for (int i = 0; i < ISSUE_TEGS.length - 1; i++) {
            ISSUE_TEGS[i] = ISSUE_TEGS[i + 1];
        }
        ISSUE_TEGS[ISSUE_TEGS.length - 1] = firstTeg;
        return firstTeg;
    }

    private static int getRandomFromTo(final int from, final int to) {
        return new Random().nextInt(to - from) + from;
    }

    private static long extractIssueNumber(final String message) {
        final Matcher matcher = Pattern.compile(GitHubServiceImplTest.ISSUE_NUMBER_REGEX).matcher(message);
        matcher.find();
        return Long.parseLong(matcher.group(1));
    }
}
