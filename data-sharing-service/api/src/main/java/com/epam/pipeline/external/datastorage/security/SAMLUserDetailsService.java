/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.security;

import com.epam.pipeline.external.datastorage.exception.PipelineAuthenticationException;
import com.epam.pipeline.external.datastorage.manager.auth.PipelineAuthManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SAMLUserDetailsService {

    private static final String ATTRIBUTES_DELIMITER = "=";

    private final List<String> authorities;
    private final Set<String> samlAttributes;
    private final PipelineAuthManager pipelineAuthManager;

    public SAMLUserDetailsService(
            @Value("${saml.authorities.attribute.names: null}") final List<String> authorities,
            @Value("#{'${saml.user.attributes}'.split(',')}") final Set<String> samlAttributes,
            final PipelineAuthManager pipelineAuthManager) {
        this.authorities = authorities;
        this.samlAttributes = samlAttributes;
        this.pipelineAuthManager = pipelineAuthManager;
    }

    public UserContext loadUserBySAML(final DefaultSaml2AuthenticatedPrincipal credential,
                                      final String rawSamlResponse) {
        final String userName = credential.getName().toUpperCase();
        final Map<String, List<Object>> attributes = credential.getAttributes();
        final List<String> groups = readAuthorities(attributes);
        final Map<String, String> parsedAttributes = readAttributes(attributes);

        final UserContext userContext = new UserContext(userName);
        userContext.setGroups(groups);
        userContext.setAttributes(parsedAttributes);

        try {
            final String token = pipelineAuthManager.getToken(rawSamlResponse);
            userContext.setToken(token);
        } catch (PipelineAuthenticationException e) {
            throw new UsernameNotFoundException(e.getMessage(), e);
        }

        return userContext;
    }

    private List<String> readAuthorities(final Map<String, List<Object>> attributes) {
        return ListUtils.emptyIfNull(authorities).stream()
                .filter(StringUtils::isNotBlank)
                .map(authName -> getGroupsFromArrayValue(attributes, authName))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<String> getGroupsFromArrayValue(final Map<String, List<Object>> attributes,
                                                 final String authName) {
        return ListUtils.emptyIfNull(attributes.get(authName)).stream()
                .filter(Objects::nonNull)
                .map(o -> (String) o)
                .filter(StringUtils::isNotBlank)
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    private Map<String, String> readAttributes(final Map<String, List<Object>> attributes) {
        if (CollectionUtils.isEmpty(samlAttributes)) {
            return Collections.emptyMap();
        }
        final Map<String, String> parsedAttributes = new HashMap<>();
        for (String attribute : samlAttributes) {
            if (attribute.contains(ATTRIBUTES_DELIMITER)) {
                final String[] parts = attribute.split(ATTRIBUTES_DELIMITER);
                final String key = parts[0];
                final String value = parts[1];
                if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                    log.error("Cannot parse SAML user attributes property: {}", attribute);
                    continue;
                }
                final String attrValue = ListUtils.emptyIfNull(attributes.get(value)).stream()
                        .findFirst()
                        .map(Object::toString)
                        .orElse(null);
                if (StringUtils.isNotEmpty(attrValue)) {
                    parsedAttributes.put(key, attrValue);
                }
            }
        }
        return parsedAttributes;
    }
}
