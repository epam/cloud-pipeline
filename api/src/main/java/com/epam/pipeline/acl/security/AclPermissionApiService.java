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

package com.epam.pipeline.acl.security;

import static com.epam.pipeline.security.acl.AclExpressions.ACL_ENTITY_OWNER;

import com.epam.pipeline.controller.vo.EntityPermissionVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service public class AclPermissionApiService {

    @Autowired private GrantPermissionManager permissionManager;

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.ownerPermission(#grantVO.id, #grantVO.aclClass)")
    public AclSecuredEntry setPermissions(PermissionGrantVO grantVO) {
        return permissionManager.setPermissions(grantVO);
    }

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.metadataPermission(#id, #aclClass, 'READ')")
    public AclSecuredEntry getPermissions(Long id, AclClass aclClass) {
        return permissionManager.getPermissions(id, aclClass);
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public AclSecuredEntry deletePermissions(Long id, AclClass aclClass, String user, boolean isPrincipal) {
        return permissionManager.deletePermissions(id, aclClass, user, isPrincipal);
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public AclSecuredEntry deleteAllPermissions(Long id, AclClass aclClass) {
        return permissionManager.deleteAllPermissions(id, aclClass);
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public AclSecuredEntry changeOwner(Long id, AclClass aclClass, String userName) {
        return permissionManager.changeOwner(id, aclClass, userName);
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public EntityPermissionVO loadEntityPermission(final Long id, final AclClass aclClass) {
        return permissionManager.loadEntityPermission(aclClass, id);
    }
}
