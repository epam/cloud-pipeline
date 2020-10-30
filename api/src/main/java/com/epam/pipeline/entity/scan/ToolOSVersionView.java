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

import lombok.Getter;

@Getter
public class ToolOSVersionView {

    private final String distribution;
    private final String version;
    private final Boolean isAllowed;

    public ToolOSVersionView(String distribution, String version, Boolean isAllowed) {
        this.distribution = distribution;
        this.version = version;
        this.isAllowed = isAllowed;
    }

    public static ToolOSVersionView from(final ToolOSVersion osVersion, final boolean isAllowed) {
        return osVersion != null
                ? new ToolOSVersionView(osVersion.getDistribution(), osVersion.getVersion(), isAllowed)
                : null;
    }
}
