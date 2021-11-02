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

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An util class responsible for handling GitHub repository entities.
 */
public final class GitHubUtils {

    private static final String ZERO = "0";
    private static final int GROUP_OF_ISSUE_NUMBER_REGEX = 1;

    private GitHubUtils() {
    }

    /**
     * Returns a predicate that matches to issue-related commits.
     *
     * @param issueRegex the regex that detects an issue-related commit
     * @return the predicate that matches to issue-related commits
     */
    public static Predicate<Commit> isIssueRelatedCommit(final String issueRegex) {
        return commit -> commit.getCommitMessage().matches(issueRegex);
    }

    /**
     * Returns a function that extracts an issue number from the issue-related commit.
     *
     * @param issueNumberRegex the issue number regex
     * @return the Function-extractor
     */
    public static Function<Commit, String> mapCommitToIssueNumber(final String issueNumberRegex) {
        return commit -> {
            final Matcher matcher = Pattern.compile(issueNumberRegex).matcher(commit.getCommitMessage());
            if (matcher.find()) {
                return matcher.group(GROUP_OF_ISSUE_NUMBER_REGEX);
            }
            return ZERO;
        };
    }
}
