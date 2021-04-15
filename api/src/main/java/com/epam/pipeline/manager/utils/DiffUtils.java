package com.epam.pipeline.manager.utils;

import io.reflectoring.diffparser.api.model.Diff;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DiffUtils {

    private static final Pattern HEADER_PATTERN = Pattern.compile("diff --git a/(.*) b/(.*)");
    private static final Pattern BINARY_PATTERN = Pattern.compile("Binary files (.*) and (.*) differ");

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
            return fileName.replaceFirst(gitSign, "");
        }
        return fileName;
    }

    public static Diff parseBinaryDiff(final String fileDiff) {
        String[] splitted = fileDiff.split("\n");
        String oldFile = null;
        String newFile = null;
        for (String line : splitted) {
            if (line.startsWith("---") || line.startsWith("+++")) {
                // Just in case
                break;
            }

            if (line.startsWith("diff --git")) {
                final Matcher matcher = HEADER_PATTERN.matcher(line);
                if (matcher.matches()) {
                    oldFile = matcher.group(1);
                    newFile = matcher.group(2);
                }
            } else if (line.contains("Binary files")) {
                final Matcher matcher = BINARY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    oldFile = matcher.group(1).replaceFirst("^a/", "");
                    newFile = matcher.group(2).replaceFirst("^b/", "");
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
        return diff.getFromFileName().equals("/dev/null") && !diff.getToFileName().equals("/dev/null");
    }

    public static boolean isFileDeleted(final Diff diff) {
        return !diff.getFromFileName().equals("/dev/null") && diff.getToFileName().equals("/dev/null");
    }

    public static void main(String[] args) {
        System.out.println("a/asdasdasd".replaceFirst("^a/", ""));
    }
}
