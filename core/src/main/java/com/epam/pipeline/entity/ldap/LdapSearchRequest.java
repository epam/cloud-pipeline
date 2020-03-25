package com.epam.pipeline.entity.ldap;

import lombok.Value;

@Value
public class LdapSearchRequest {
    private final String query;
    private final LdapEntityType type;
    
    public static LdapSearchRequest forUser(final String query) {
        return new LdapSearchRequest(query, LdapEntityType.USER);
    }
    
    public static LdapSearchRequest forGroup(final String query) {
        return new LdapSearchRequest(query, LdapEntityType.GROUP);
    }
}
