package com.epam.pipeline.manager.utils;

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import org.apache.commons.collections4.ListUtils;

public final class UserUtils {

    private UserUtils() {
        //no op
    }

    public static boolean hasRole(final PipelineUser user, final DefaultRoles role) {
        return ListUtils.emptyIfNull(user.getRoles())
                .stream()
                .anyMatch(r -> role.getName().equals(r.getName()));
    }
}
