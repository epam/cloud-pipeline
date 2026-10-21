/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.test.creator.git;

import com.epam.pipeline.controller.Result;
import com.epam.pipeline.dto.git.GitRepositoryDTO;
import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitNamespace;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class GitCreatorUtils {

    public static final TypeReference<Result<GitTagEntry>> GIT_TAG_ENTRY_TYPE =
            new TypeReference<Result<GitTagEntry>>() {};
    public static final TypeReference<Result<GitCredentials>> GIT_CREDENTIALS_TYPE =
            new TypeReference<Result<GitCredentials>>() {};
    public static final TypeReference<Result<GitCommitEntry>> GIT_COMMIT_ENTRY_TYPE =
            new TypeReference<Result<GitCommitEntry>>() {};
    public static final TypeReference<Result<GitRepositoryEntry>> GIT_REPOSITORY_ENTRY_TYPE =
            new TypeReference<Result<GitRepositoryEntry>>() {};
    public static final TypeReference<Result<List<GitRepositoryEntry>>> GIT_REPOSITORY_ENTRY_LIST_TYPE =
            new TypeReference<Result<List<GitRepositoryEntry>>>() {};
    public static final TypeReference<Result<List<GitNamespace>>> GIT_NAMESPACE_LIST_TYPE =
            new TypeReference<Result<List<GitNamespace>>>() {};
    public static final TypeReference<Result<List<GitRepositoryDTO>>> GIT_REPOSITORY_DTO_LIST_TYPE =
            new TypeReference<Result<List<GitRepositoryDTO>>>() {};

    private GitCreatorUtils() {

    }

    public static GitTagEntry getGitTagEntry() {
        return new GitTagEntry();
    }

    public static GitRepositoryEntry getGitRepositoryEntry() {
        return new GitRepositoryEntry();
    }

    public static GitCommitEntry getGitCommitEntry() {
        return new GitCommitEntry();
    }

    public static GitCredentials getGitCredentials() {
        return new GitCredentials(TEST_STRING, TEST_STRING, TEST_STRING, TEST_STRING);
    }

    public static GitNamespace getGitNamespace() {
        final GitNamespace namespace = new GitNamespace();
        namespace.setName("test-org");
        namespace.setId("123456");
        namespace.setType("Organization");
        return namespace;
    }

    public static GitRepositoryDTO getGitRepositoryDTO() {
        return GitRepositoryDTO.builder()
                .id("123456")
                .name("test-repo")
                .description("Test repository")
                .defaultBranch("main")
                .httpUrl("https://github.com/test-org/test-repo.git")
                .sshUrl("git@github.com:test-org/test-repo.git")
                .path("https://github.com/test-org/test-repo")
                .build();
    }
}
