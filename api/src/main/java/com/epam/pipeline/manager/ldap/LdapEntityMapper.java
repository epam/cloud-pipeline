package com.epam.pipeline.manager.ldap;

import com.epam.pipeline.entity.ldap.LdapEntity;
import com.epam.pipeline.entity.ldap.LdapEntityType;
import com.epam.pipeline.exception.ldap.LdapException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class LdapEntityMapper {
    
    private static final String FALLBACK_ENTITY_NAME_ATTRIBUTE = "cn";
    
    private final PreferenceManager preferenceManager;

    public LdapEntity map(final Attributes attributes, final LdapEntityType type) {
        final Map<String, List<String>> attributesMap = resolveAttributes(attributes);
        return new LdapEntity(nameFrom(attributesMap), type, attributesMap);
    }

    private Map<String, List<String>> resolveAttributes(final Attributes attributes) {
        return Collections.list(attributes.getIDs())
                .stream()
                .map(attributes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Attribute::getID, this::toValues));
    }

    private List<String> toValues(final Attribute attribute) {
        try {
            return Collections.list(attribute.getAll()).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } catch (NamingException e) {
            throw new LdapException("Attribute values extraction has failed", e);
        }
    }

    private String nameFrom(final Map<String, List<String>> attributeValues) {
        return Optional.of(nameAttribute())
                .map(attributeValues::get)
                .map(List::stream)
                .map(Stream::findFirst)
                .flatMap(Function.identity())
                .orElse("Name not found");
    }

    private String nameAttribute() {
        return preferenceManager.findPreference(SystemPreferences.LDAP_NAME_ATTRIBUTE)
                .orElse(FALLBACK_ENTITY_NAME_ATTRIBUTE);
    }
}
