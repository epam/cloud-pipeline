package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.Commit;
import org.mockito.Mock;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GitHubServiceImplTest {

    private static final String TEST_MESSAGE_1 = "test message 1";
    private static final String TEST_MESSAGE_2 = "test message 2";
    private static final String TEST_SHA1 = "a4b5c6d7e8f90a1b2c3d4e5fa4b5c6d7e8f90a1b";
    private static final String TEST_SHA2 = "777776d7e8f90a1b2c3d4e5fa4b5c6d7e8f55555";
    private static final String COMMIT = "commit";
    private static final String SHA = "sha";
    private static final String MESSAGE = "message";

    @Mock
    private GitHubApiClient gitHubApiClient;

    public void fetchCommits() {

    }

    private static List<Commit> getTestCommits() {
        return Stream.of(Commit.builder().commitSha(TEST_SHA1).commitMessage(TEST_MESSAGE_1).build(),
                Commit.builder().commitSha(TEST_SHA2).commitMessage(TEST_MESSAGE_2).build())
                .collect(Collectors.toList());
    }
}
