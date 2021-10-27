package com.epam.release.notes.agent.service.github;

import com.epam.release.notes.agent.entity.github.Commit;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class EntityBuilderTest {

    private static final String TEST_MESSAGE_1 = "test message 1";
    private static final String TEST_MESSAGE_2 = "test message 2";
    private static final String TEST_SHA1 = "a4b5c6d7e8f90a1b2c3d4e5fa4b5c6d7e8f90a1b";
    private static final String TEST_SHA2 = "777776d7e8f90a1b2c3d4e5fa4b5c6d7e8f55555";
    private static final String COMMIT = "commit";
    private static final String SHA = "sha";
    private static final String MESSAGE = "message";

    @Test
    public void getCommits() {
        Map<String, Object> root1 = new HashMap<>();
        root1.put(SHA, TEST_SHA1);
        Map<String, Object> root2 = new HashMap<>();
        root2.put(SHA, TEST_SHA2);
        Map<String, Object> innerCommitData1 = new HashMap<>();
        innerCommitData1.put(MESSAGE, TEST_MESSAGE_1);
        Map<String, Object> innerCommitData2 = new HashMap<>();
        innerCommitData2.put(MESSAGE, TEST_MESSAGE_2);
        root1.put(COMMIT, innerCommitData1);
        root2.put(COMMIT, innerCommitData2);

        List<Commit> commits = new EntityBuilder(Stream.of(root1, root2).collect(Collectors.toList())).getCommits();
        assertEquals(TEST_MESSAGE_1, commits.get(0).getCommitMessage());
        assertEquals(TEST_MESSAGE_2, commits.get(1).getCommitMessage());
        assertEquals(TEST_SHA1, commits.get(0).getCommitSha());
        assertEquals(TEST_SHA2, commits.get(1).getCommitSha());
    }
}
