package com.epam.pipeline.manager.security.user;

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPermissionsManager {

    private static final String ROLE_ADMIN_PREFIX = "ADMIN";

    private final UserManager userManager;
    private final CheckPermissionHelper permissionHelper;

    public boolean impersonatePermission(final UserContext details) {
        if (permissionHelper.isAdmin()) {
            return true;
        }
        final PipelineUser user = userManager.loadByNameOrId(details.getUsername());
        if (permissionHelper.hasAnyRole(DefaultRoles.ROLE_USER_ADMIN)) {
            // If userToImpersonate has any of ADMIN roles (see DefaultRoles for more details)
            // restrict impersonation for ROLE_USER_ADMIN
            return user.getRoles().stream().map(Role::getName)
                    .noneMatch(roleName -> roleName.contains(ROLE_ADMIN_PREFIX));
        }
        return permissionHelper.isAllowed("EXECUTE", user);
    }
}
