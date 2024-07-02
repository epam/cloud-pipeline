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

package com.epam.pipeline.entity.app;

import lombok.Data;

import java.util.Map;

@Data
public class ApplicationInfo {

    private final String version;
    private final String prettyName;
    private final Map<String, String> components;

    public ApplicationInfo(final Map<String, String> components, final String prettyName) {
        this.version = getClass().getPackage().getImplementationVersion();
        this.prettyName = prettyName;
        this.components = components;
    }
}
