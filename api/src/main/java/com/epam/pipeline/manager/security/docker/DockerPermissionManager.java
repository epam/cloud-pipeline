/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.docker;

import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerPermissionManager {

    private final GrantPermissionManager permissionManager;
    private final AuthManager authManager;
    private final DockerRegistryManager registryManager;
    private final CheckPermissionHelper permissionHelper;

    public boolean hasDockerReadPermission(final Long registryId) {
        if (permissionHelper.isAdmin()) {
            return true;
        }
        final DockerRegistry fullTree = registryManager.getDockerRegistryTree(registryId);
        final String authorizedUser = authManager.getAuthorizedUser();
        if (permissionManager.isActionAllowedForUser(fullTree, authorizedUser, AclPermission.READ)) {
            return true;
        }
        permissionManager.filterTree(authorizedUser, fullTree, AclPermission.READ);
        return CollectionUtils.isNotEmpty(fullTree.getChildren());
    }
}
