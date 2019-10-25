package com.epam.pipeline.acl.run;

import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.security.acl.JdbcMutableAclServiceImpl;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
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

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

@RequiredArgsConstructor
public class PipelineAclFactory {
    public static final Long TEST_PIPELINE_ID = 10L;

    private final AuthManager authManager;
    private final GrantPermissionManager grantPermissionManager;
    private final JdbcMutableAclServiceImpl aclService;

    private final UserManager mockUserManager;
    private final PipelineManager mockPipelineManager;
    private final EntityManager mockEntityManager;

    public Pipeline initPipelineForCurrentUser() {
        return initPipelineForOwner(authManager.getAuthorizedUser());
    }

    public Pipeline initPipelineForOwner(final String owner) {
        return initPipelineForOwnerWithPermissions(owner, null, 0);
    }

    public Pipeline initPipelineForOwnerWithPermissions(final String owner,
                                                         final String user,
                                                         final int permissionMask) {
        final Pipeline pipeline = new Pipeline();
        pipeline.setId(TEST_PIPELINE_ID);
        pipeline.setOwner(owner);
        doReturn(pipeline)
                .when(mockPipelineManager)
                .load(eq(TEST_PIPELINE_ID));
        doReturn(pipeline)
                .when(mockPipelineManager)
                .load(eq(TEST_PIPELINE_ID), anyBoolean());
        aclService.getOrCreateObjectIdentity(pipeline);
        // we need to grant permissions before changing the owner,
        // because it may cause AccessDeniedException for non-owner, non-admin user
        Optional.ofNullable(user)
                .ifPresent(userName -> grantPermission(pipeline, userName, permissionMask));
        if (!owner.equals(authManager.getAuthorizedUser())) {
            doReturn(new UserContext(null, owner))
                    .when(mockUserManager)
                    .loadUserContext(eq(owner));
            doReturn(pipeline)
                    .when(mockEntityManager)
                    .load(eq(AclClass.PIPELINE), eq(TEST_PIPELINE_ID));
            grantPermissionManager.changeOwner(TEST_PIPELINE_ID, AclClass.PIPELINE, owner);
        }
        return pipeline;
    }

    private void grantPermission(final AbstractSecuredEntity entity,
                                 final String userName,
                                 final int mask) {
        final PermissionGrantVO permissionGrant = new PermissionGrantVO();
        permissionGrant.setAclClass(entity.getAclClass());
        permissionGrant.setId(entity.getId());
        permissionGrant.setPrincipal(true);
        permissionGrant.setUserName(userName);
        permissionGrant.setMask(mask);
        doReturn(entity)
                .when(mockEntityManager)
                .load(eq(entity.getAclClass()), eq(entity.getId()));
        grantPermissionManager.setPermissions(permissionGrant);
    }
}
