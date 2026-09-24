/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.security.saml.proxy;

import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SAMLProxyAuthenticationProvider {

    private final UserAccessService accessService;

    public SAMLProxyAuthentication authenticate(final Authentication authentication,
                                                final String samlResponse,
                                                final ExternalServiceEndpoint endpoint) {
        final var principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();

        final String userName = principal.getName().toUpperCase();
        final Map<String, List<Object>> credentials = principal.getAttributes();
        final Map<String, String> attributes = readAttributes(credentials, endpoint.getSamlAttributes());
        final List<String> groups = readAuthorities(credentials, endpoint.getAuthorities());
        final UserContext userContext = accessService.parseUser(userName, groups, attributes);
        userContext.setExternal(endpoint.isExternal());
        log.debug("Found user by name {}", userName);

        return new SAMLProxyAuthentication(samlResponse, userContext);
    }

    private Map<String, String> readAttributes(final Map<String, List<Object>> attributes,
                                               final Set<String> samlAttributes) {
        if (MapUtils.isEmpty(attributes) || CollectionUtils.isEmpty(samlAttributes)) {
            return Collections.emptyMap();
        }
        return samlAttributes
                .stream()
                .filter(attribute -> attribute.contains("="))
                .map(attribute -> {
                    String[] splittedRecord = attribute.split("=");
                    String key = splittedRecord[0];
                    String value = splittedRecord[1];
                    if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                        log.error("Can not parse saml user attributes property.");
                        return null;
                    }
                    List<Object> attributeValues = attributes.get(value);
                    return ListUtils.emptyIfNull(attributeValues).stream()
                            .findFirst()
                            .map(o -> (String) o)
                            .map(v -> new ImmutablePair<>(key, v))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ImmutablePair::getLeft, ImmutablePair::getRight, (v1, v2) -> v1));
    }

    private List<String> readAuthorities(final Map<String, List<Object>> attributes,
                                         final List<String> authorities) {
        if (CollectionUtils.isEmpty(authorities) || MapUtils.isEmpty(attributes)) {
            return Collections.emptyList();
        }
        return authorities
                .stream()
                .filter(StringUtils::isNotBlank)
                .map(attributes::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(o -> (String) o)
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }
}
