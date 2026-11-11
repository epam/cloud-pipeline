/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings({"HideUtilityClassConstructor"})
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PythonExecutableUtils {

    public static final String CP_PYTHON_PATH = "CP_PYTHON_PATH";
    private static final String DEFAULT_PYTHON_EXECUTABLE = "python3";

    /**
     * Resolves the Python interpreter that the scripts the API runs inside its own pod shall use:
     * CP_PYTHON_PATH, which the entrypoint of the pod (init-api) sets, or "python3" if it is not set.
     */
    public static String getPythonExecutable() {
        return resolvePythonExecutable(System.getenv(CP_PYTHON_PATH));
    }

    static String resolvePythonExecutable(final String cpPythonPath) {
        return StringUtils.isBlank(cpPythonPath) ? DEFAULT_PYTHON_EXECUTABLE : cpPythonPath;
    }
}
