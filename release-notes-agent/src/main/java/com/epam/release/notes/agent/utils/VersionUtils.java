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
package com.epam.release.notes.agent.utils;

import com.epam.release.notes.agent.entity.version.Version;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import static java.lang.String.format;

public final class VersionUtils {

    private VersionUtils() {
    }

    public static void updateVersionInFile(final Version version, final String versionFilePath) {
        try {
            Files.write(Paths.get(versionFilePath), Collections.singleton(version.toString()));
        } catch (IOException e) {
            throw new RuntimeException(format("Unable to update version %s in file %s, cause: %s", version.toString(),
                    versionFilePath, e.getMessage()), e);
        }
    }
}
