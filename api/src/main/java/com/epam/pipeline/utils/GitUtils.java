/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.utils;

import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import org.apache.commons.lang.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitUtils {

    public static final String INITIAL_COMMIT = "Initial commit";
    public static final String GIT_MASTER_REPOSITORY = "master";
    public static final String DRAFT_PREFIX = "draft-";
    public static final String GITKEEP_FILE = ".gitkeep";
    public static final String FOLDER_MARKER = "tree";
    public static final String FILE_MARKER = "blob";
    public static final String BRANCH_REF_PATTERN = "refs/heads/%s";

    //regex for alphanumeric characters, underscore, dots and dash, allowing dash only in the middle
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+([-.][a-zA-Z0-9_]+)*$");

    // This regexps differ from NAME_PATTERN, actually there is no sign why we should replace all '.' and etc
    // from name, and can't use the same pattern here, but since it's legacy we decided to leave it as is.
    // In case of any changes these patterns must match each other in terms of allowed symbols for name.
    private static final String PROJECT_NAME_IN_URL_PATTERN = "(?<=/)\\w+(?=\\.git$)";
    private static final String CHARS_TO_BE_REMOVED_FROM_NAME = "[^\\w\\s]";

    private GitUtils() {}

    /**
     * Checks that name of git entities corresponds to the git naming requirements
     * @param name git entity name
     */
    public static boolean checkGitNaming(String name) {
        Matcher m = NAME_PATTERN.matcher(name);
        return m.matches();
    }

    public static String replaceGitProjectNameInUrl(final String url, final String newName) {
        return url.replaceFirst(PROJECT_NAME_IN_URL_PATTERN, newName);
    }

    public static String convertPipeNameToProject(final String name) {
        return name.trim().toLowerCase().replaceAll(CHARS_TO_BE_REMOVED_FROM_NAME, "").replaceAll("\\s+", "-");
    }

    public static String getRevisionName(final String version) {
        if (StringUtils.isBlank(version)) {
            return version;
        }
        return version.startsWith(DRAFT_PREFIX) ? version.substring(DRAFT_PREFIX.length()) : version;
    }

    public static String buildModifyFileCommitMessage(final String commitMessage, final String filePath,
                                                      final boolean fileExists) {
        if (StringUtils.isNotBlank(commitMessage)) {
            return commitMessage;
        }
        return fileExists
                ? String.format("Updating file %s", filePath)
                : String.format("Creating file %s", filePath);
    }

    public static String getBranchRefOrDefault(final String branch) {
        return String.format(BRANCH_REF_PATTERN, StringUtils.isNotBlank(branch) ? branch : GIT_MASTER_REPOSITORY);
    }

    public static String withoutLeadingDelimiter(final String path) {
        if (StringUtils.isBlank(path) || ProviderUtils.DELIMITER.equals(path)) {
            return path;
        }
        return ProviderUtils.withoutLeadingDelimiter(path);
    }
}
