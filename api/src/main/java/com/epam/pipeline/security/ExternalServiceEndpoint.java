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

package com.epam.pipeline.security;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents an external service, that can authenticate into Pipeline API via Proxy authentication.
 * Key fields are endpointID (e.g. https://localhost:9001/proxy)
 * and metadataPath (e.g. /home/pipeline/api/sso/FederationMetadata.xml)
 */
@Getter
@Setter
public class ExternalServiceEndpoint {
    private String endpointId;
    private String metadataPath;

    /**
     * Defines if service is exteranl to Pipeline and therefore will receive an external JWT token
     */
    private boolean external = true;
}
