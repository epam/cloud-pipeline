package com.epam.pipeline.manager.security.user;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPermissionsManager {

    private final UserManager userManager;
    private final CheckPermissionHelper permissionHelper;

    public boolean impersonatePermission(final UserContext details) {
        if (permissionHelper.isAdmin()) {
            return true;
        }
        final PipelineUser user = userManager.loadByNameOrId(details.getUsername());
        return permissionHelper.isAllowed("EXECUTE", user);
    }
}
