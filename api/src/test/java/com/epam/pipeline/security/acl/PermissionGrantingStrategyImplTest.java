/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.security.acl;

import com.epam.pipeline.manager.security.PermissionsService;
import com.epam.pipeline.security.acl.redis.AllowAllAuthStrategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.acls.domain.*;
import org.springframework.security.acls.model.NotFoundException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PermissionGrantingStrategyImplTest {

    public static final String FOLDER_ENTITY_TYPE = "com.epam.pipeline.entity.pipeline.Folder";
    public static final String PIPELINE_ENTITY_TYPE = "com.epam.pipeline.entity.pipeline.Pipeline";
    public static final String RUN_CONFIGURATION_ENTITY_TYPE =
            "com.epam.pipeline.entity.configuration.RunConfiguration";

    public static final String PIPELINE_RUN_ENTITY_TYPE = "com.epam.pipeline.entity.pipeline.PipelineRun";

    public static final String S3_STORAGE_ENTITY_TYPE = "com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage";
    public static final String GS_STORAGE_ENTITY_TYPE = "com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage";
    public static final String AZ_STORAGE_ENTITY_TYPE = "com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage";
    public static final String NFS_STORAGE_ENTITY_TYPE = "com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage";

    public static final String USER_ENTITY_TYPE = "com.epam.pipeline.entity.user.PipelineUser";
    public static final String ROLE_ENTITY_TYPE = "com.epam.pipeline.entity.user.Role";

    public static final String TOOL_ENTITY_TYPE = "com.epam.pipeline.entity.pipeline.Tool";
    public static final String TOOL_GROUP_ENTITY_TYPE = "com.epam.pipeline.entity.pipeline.ToolGroup";
    public static final String REGISTRY_ENTITY_TYPE = "com.epam.pipeline.entity.pipeline.DockerRegistry";

    public static final long IDENTIFIER = 1L;
    public static final long IDENTIFIER_2 = 2L;

    PrincipalSid userSid;
    PrincipalSid user2Sid;
    GrantedAuthoritySid groupSid;
    GrantedAuthoritySid roleUserSid;
    GrantedAuthoritySid scopedRunAdminSid;
    GrantedAuthoritySid scopedStorageAdminSid;
    GrantedAuthoritySid scopedToolAdminSid;
    GrantedAuthoritySid scopedPipelineAdminSid;
    GrantedAuthoritySid scopedUserAdminSid;

    PermissionGrantingStrategyImpl permissionGrantingStrategy;

    @Before
    public void setUp() throws Exception {
        userSid = new PrincipalSid("USER");
        user2Sid = new PrincipalSid("USER2");
        groupSid = new GrantedAuthoritySid("GROUP");
        roleUserSid = new GrantedAuthoritySid("ROLE_USER");
        scopedRunAdminSid = new GrantedAuthoritySid("ROLE_RUN_ADMIN");
        scopedStorageAdminSid = new GrantedAuthoritySid("ROLE_STORAGE_ADMIN");
        scopedToolAdminSid = new GrantedAuthoritySid("ROLE_TOOL_ADMIN");
        scopedPipelineAdminSid = new GrantedAuthoritySid("ROLE_PIPELINE_ADMIN");
        scopedUserAdminSid = new GrantedAuthoritySid("ROLE_USER_ADMIN");

        permissionGrantingStrategy = new PermissionGrantingStrategyImpl(
                Mockito.mock(AuditLogger.class), new PermissionsService()
        );
    }

    @Test
    public void permissionShouldBeGrantedIfEntityACLHasRequiredACE() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, userSid, true);

        assertTrue(
            permissionGrantingStrategy.isGranted(
                    folder, Collections.singletonList(AclPermission.READ),
                    Collections.singletonList(userSid), false
            )
        );
    }

    @Test
    public void permissionShouldBeGrantedIfUserIsOwner() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, userSid, true);

        assertTrue(
            permissionGrantingStrategy.isGranted(
                folder, Collections.singletonList(AclPermission.READ),
                Collections.singletonList(userSid), false
            )
        );
    }

    @Test(expected = NotFoundException.class)
    public void shouldThrowIfEntityACLDoesNotHaveRequiredACE() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        permissionGrantingStrategy.isGranted(
                folder, Collections.singletonList(AclPermission.READ),
                Collections.singletonList(user2Sid), false
        );
    }

    @Test
    public void permissionShouldBeGrantedIfUserHasRoleWhichHasAccess() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, groupSid, true);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        folder, Collections.singletonList(AclPermission.READ),
                        Arrays.asList(user2Sid, groupSid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedIfUserHasAccessAndGroupHasDeny() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, user2Sid, true);
        folder.insertAce(0, AclPermission.NO_READ, groupSid, false);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        folder, Collections.singletonList(AclPermission.READ),
                        Arrays.asList(user2Sid, groupSid), false
                )
        );
    }

    @Test
    public void permissionShouldNotBeGrantedIfUserHasDenyAndGroupHasAccess() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, groupSid, true);
        folder.insertAce(0, AclPermission.NO_READ, user2Sid, false);

        assertFalse(
                permissionGrantingStrategy.isGranted(
                        folder, Collections.singletonList(AclPermission.READ),
                        Arrays.asList(user2Sid, groupSid), false
                )
        );
    }

    @Test
    public void permissionShouldNotBeGrantedIfGroupHasDenyAndRoleHasAccess() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, roleUserSid, true);
        folder.insertAce(0, AclPermission.NO_READ, groupSid, false);

        assertFalse(
                permissionGrantingStrategy.isGranted(
                        folder, Collections.singletonList(AclPermission.READ),
                        Arrays.asList(user2Sid, groupSid, roleUserSid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedIfUserHasAccessToEntityButHasDenyForParent() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        AclImpl folder2 = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER_2), IDENTIFIER_2,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, folder,
                null, true, userSid);
        folder.insertAce(0, AclPermission.NO_READ, user2Sid, false);
        folder2.insertAce(0, AclPermission.READ, user2Sid, true);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        folder2, Collections.singletonList(AclPermission.READ),
                        Collections.singletonList(user2Sid), false
                )
        );
    }

    @Test
    public void permissionShouldNotBeGrantedIfUserHasDenyToEntityButHasAllowForParent() {
        AclImpl folder = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);
        AclImpl folder2 = new AclImpl(new ObjectIdentityImpl(FOLDER_ENTITY_TYPE, IDENTIFIER_2), IDENTIFIER_2,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, folder,
                null, true, userSid);
        folder.insertAce(0, AclPermission.READ, user2Sid, true);
        folder2.insertAce(0, AclPermission.NO_READ, user2Sid, false);

        assertFalse(
                permissionGrantingStrategy.isGranted(
                        folder2, Collections.singletonList(AclPermission.READ),
                        Collections.singletonList(user2Sid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedForPipelineIfUserHasPipelineAdmin() {
        AclImpl pipeline = new AclImpl(new ObjectIdentityImpl(PIPELINE_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        pipeline, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedPipelineAdminSid), false
                )
        );

        AclImpl configuration = new AclImpl(new ObjectIdentityImpl(RUN_CONFIGURATION_ENTITY_TYPE, IDENTIFIER),
                IDENTIFIER, new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        configuration, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedPipelineAdminSid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedForUserOrGroupIfUserHasUserAdmin() {
        AclImpl user = new AclImpl(new ObjectIdentityImpl(USER_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        user, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedUserAdminSid), false
                )
        );

        AclImpl role = new AclImpl(new ObjectIdentityImpl(ROLE_ENTITY_TYPE, IDENTIFIER),
                IDENTIFIER, new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        role, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedUserAdminSid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedForDatastorageIfUserHasStorageAdmin() {
        AclImpl s3Storage = new AclImpl(new ObjectIdentityImpl(S3_STORAGE_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        s3Storage, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedStorageAdminSid), false
                )
        );

        AclImpl gsStorage = new AclImpl(new ObjectIdentityImpl(GS_STORAGE_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        gsStorage, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedStorageAdminSid), false
                )
        );

        AclImpl azStorage = new AclImpl(new ObjectIdentityImpl(AZ_STORAGE_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        azStorage, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedStorageAdminSid), false
                )
        );

        AclImpl nfsStorage = new AclImpl(new ObjectIdentityImpl(NFS_STORAGE_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        nfsStorage, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedStorageAdminSid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedForPipelineRunIfUserHasPipelineRunAdmin() {
        AclImpl pipelineRun = new AclImpl(new ObjectIdentityImpl(PIPELINE_RUN_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        pipelineRun, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedRunAdminSid), false
                )
        );
    }

    @Test
    public void permissionShouldBeGrantedForToolEntitiesIfUserHasToolAdmin() {
        AclImpl tool = new AclImpl(new ObjectIdentityImpl(TOOL_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        tool, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedToolAdminSid), false
                )
        );

        AclImpl toolGroup = new AclImpl(new ObjectIdentityImpl(TOOL_GROUP_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        toolGroup, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedToolAdminSid), false
                )
        );

        AclImpl registry = new AclImpl(new ObjectIdentityImpl(REGISTRY_ENTITY_TYPE, IDENTIFIER), IDENTIFIER,
                new AllowAllAuthStrategy(), permissionGrantingStrategy, null,
                null, true, userSid);

        assertTrue(
                permissionGrantingStrategy.isGranted(
                        registry, Collections.singletonList(AclPermission.OWNER),
                        Arrays.asList(user2Sid, scopedToolAdminSid), false
                )
        );
    }
}
