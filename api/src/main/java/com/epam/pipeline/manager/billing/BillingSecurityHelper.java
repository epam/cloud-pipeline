package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingSecurityHelper {

    private final AuthManager authManager;

    public Map<String, List<String>> getFilters(final Map<String, List<String>> requestedFilters) {
        final Map<String, List<String>> filters = new HashMap<>(MapUtils.emptyIfNull(requestedFilters));
        final PipelineUser authorizedUser = authManager.getCurrentUser();
        if (!hasFullBillingAccess(authorizedUser)) {
            filters.put(BillingUtils.OWNER_FIELD, Collections.singletonList(authorizedUser.getUserName()));
        }
        return filters;
    }

    private boolean hasFullBillingAccess(final PipelineUser authorizedUser) {
        return authorizedUser.isAdmin()
                || authorizedUser.getRoles().stream()
                .map(Role::getName)
                .anyMatch(DefaultRoles.ROLE_BILLING_MANAGER.getName()::equals);
    }
}
