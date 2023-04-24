/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.utils;

import com.epam.pipeline.entity.git.GitCommitsFilter;
import com.epam.pipeline.entity.git.report.GitDiffReportFilter;
import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import io.reflectoring.diffparser.api.DiffParser;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DiffUtils {

    private static final Pattern HEADER_PATTERN = Pattern.compile("diff --git a/(.*) b/(.*)");
    private static final Pattern BINARY_PATTERN = Pattern.compile("Binary files (.*) and (.*) differ");
    
    public static final String DEV_NULL = "/dev/null";
    public static final String BINARY_FILES = "Binary files";
    public static final String DIFF_GIT_PREFIX = "diff --git ";
    public static final String DELETION_MARK = "---";
    public static final String ADDITION_MARK = "+++";
    public static final String EMPTY = "";
    public static final String NEW_FILE_HEADER_MESSAGE = "new file mode";
    public static final String DELETED_FILE_HEADER_MESSAGE = "deleted file mode";

    private DiffUtils() {

    }

    public static Diff normalizeDiff(final Diff diff) {
        final Diff result = new Diff();
        result.setFromFileName(parseName(diff.getFromFileName(), "^a/"));
        result.setToFileName(parseName(diff.getToFileName(), "^b/"));
        result.setHeaderLines(diff.getHeaderLines());
        result.setHunks(diff.getHunks());
        return result;
    }

    private static String parseName(final String fileName, final String gitSign) {
        if (StringUtils.isNotBlank(fileName)) {
            return fileName.replaceFirst(gitSign, EMPTY);
        }
        return fileName;
    }

    public static Diff parseBinaryDiff(final String fileDiff) {
        String[] splitted = fileDiff.split("\n");
        String oldFile = null;
        String newFile = null;
        for (String line : splitted) {
            if (line.startsWith(DELETION_MARK) || line.startsWith(ADDITION_MARK)) {
                // Just in case
                break;
            }

            if (line.startsWith(DIFF_GIT_PREFIX)) {
                final Matcher matcher = HEADER_PATTERN.matcher(line);
                if (matcher.matches()) {
                    oldFile = matcher.group(1);
                    newFile = matcher.group(2);
                }
            } else if (line.contains(BINARY_FILES)) {
                final Matcher matcher = BINARY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    oldFile = matcher.group(1).replaceFirst("^a/", EMPTY);
                    newFile = matcher.group(2).replaceFirst("^b/", EMPTY);
                }
            }
        }
        final Diff result = new Diff();
        result.setFromFileName(oldFile);
        result.setToFileName(newFile);
        result.setHeaderLines(Arrays.stream(splitted).collect(Collectors.toList()));
        return result;
    }

    public static DiffType defineDiffType(final Diff diff) {
        if (isFileWasCreated(diff)) {
            return DiffType.ADDED;
        } else if (isFileWasDeleted(diff)) {
            return DiffType.DELETED;
        } else if (!diff.getFromFileName().equals(diff.getToFileName())) {
            return DiffType.RENAMED;
        } else {
            return DiffType.CHANGED;
        }
    }

    private static boolean isFileWasDeleted(Diff diff) {
        return !diff.getFromFileName().equals(DEV_NULL) && diff.getToFileName().equals(DEV_NULL)
                || ListUtils.emptyIfNull(diff.getHeaderLines()).stream()
                        .anyMatch(h -> h.contains(DELETED_FILE_HEADER_MESSAGE));
    }

    private static boolean isFileWasCreated(Diff diff) {
        return diff.getFromFileName().equals(DEV_NULL) && !diff.getToFileName().equals(DEV_NULL)
                || ListUtils.emptyIfNull(diff.getHeaderLines()).stream()
                        .anyMatch(h -> h.contains(NEW_FILE_HEADER_MESSAGE));
    }

    public static GitParsedDiff reduceDiffByFile(GitReaderDiff gitReaderDiff, GitDiffReportFilter reportFilters) {
        final DiffParser diffParser = new UnifiedDiffParser();
        return GitParsedDiff.builder()
                .entries(
                    gitReaderDiff.getEntries().stream()
                            .filter(entry -> Objects.nonNull(entry.getDiff()))
                            .flatMap(diff -> {
                                final String[] diffsByFile = diff.getDiff().split(DIFF_GIT_PREFIX);
                                return Arrays.stream(diffsByFile)
                                        .filter(org.apache.commons.lang.StringUtils::isNotBlank)
                                        .map(fileDiff -> {
                                            final GitParsedDiffEntry.GitParsedDiffEntryBuilder fileDiffBuilder =
                                                    GitParsedDiffEntry.builder().commit(
                                                            diff.getCommit().toBuilder()
                                                                    .authorDate(
                                                                        new Date(diff.getCommit().getAuthorDate()
                                                                                .toInstant()
                                                                        .plus(reportFilters.getUserTimeOffsetInMin(),
                                                                                ChronoUnit.MINUTES).toEpochMilli()))
                                                                    .committerDate(
                                                                        new Date(diff.getCommit().getCommitterDate()
                                                                                .toInstant()
                                                                        .plus(reportFilters.getUserTimeOffsetInMin(),
                                                                                ChronoUnit.MINUTES).toEpochMilli())
                                                            ).build());
                                            try {
                                                final Diff parsed = diffParser.parse(
                                                        (DIFF_GIT_PREFIX + fileDiff).getBytes(StandardCharsets.UTF_8)
                                                ).stream().findFirst().orElseThrow(IllegalArgumentException::new);
                                                return fileDiffBuilder.diff(DiffUtils.normalizeDiff(parsed)).build();
                                            } catch (IllegalArgumentException | IllegalStateException e) {
                                                // If we fail to parse diff with diffParser lets
                                                // try to parse it as binary diffs
                                                return fileDiffBuilder.diff(
                                                        DiffUtils.parseBinaryDiff(DIFF_GIT_PREFIX + fileDiff))
                                                        .build();
                                            }
                                        });
                            }).collect(Collectors.toList())
                ).filters(convertFiltersToUserTimeZone(gitReaderDiff, reportFilters)).build();
    }

    private static GitCommitsFilter convertFiltersToUserTimeZone(final GitReaderDiff gitReaderDiff,
                                                                 final GitDiffReportFilter reportFilters) {
        if (gitReaderDiff.getFilters() == null) {
            return null;
        }

        final GitCommitsFilter.GitCommitsFilterBuilder gitCommitsFilterBuilder = gitReaderDiff.getFilters().toBuilder();
        if (Optional.ofNullable(reportFilters.getCommitsFilter()).map(GitCommitsFilter::getDateFrom).isPresent()) {
            gitCommitsFilterBuilder
                    .dateFrom(reportFilters.getCommitsFilter().getDateFrom()
                            .plus(reportFilters.getUserTimeOffsetInMin(), ChronoUnit.MINUTES));
        }
        if (Optional.ofNullable(reportFilters.getCommitsFilter()).map(GitCommitsFilter::getDateTo).isPresent()) {
            gitCommitsFilterBuilder
                    .dateTo(reportFilters.getCommitsFilter().getDateTo()
                            .plus(reportFilters.getUserTimeOffsetInMin(), ChronoUnit.MINUTES));
        }
        return gitCommitsFilterBuilder.build();
    }

    public static String getChangedFileName(final Diff diff) {
        return diff.getFromFileName().equals(DEV_NULL)
                ? diff.getToFileName()
                : diff.getFromFileName();
    }

    public static boolean isBinary(final Diff diff, final List<String> binaryExts) {
        return ListUtils.emptyIfNull(diff.getHunks()).isEmpty() ||
                binaryExts.stream()
                        .anyMatch(ext -> diff.getToFileName().endsWith(ext) || diff.getFromFileName().endsWith(ext));
    }

    public enum DiffType {
        ADDED, DELETED, CHANGED, RENAMED
    }
}
