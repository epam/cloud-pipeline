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

package com.epam.pipeline.entity.security.acl;

import lombok.Getter;

@Getter
public enum AclClass {
    PIPELINE,
    FOLDER,
    DATA_STORAGE,
    DOCKER_REGISTRY,
    TOOL,
    TOOL_GROUP,
    CONFIGURATION,
    METADATA_ENTITY,
    ATTACHMENT,
    CLOUD_REGION,
    PIPELINE_USER(false),
    ROLE(false),
    // added only for backward compatibility with old APi versions
    @Deprecated
    AWS_REGION;

    private final boolean supportsEntityManager;

    AclClass() {
        this(true);
    }

    AclClass(final boolean supportsEntityManager) {
        this.supportsEntityManager = supportsEntityManager;
    }
}
