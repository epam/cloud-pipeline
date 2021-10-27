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
import org.apache.http.ParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubUtils {

    private static final String ZERO = "0";
    private static final String ISSUE_REGEX = "(?i)Issue #\\d.*";
    private static final String ISSUE_NUMBER_REGEX = "#\\d+";

    private GitHubUtils() {
    }

    /**
     * Retrieves string field value from hierarchical structured map object
     * by the path to the field.
     * E.g. the {@code fieldNamesHierarchy} array {@code "commit", "author", "name"}
     * points at the {@code  authorName} in the map:
     * {@code "commit":..."author":..."name":"authorName"}
     *
     * @param map                 hierarchical structured Map
     * @param fieldNamesHierarchy the path to the field which stored in the map
     * @return the string value of specified field
     */
    @SuppressWarnings("unchecked")
    public static String getValueFromHierarchicalMap(final Map<String, Object> map,
                                                     final String... fieldNamesHierarchy) {
        if (fieldNamesHierarchy.length == 1) {
            return getStringValue(assertThatFieldExists(
                    map.get(fieldNamesHierarchy[0]), fieldNamesHierarchy[0]), fieldNamesHierarchy[0]);
        }
        return getValueFromHierarchicalMap((Map<String, Object>) assertThatFieldExists(
                        map.get(fieldNamesHierarchy[0]), fieldNamesHierarchy[0]),
                Arrays.copyOfRange(fieldNamesHierarchy, 1, fieldNamesHierarchy.length));
    }

    /**
     * Returns a list of elements which is taken from the source list unless not matches the predicate condition.
     *
     * @param list      the source list
     * @param predicate the predicate that defines not valid condition
     * @return the list of the elements unless not matches the predicate condition
     */
    public static <T> List<T> takeWhileNot(final Iterable<T> list, final Predicate<T> predicate) {
        List<T> result = new ArrayList<>();
        for (T element : list) {
            if (predicate.test(element)) {
                return result;
            }
            result.add(element);
        }
        return result;
    }

    /**
     * Returns a predicate that matches to issue-related commits.
     *
     * @return the predicate that matches to issue-related commits
     */
    public static Predicate<Commit> isIssueRelatedCommit() {
        return commit -> commit.getCommitMessage().matches(ISSUE_REGEX);
    }

    /**
     * Returns issue-related commits to issue number function.
     *
     * @return the issue-related commits to issue number function
     */
    public static Function<Commit, String> mapCommitToIssueNumber() {
        return commit -> {
            final Matcher matcher = Pattern.compile(ISSUE_NUMBER_REGEX).matcher(commit.getCommitMessage());
            if (matcher.find()) {
                return commit.getCommitMessage().substring(matcher.start() + 1, matcher.end());
            }
            return ZERO;
        };
    }

    private static <T> T assertThatFieldExists(final T field, final String fieldName) {
        return Optional.ofNullable(field).orElseThrow(() ->
                new ParseException("Json field \"" + fieldName + "\" wasn't found!"));
    }

    private static String getStringValue(final Object field, final String fieldName) {
        try {
            return (String) field;
        } catch (ClassCastException e) {
            throw new ParseException("Json field \"" + fieldName + "\" isn't a plain string!");
        }
    }
}
