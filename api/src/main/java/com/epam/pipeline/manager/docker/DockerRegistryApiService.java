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

package com.epam.pipeline.manager.docker;

import com.epam.pipeline.controller.vo.docker.DockerRegistryVO;
import com.epam.pipeline.entity.docker.DockerRegistryList;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.DockerRegistryEventEnvelope;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.manager.security.acl.AclTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DockerRegistryApiService {

    @Autowired
    private DockerRegistryManager registryManager;

    @PreAuthorize("hasRole('ADMIN')")
    public DockerRegistry create(DockerRegistryVO dockerRegistryVO) {
        return registryManager.create(dockerRegistryVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#dockerRegistry, 'WRITE')")
    @AclTree
    public DockerRegistry updateDockerRegistry(DockerRegistry dockerRegistry) {
        return registryManager.updateDockerRegistry(dockerRegistry);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @AclTree
    public DockerRegistry updateDockerRegistryCredentials(DockerRegistryVO dockerRegistry) {
        return registryManager.updateDockerRegistryCredentials(dockerRegistry);
    }

    @AclTree
    public DockerRegistryList listDockerRegistriesWithCerts() {
        return registryManager.listAllDockerRegistriesWithCerts();
    }

    @AclTree
    public DockerRegistryList loadAllRegistriesContent() {
        return registryManager.loadAllRegistriesContent();
    }

    @AclTree
    public DockerRegistry load(Long id) {
        return registryManager.load(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.DockerRegistry', 'WRITE')")
    public DockerRegistry delete(Long id, boolean force) {
        return registryManager.delete(id, force);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Tool> notifyDockerRegistryEvents(String registry, DockerRegistryEventEnvelope events) {
        return registryManager.notifyDockerRegistryEvents(registry, events);
    }

    public JwtRawToken issueTokenForDockerRegistry(String userName, String token,
            String dockerRegistryHost, String scope) {
        return registryManager.issueTokenForDockerRegistry(userName, token, dockerRegistryHost, scope);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.DockerRegistry', 'READ')")
    public byte[] getCertificateContent(Long id) {
        return registryManager.getCertificateContent(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#id, 'com.epam.pipeline.entity.pipeline.DockerRegistry', 'READ')")
    public byte[] getConfigScript(Long id) {
        return registryManager.getConfigScript(id);
    }
}
