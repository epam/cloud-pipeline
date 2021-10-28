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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubServiceImplTest {

    private static final int MESSAGE_MAX_LENGTH = 100;
    private static final String[] ISSUE_TEGS = {"issue #777", "Issue #888", "(issue #999)", "(Issue #7)"};
    private static final String ISSUE_REGEX = "(?i)\\(?issue #.+";
    private static final String ISSUE_NUMBER_REGEX = "#\\d+";

    private static GitHubApiClient gitHubApiClient;

    @BeforeAll
    public static void populateTestCommitList() {
        gitHubApiClient = Mockito.mock(GitHubApiClient.class);
        Mockito.when(gitHubApiClient.listCommit(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> getTestCommits(Integer.parseInt(invocation.getArgument(0))));
        Mockito.when(gitHubApiClient.getIssue(Mockito.anyLong()))
                .thenAnswer(invocation -> GitHubIssue.builder().number(invocation.getArgument(0)).build());
    }

    @Test
    void fetchIssues() {
        GitHubServiceImpl gitHubService = new GitHubServiceImpl(gitHubApiClient, ISSUE_REGEX, ISSUE_NUMBER_REGEX);
        List<GitHubIssue> issues = gitHubService.fetchIssues("4", "");
        assertEquals(ISSUE_TEGS.length, issues.size());
        issues = gitHubService.fetchIssues("10", "");
        assertEquals(ISSUE_TEGS.length, issues.size());
    }

    private static List<Commit> getTestCommits(final int listSize) {
        final List<Commit> testCommitList = Stream.generate(Commit::builder)
                .map(builder -> builder.commitMessage(createRandomMessage()))
                .map(builder -> builder.commitSha(createRandomSha()).build())
                .limit(listSize)
                .collect(Collectors.toList());
        for (int i = 0; i < testCommitList.size(); i++) {
            testCommitList.set(i, addIssueTeg(testCommitList.get(i), ISSUE_TEGS[i % ISSUE_TEGS.length]));
        }
        return testCommitList;
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
}
