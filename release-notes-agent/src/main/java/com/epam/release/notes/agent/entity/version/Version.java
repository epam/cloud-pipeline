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

package com.epam.release.notes.agent.entity.version;

import lombok.Builder;
import lombok.Value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Value
@Builder
public class Version {

    private static final String VERSION_PATTERN = "^(\\d+\\.\\d+\\.\\d+)\\.(\\d+)\\.(\\w+)$";
    private static final Pattern PATTERN = Pattern.compile(VERSION_PATTERN);

    String major;
    String buildNumber;
    String sha;

    public static Version buildVersion(final String version) {
        final Matcher matcher = PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(format("The application version %s doesn't match the pattern %s ", version,
                    VERSION_PATTERN));
        }
        return Version.builder()
                .major(matcher.group(1))
                .buildNumber(matcher.group(2))
                .sha(matcher.group(3))
                .build();
    }

    @Override
    public String toString() {
        return format("%s.%s.%s", major, buildNumber, sha);
    }
}
