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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitUtils {

    //regex for alphanumeric characters, underscore, dots and dash, allowing dash only in the middle
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+([-.][a-zA-Z0-9_]+)*$");
    private static final String PROJECT_NAME_IN_URL_PATTERN = "(?<=/)[\\w]+(?=\\.git$)";

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
        // This regexp differ from NAME_PATTERN, actually there is no sign why we should replace all '.' and etc
        // from name, and cant use the same pattern here, but since it's legacy method we decided to leave it as is
        return name.trim().toLowerCase().replaceAll("[^\\w\\s]", "").replaceAll("\\s+", "-");
    }
}
