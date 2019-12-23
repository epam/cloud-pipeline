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

package com.epam.pipeline.entity.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;

@Getter
@Setter
@Builder
public class PipelineUserWithStoragePath {

    @Delegate
    private PipelineUser pipelineUser;
    private String defaultStoragePath;

    @Getter
    public enum PipelineUserFields {

        ID("id"),
        USER_NAME("userName"),
        EMAIL("email"),
        ROLES("roles"),
        GROUPS("groups"),
        BLOCKED("blocked"),
        REGISTRATION_DATE("registrationDate"),
        FIRST_LOGIN_DATE("firstLoginDate"),
        ATTRIBUTES("attributes"),
        DEFAULT_STORAGE_ID("defaultStorageId"),
        DEFAULT_STORAGE_PATH("defaultStoragePath");

        private final String value;

        PipelineUserFields(final String value) {
            this.value = value;
        }
    }
}
