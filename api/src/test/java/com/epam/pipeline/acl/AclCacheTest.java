/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.dao.util.AclTestDao;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.pipeline.FolderManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.user.RoleManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.util.TestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class AclCacheTest extends AbstractManagerTest {

    private static final String TEST_FOLDER1 = "testFolder";
    private static final String TEST_OWNER = "owner";
    private static final String TEST_ROLE = "testRole";
    public static final String ROLE_ADMIN = "ADMIN";

    @Autowired
    private GrantPermissionManager permissionManager;

    @Autowired
    private RoleManager roleManager;

    @Autowired
    private UserManager userManager;

    @Autowired
    private FolderManager folderManager;

    @Autowired
    private AclTestDao aclTestDao;


    private Folder folder;
    private Role role;
    PipelineUser user;

    @Before
    public void setUp() {
        folder = folderManager.create(TestUtils.createFolder(TEST_FOLDER1, TEST_OWNER));
        role = roleManager.createRole(TEST_ROLE, false, false, null);
        user = userManager.create("TEST_USER", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), null);

        AclTestDao.AclSid userSid = new AclTestDao.AclSid(true, TEST_OWNER);
        aclTestDao.createAclSid(userSid);

        AclTestDao.AclSid roleSid = new AclTestDao.AclSid(false, role.getName());
        aclTestDao.createAclSid(roleSid);

        AclTestDao.AclClass folderAclClass = new AclTestDao.AclClass(Folder.class.getCanonicalName());
        aclTestDao.createAclClassIfNotPresent(folderAclClass);

    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_OWNER, roles = {ROLE_ADMIN})
    public void roleAuthorityRemovalTest() {
        grantPermissions(AclPermission.READ.getMask(), role.getName(), AclClass.FOLDER, folder.getId(), false);
        grantPermissions(AclPermission.READ.getMask(), user.getUserName(), AclClass.FOLDER, folder.getId(), true);
        final AclSecuredEntry permissions = permissionManager.getPermissions(folder.getId(), AclClass.FOLDER);
        final List<AclPermissionEntry> permissionEntries = permissions.getPermissions();

        assertEquals(2, permissionEntries.size());
        assertTrue(role.getName(),
                permissions.getPermissions().stream().anyMatch(acl -> acl.getSid().getName().equals(role.getName())));
        assertTrue(user.getUserName(),
                permissions.getPermissions().stream().anyMatch(acl ->
                        acl.getSid().getName().equals(user.getUserName())));

        roleManager.deleteRole(role.getId());
        AclSecuredEntry permissionsAfter = permissionManager.getPermissions(folder.getId(), AclClass.FOLDER);
        assertEquals(1, permissionsAfter.getPermissions().size());
        assertTrue(user.getUserName(),
                permissionsAfter.getPermissions().stream().allMatch(acl ->
                        acl.getSid().getName().equals(user.getUserName())));

        userManager.delete(user.getId());
        permissionsAfter = permissionManager.getPermissions(folder.getId(), AclClass.FOLDER);
        assertTrue(permissionsAfter.getPermissions().isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @WithMockUser(username = TEST_OWNER, roles = {ROLE_ADMIN})
    public void userAuthorityRemovalTest() {
        grantPermissions(AclPermission.READ.getMask(), role.getName(), AclClass.FOLDER, folder.getId(), false);
        final AclSecuredEntry permissions = permissionManager.getPermissions(folder.getId(), AclClass.FOLDER);
        final List<AclPermissionEntry> permissionEntries = permissions.getPermissions();
        assertEquals(1, permissionEntries.size());
        assertEquals(role.getName(), permissions.getPermissions().get(0).getSid().getName());
        roleManager.deleteRole(role.getId());
        final AclSecuredEntry permissionsAfter = permissionManager.getPermissions(folder.getId(), AclClass.FOLDER);
        assertTrue(CollectionUtils.isEmpty(permissionsAfter.getPermissions()));
    }


    private void grantPermissions(final Integer mask, final String user, final AclClass aclClass,
                                  final Long entityId, final boolean isPrincipal) {
        PermissionGrantVO grantVO = new PermissionGrantVO();
        grantVO.setAclClass(aclClass);
        grantVO.setId(entityId);
        grantVO.setMask(mask);
        grantVO.setPrincipal(isPrincipal);
        grantVO.setUserName(user);
        permissionManager.setPermissions(grantVO);
    }
}
