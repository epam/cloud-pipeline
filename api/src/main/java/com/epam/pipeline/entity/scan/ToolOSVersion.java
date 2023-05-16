/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.scan;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Data
@Builder
@RequiredArgsConstructor
public class ToolOSVersion {
    private final String distribution;
    private final String version;

    public boolean isMatched(final String patternsToMatch) {
        return Arrays.stream(patternsToMatch.split(",")).anyMatch(os -> {
            String[] distroVersion = os.split(":");
            // if distro name is not equals allowed return false (allowed: centos, actual: ubuntu)
            if (!distroVersion[0].equalsIgnoreCase(this.distribution)) {
                return false;
            }
            // return false only if version of allowed exists (e.g. centos:6)
            // and actual version contains allowed (e.g. : allowed centos:6, actual centos:6.10)
            return distroVersion.length != 2 || this.version.toLowerCase()
                    .startsWith(distroVersion[1].toLowerCase());
        });
    }
}
