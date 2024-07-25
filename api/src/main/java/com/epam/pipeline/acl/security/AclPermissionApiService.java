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
import com.epam.pipeline.eventsourcing.acl.ACLUpdateEventProducer;
import com.epam.pipeline.eventsourcing.Event;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service public class AclPermissionApiService {

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired(required = false)
    private ACLUpdateEventProducer aclUpdateEventProducer;

    @PreAuthorize("hasRole('ADMIN') or @grantPermissionManager.ownerPermission(#grantVO.id, #grantVO.aclClass)")
    public AclSecuredEntry setPermissions(PermissionGrantVO grantVO) {
        final AclSecuredEntry result = permissionManager.setPermissions(grantVO);
        notifyACLChange(grantVO.getId(), grantVO.getAclClass());
        return result;
    }

    @PreAuthorize("hasRole('ADMIN') or @metadataPermissionManager.metadataPermission(#id, #aclClass, 'READ')")
    public AclSecuredEntry getPermissions(Long id, AclClass aclClass) {
        return permissionManager.getPermissions(id, aclClass);
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public AclSecuredEntry deletePermissions(Long id, AclClass aclClass, String user, boolean isPrincipal) {
        final AclSecuredEntry result = permissionManager.deletePermissions(id, aclClass, user, isPrincipal);
        notifyACLChange(id, aclClass);
        return result;
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public AclSecuredEntry deleteAllPermissions(Long id, AclClass aclClass) {
        final AclSecuredEntry result = permissionManager.deleteAllPermissions(id, aclClass);
        notifyACLChange(id, aclClass);
        return result;
    }

    @PreAuthorize(ACL_ENTITY_OWNER)
    public AclSecuredEntry changeOwner(Long id, AclClass aclClass, String userName) {
        final AclSecuredEntry result = permissionManager.changeOwner(id, aclClass, userName);
        notifyACLChange(id, aclClass);
        return result;
    }

    @PreAuthorize("hasRole('ADMIN') or @metadataPermissionManager.metadataPermission(#id, #aclClass, 'READ')")
    public EntityPermissionVO loadEntityPermission(final Long id, final AclClass aclClass) {
        return permissionManager.loadEntityPermission(aclClass, id);
    }

    private void notifyACLChange(final long id, final AclClass aclClass) {
        if (aclUpdateEventProducer != null) {
            aclUpdateEventProducer.put(id, aclClass);
        }
    }
}
