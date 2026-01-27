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

package com.epam.pipeline.security.saml;

import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SamlResponseAuthenticationConverter {

    private static final String ATTRIBUTES_DELIMITER = "=";

    private final List<String> authorities;
    private final Set<String> samlAttributes;
    private final String blockedAttribute;
    private final String blockedAttributeTrueValue;
    private final UserManager userManager;
    private final UserAccessService accessService;

    public SamlResponseAuthenticationConverter(
            @Value("${saml.authorities.attribute.names: null}") final List<String> authorities,
            @Value("#{'${saml.user.attributes}'.split(',')}") final Set<String> samlAttributes,
            @Value("${saml.user.blocked.attribute: }") final String blockedAttribute,
            @Value("${saml.user.blocked.attribute.true.val: true}") final String blockedAttributeTrueValue,
            final UserManager userManager,
            final UserAccessService accessService) {
        this.authorities = authorities;
        this.samlAttributes = samlAttributes;
        this.blockedAttribute = blockedAttribute;
        this.blockedAttributeTrueValue = blockedAttributeTrueValue;
        this.userManager = userManager;
        this.accessService = accessService;
    }

    public AbstractAuthenticationToken covert(final OpenSaml4AuthenticationProvider.ResponseToken responseToken) {
        final Saml2Authentication authentication = OpenSaml4AuthenticationProvider
                .createDefaultResponseAuthenticationConverter()
                .convert(responseToken);
        if (Objects.isNull(authentication)) {
            log.debug("Cannot parse SAML2 response.");
            return null;
        }
        final var principal = (DefaultSaml2AuthenticatedPrincipal) authentication.getPrincipal();
        final Map<String, List<Object>> credentials = principal.getAttributes();
        final List<String> groups = readAuthorities(credentials);
        final UserContext userContext = loadUserBySAML(credentials, principal, groups);

        final List<GrantedAuthority> authorities = ListUtils.emptyIfNull(groups).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        authorities.addAll(authentication.getAuthorities());

        final var authenticationToken = new UsernamePasswordAuthenticationToken(
                userContext, authentication.getSaml2Response(), authorities);
        authenticationToken.setDetails(responseToken.getToken().getDetails());
        return authenticationToken;
    }

    private UserContext loadUserBySAML(final Map<String, List<Object>> credentials,
                                       final DefaultSaml2AuthenticatedPrincipal principal,
                                       final List<String> groups) {
        final String userName = principal.getName().toUpperCase();
        final Map<String, String> attributes = readAttributes(credentials);
        final UserContext userContext = accessService.parseUser(userName, groups, attributes);
        accessService.validateUserGroupsBlockStatus(userContext.toPipelineUser());
        if (hasBlockedStatusAttribute(credentials)) {
            Optional.ofNullable(userContext.getUserId())
                    .ifPresent(id -> userManager.updateUserBlockingStatus(id, true));
            accessService.throwUserIsBlocked(userName);
        }
        log.info("Successfully authenticate user: {}", userContext.getUsername());
        return userContext;
    }

    private boolean hasBlockedStatusAttribute(final Map<String, List<Object>> attributes) {
        final String blockingStatus = getAttributeAsString(attributes, blockedAttribute);
        return StringUtils.isNotEmpty(blockingStatus)
                && blockingStatus.equalsIgnoreCase(blockedAttributeTrueValue);
    }

    List<String> readAuthorities(final Map<String, List<Object>> attributes) {
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

    Map<String, String> readAttributes(final Map<String, List<Object>> attributes) {
        if (CollectionUtils.isEmpty(samlAttributes)) {
            return Collections.emptyMap();
        }
        final Map<String, String> parsedAttributes = new HashMap<>();
        for (String attribute : samlAttributes) {
            if (attribute.contains(ATTRIBUTES_DELIMITER)) {
                final String[] splittedRecord = attribute.split(ATTRIBUTES_DELIMITER);
                final String key = splittedRecord[0];
                final String value = splittedRecord[1];
                if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
                    log.error("Can not parse saml user attributes property.");
                    continue;
                }
                final String attributeValues = getAttributeAsString(attributes, value);
                if (StringUtils.isNotEmpty(attributeValues)) {
                    parsedAttributes.put(key, attributeValues);
                }
            }
        }
        return parsedAttributes;
    }

    private String getAttributeAsString(final Map<String, List<Object>> attributes,
                                        final String value) {
        // This part duplicates method SAMLCredential#getAttributeAsString behaviour:
        // It returns text content of the first AttributeValue element.
        // In case there's multiple AttributeValues, the others are ignored.
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return ListUtils.emptyIfNull(attributes.get(value)).stream().findFirst()
                .map(Object::toString)
                .orElse(Strings.EMPTY);
    }
}
