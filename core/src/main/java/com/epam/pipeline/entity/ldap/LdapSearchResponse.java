package com.epam.pipeline.entity.ldap;

import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value
public class LdapSearchResponse {
    private final List<LdapEntity> entities;
    private final LdapSearchResponseType type;

    public static LdapSearchResponse completed(final List<LdapEntity> entities) {
        return new LdapSearchResponse(entities, LdapSearchResponseType.COMPLETED);
    }

    public static LdapSearchResponse truncated(final List<LdapEntity> entities) {
        return new LdapSearchResponse(entities, LdapSearchResponseType.TRUNCATED);
    }
    
    public static LdapSearchResponse timedOut() {
        return new LdapSearchResponse(Collections.emptyList(), LdapSearchResponseType.TIMED_OUT);
    }
}
