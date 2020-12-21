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

import com.epam.pipeline.entity.git.GitCommitEntry;
import com.epam.pipeline.entity.git.GitCredentials;
import com.epam.pipeline.entity.git.GitRepositoryEntry;
import com.epam.pipeline.entity.git.GitTagEntry;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;

public final class GitCreatorUtils {

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
}
