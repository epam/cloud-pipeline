package com.epam.pipeline.manager.utils;

import com.epam.pipeline.entity.git.report.GitParsedDiff;
import com.epam.pipeline.entity.git.report.GitParsedDiffEntry;
import com.epam.pipeline.entity.git.gitreader.GitReaderDiff;
import io.reflectoring.diffparser.api.DiffParser;
import io.reflectoring.diffparser.api.UnifiedDiffParser;
import io.reflectoring.diffparser.api.model.Diff;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DiffUtils {

    private static final Pattern HEADER_PATTERN = Pattern.compile("diff --git a/(.*) b/(.*)");
    private static final Pattern BINARY_PATTERN = Pattern.compile("Binary files (.*) and (.*) differ");
    
    public static final String DEV_NULL = "/dev/null";
    public static final String BINARY_FILES = "Binary files";
    public static final String DIFF_GIT_PREFIX = "diff --git ";
    public static final String DELETION_MARK = "---";
    public static final String ADDITION_MARK = "+++";
    public static final String EMPTY = "";

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

    public static boolean isFileCreated(final Diff diff) {
        return diff.getFromFileName().equals(DEV_NULL) && !diff.getToFileName().equals(DEV_NULL);
    }

    public static boolean isFileDeleted(final Diff diff) {
        return !diff.getFromFileName().equals(DEV_NULL) && diff.getToFileName().equals(DEV_NULL);
    }

    public static GitParsedDiff reduceDiffByFile(GitReaderDiff gitReaderDiff) {
        final DiffParser diffParser = new UnifiedDiffParser();
        return GitParsedDiff.builder()
                .entries(
                        gitReaderDiff.getEntries().stream().flatMap(diff -> {
                            final String[] diffsByFile = diff.getDiff().split(DIFF_GIT_PREFIX);
                            return Arrays.stream(diffsByFile)
                                    .filter(org.apache.commons.lang.StringUtils::isNotBlank)
                                    .map(fileDiff -> {
                                        try {
                                            final Diff parsed = diffParser.parse(
                                                    (DIFF_GIT_PREFIX + fileDiff).getBytes(StandardCharsets.UTF_8)
                                            ).stream().findFirst().orElseThrow(IllegalArgumentException::new);
                                            return GitParsedDiffEntry.builder()
                                                    .commit(diff.getCommit())
                                                    .diff(DiffUtils.normalizeDiff(parsed)).build();
                                        } catch (IllegalArgumentException | IllegalStateException e) {
                                            // If we fail to parse diff with diffParser lets
                                            // try to parse it as binary diffs
                                            return GitParsedDiffEntry.builder()
                                                    .commit(diff.getCommit())
                                                    .diff(DiffUtils.parseBinaryDiff(DIFF_GIT_PREFIX + fileDiff))
                                                    .build();
                                        }
                                    });
                        }).collect(Collectors.toList())
                ).filters(gitReaderDiff.getFilters()).build();
    }
}
