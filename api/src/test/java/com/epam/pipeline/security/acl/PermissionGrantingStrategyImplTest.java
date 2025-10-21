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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.acls.domain.*;
import org.springframework.security.acls.model.NotFoundException;

import java.util.Arrays;
import java.util.Collections;

public class PermissionGrantingStrategyImplTest {

    public static final String FOLDER_ENTITY_TYPE = "Folder";
    public static final long IDENTIFIER = 1L;
    public static final long IDENTIFIER_2 = 2L;

    PrincipalSid userSid;
    PrincipalSid user2Sid;
    GrantedAuthoritySid groupSid;
    GrantedAuthoritySid roleUserSid;

    PermissionGrantingStrategyImpl permissionGrantingStrategy;

    @Before
    public void setUp() throws Exception {
        userSid = new PrincipalSid("USER");
        user2Sid = new PrincipalSid("USER2");
        groupSid = new GrantedAuthoritySid("GROUP");
        roleUserSid = new GrantedAuthoritySid("ROLE_USER");
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

        Assert.assertTrue(
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

        Assert.assertTrue(
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

        Assert.assertTrue(
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

        Assert.assertTrue(
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

        Assert.assertFalse(
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

        Assert.assertFalse(
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

        Assert.assertTrue(
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

        Assert.assertFalse(
                permissionGrantingStrategy.isGranted(
                        folder2, Collections.singletonList(AclPermission.READ),
                        Collections.singletonList(user2Sid), false
                )
        );
    }
}
