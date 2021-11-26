package com.epam.pipeline.entity.ldap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class LdapSearchRequest {
    private String query;
    private LdapEntityType type;
    private Integer size;
    private List<String> attributes;
    private String nameAttribute;
}
