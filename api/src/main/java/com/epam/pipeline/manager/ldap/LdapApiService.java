package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.entity.ldap.LdapSearchRequest;
import com.epam.pipeline.entity.ldap.LdapSearchResponse;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LdapApiService {
    
    private final LdapManager ldapManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public LdapSearchResponse search(final LdapSearchRequest request) {
        return ldapManager.search(request);
    }
}
