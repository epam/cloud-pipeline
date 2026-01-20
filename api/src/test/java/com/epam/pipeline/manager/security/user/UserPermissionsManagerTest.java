package com.epam.pipeline.manager.security.user;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class UserPermissionsManagerTest extends AbstractAclTest {

    private static final String USER = "USER";
    private static final String USER_TO_IMPERSONATE = "USER_1";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER_ADMIN = "USER_ADMIN";
    private static final String ROLE_USER = "USER";


    @Autowired
    private UserPermissionsManager userPermissionsManager;

    @Mock
    private UserManager userManager;

    @Test
    @WithMockUser(username = USER, roles = ROLE_ADMIN)
    public void adminCanImpersonate() {
        mockAuthUser(USER);
        PipelineUser adminUser = new PipelineUser(USER_TO_IMPERSONATE);
        adminUser.setRoles(Collections.singletonList(new Role(ROLE_ADMIN)));
        Mockito.when(userManager.loadByNameOrId(USER_TO_IMPERSONATE))
                .thenReturn(adminUser);
        assertTrue(userPermissionsManager.impersonatePermission(new UserContext(1L, USER_TO_IMPERSONATE)));
    }

    @Test
    @WithMockUser(username = USER, roles = ROLE_USER_ADMIN)
    public void userAdminCantImpersonateAsAdmin() {
        final List<Role> userToImpersonateRoles = Collections.singletonList(new Role(ROLE_ADMIN));
        final PipelineUser adminUserToImpersonate = new PipelineUser(USER_TO_IMPERSONATE);
        adminUserToImpersonate.setRoles(userToImpersonateRoles);
        final UserContext userToImpersonateContext = new UserContext(1L, USER_TO_IMPERSONATE);
        userToImpersonateContext.setRoles(userToImpersonateRoles);

        Mockito.when(userManager.loadByNameOrId(USER_TO_IMPERSONATE))
                .thenReturn(adminUserToImpersonate);
        mockAuthUser(USER);
        mockUser(adminUserToImpersonate);
        assertFalse(userPermissionsManager.impersonatePermission(userToImpersonateContext));
    }

    @Test
    @WithMockUser(username = USER, roles = ROLE_USER_ADMIN)
    public void userAdminCantImpersonateAsScopedAdmin() {
        final List<Role> userToImpersonateRoles = Collections.singletonList(new Role(ROLE_USER_ADMIN));
        final PipelineUser scopedAdminUserToImpersonate = new PipelineUser(USER_TO_IMPERSONATE);
        scopedAdminUserToImpersonate.setRoles(userToImpersonateRoles);
        final UserContext userToImpersonateContext = new UserContext(1L, USER_TO_IMPERSONATE);
        userToImpersonateContext.setRoles(userToImpersonateRoles);

        Mockito.when(userManager.loadByNameOrId(USER_TO_IMPERSONATE))
                .thenReturn(scopedAdminUserToImpersonate);
        mockAuthUser(USER);
        mockUser(scopedAdminUserToImpersonate);
        assertFalse(userPermissionsManager.impersonatePermission(userToImpersonateContext));
    }

    @Test
    @WithMockUser(username = USER, roles = ROLE_USER_ADMIN)
    public void userAdminCanImpersonateAsSimpleUser() {
        final List<Role> userToImpersonateRoles = Collections.singletonList(new Role(ROLE_USER));
        final PipelineUser userToImpersonate = new PipelineUser(USER_TO_IMPERSONATE);
        userToImpersonate.setRoles(userToImpersonateRoles);
        final UserContext userToImpersonateContext = new UserContext(1L, USER_TO_IMPERSONATE);
        userToImpersonateContext.setRoles(userToImpersonateRoles);

        Mockito.when(userManager.loadByNameOrId(USER_TO_IMPERSONATE))
                .thenReturn(userToImpersonate);
        mockAuthUser(USER);
        mockUser(userToImpersonate);
        assertTrue(userPermissionsManager.impersonatePermission(userToImpersonateContext));
    }

}