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

package com.epam.pipeline.controller.vo.docker;

import com.epam.pipeline.entity.docker.DockerRegistrySecret;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DockerRegistryVO {
    private Long id;
    private String path;
    private String description;
    private String userName;
    private String password;
    private String caCert;
    private boolean pipelineAuth = false;
    private String externalUrl;
    private boolean securityScanEnabled;

    public DockerRegistry convertToDockerRegistry() {
        DockerRegistry registry = new DockerRegistry();
        registry.setId(getId());
        registry.setPath(getPath());
        registry.setDescription(getDescription());
        registry.setCaCert(getCaCert());
        registry.setUserName(getUserName());
        registry.setPassword(getPassword());
        registry.setPipelineAuth(isPipelineAuth());
        registry.setExternalUrl(getExternalUrl());
        registry.setSecurityScanEnabled(isSecurityScanEnabled());
        return registry;
    }

    public DockerRegistrySecret convertToSecret() {
        return DockerRegistrySecret
                .builder()
                .userName(getUserName())
                .password(getPassword())
                .registryUrl(getPath())
                .build();
    }
}
