package com.epam.pipeline.exception.ldap;

public class LdapException extends RuntimeException {

    public LdapException(final String s) {
        super(s);
    }

    public LdapException(final String s, final Throwable throwable) {
        super(s, throwable);
    }
}
