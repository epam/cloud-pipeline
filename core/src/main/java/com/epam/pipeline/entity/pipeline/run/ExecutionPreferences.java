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

package com.epam.pipeline.entity.pipeline.run;

import com.epam.pipeline.entity.configuration.ExecutionEnvironment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "environment",
        defaultImpl = CloudPlatformPreferences.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = FirecloudPreferences.class, name = "FIRECLOUD"),
        @JsonSubTypes.Type(value = CloudPlatformPreferences.class, name = "CLOUD_PLATFORM"),
        @JsonSubTypes.Type(value = DtsExecutionPreferences.class, name = "DTS")})
public interface ExecutionPreferences {

    @JsonIgnore
    ExecutionEnvironment getEnvironment();

    static ExecutionPreferences getDefault() {
        return new CloudPlatformPreferences();
    }
}
