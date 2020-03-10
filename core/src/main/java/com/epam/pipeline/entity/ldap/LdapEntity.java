package com.epam.pipeline.entity.ldap;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
public class LdapEntity {
    private final String name;
    private final LdapEntityType type;
    private final Map<String, List<String>> attributes;
}
