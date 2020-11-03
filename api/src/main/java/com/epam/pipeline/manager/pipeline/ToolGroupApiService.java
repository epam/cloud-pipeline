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

package com.epam.pipeline.manager.pipeline;

import java.util.List;

import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.pipeline.ToolGroupWithIssues;
import com.epam.pipeline.manager.pipeline.ToolGroupManager;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import com.epam.pipeline.manager.security.acl.AclTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ToolGroupApiService {
    @Autowired
    private ToolGroupManager toolGroupManager;

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('TOOL_GROUP_MANAGER') AND "
                  + "hasPermission(#group.registryId, 'com.epam.pipeline.entity.pipeline.DockerRegistry', 'WRITE'))")
    public ToolGroup create(final ToolGroup group) {
        return toolGroupManager.create(group);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#group, 'WRITE')")
    public ToolGroup update(final ToolGroup group) {
        return toolGroupManager.updateToolGroup(group);
    }

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject, 'READ')")
    @AclMaskList
    public List<ToolGroup> loadByRegistryId(Long registryId) {
        return toolGroupManager.loadByRegistryId(registryId);
    }

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject, 'READ')")
    @AclMaskList
    public List<ToolGroup> loadByRegistryNameOrId(String registry) {
        return toolGroupManager.loadByRegistryNameOrId(registry);
    }

    @AclTree
    public ToolGroup load(Long groupId) {
        return toolGroupManager.load(groupId);
    }

    @AclTree
    public ToolGroupWithIssues loadToolsWithIssuesCount(Long groupId) {
        return toolGroupManager.loadToolsWithIssuesCount(groupId);
    }

    @AclTree
    public ToolGroup loadByNameOrId(String groupId) {
        return toolGroupManager.loadByNameOrId(groupId);
    }

    public ToolGroup loadPrivate(Long registryId) {
        return toolGroupManager.loadPrivate(registryId);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.createPrivateToolGroupPermission(#registryId)")
    public ToolGroup createPrivate(Long registryId) {
        return toolGroupManager.createPrivate(registryId);
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('TOOL_GROUP_MANAGER') AND "
            + "@grantPermissionManager.toolGroupPermission(#id, 'WRITE'))")
    public ToolGroup delete(String id) {
        return toolGroupManager.delete(id, false);
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('TOOL_GROUP_MANAGER') AND "
            + "@grantPermissionManager.toolGroupChildPermission(#id, 'WRITE'))")
    public ToolGroup deleteForce(String id) {
        return toolGroupManager.delete(id, true);
    }
}
