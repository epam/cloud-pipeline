/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.security.pipeline;

import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineType;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelinePermissionManager {

    private static final String WRITE = "WRITE";
    private static final String READ = "READ";

    private final CheckPermissionHelper permissionHelper;
    private final EntityManager entityManager;

    public boolean hasCreatePermission(final PipelineType type, final Long folderId) {
        return checkManagerRole(type) && folderId != null &&
                permissionHelper.isAllowed(WRITE, entityManager.load(AclClass.FOLDER, folderId));
    }

    public boolean hasManagePermission(final Long id) {
        final Pipeline pipeline = (Pipeline) entityManager.load(AclClass.PIPELINE, id);
        return hasManagePermission(pipeline);
    }

    public boolean hasManagePermission(final Pipeline pipeline) {
        return checkManagerRole(pipeline.getPipelineType()) && permissionHelper.isAllowed(WRITE, pipeline);
    }

    public boolean hasCopyPermission(final Long id, final Long folderId) {
        final Pipeline pipeline = (Pipeline) entityManager.load(AclClass.PIPELINE, id);
        return hasCopyPermission(pipeline, folderId);
    }

    public boolean hasCopyPermission(final Pipeline pipeline, final Long folderId) {
        return permissionHelper.isAllowed(READ, pipeline)
                && folderId != null && hasCreatePermission(pipeline.getPipelineType(), folderId)
                && checkManagerRole(pipeline.getPipelineType());
    }

    private boolean checkManagerRole(final PipelineType type) {
        final DefaultRoles roleToVerify;
        if (type == PipelineType.VERSIONED_STORAGE) {
            roleToVerify = DefaultRoles.ROLE_VERSIONED_STORAGE_MANAGER;
        } else {
            roleToVerify = DefaultRoles.ROLE_PIPELINE_MANAGER;
        }
        return permissionHelper.hasAnyRole(roleToVerify);
    }
}
