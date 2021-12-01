package com.epam.pipeline.entity.ldap;

public enum  LdapSearchResponseType {
    /**
     * Completed LDAP response contains all the found entities.
     */
    COMPLETED,

    /**
     * Truncated LDAP response contains only a part of the found entities.
     */
    TRUNCATED,

    /**
     * Timed out LDAP response contains no entities because the corresponding request took too much time
     * and was aborted.
     */
    TIMED_OUT
}
