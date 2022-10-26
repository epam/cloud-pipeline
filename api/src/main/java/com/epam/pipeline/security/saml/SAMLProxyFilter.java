/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.coveo.saml.SamlResponse;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opensaml.common.SAMLException;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.Response;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.schema.XSString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_EXTERNAL_SERVICES_ENDPOINTS;

public class SAMLProxyFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAMLProxyFilter.class);
    private static final int RESPONSE_SKEW = 1200;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private UserAccessService accessService;

    @Value("${saml.authn.max.authentication.age:93600}")
    private Long maxAuthentificationAge;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        if (!urlMatches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<ExternalServiceEndpoint> externalServices = preferenceManager.getPreference(
            SYSTEM_EXTERNAL_SERVICES_ENDPOINTS);
        if (CollectionUtils.isEmpty(externalServices)) {
            LOGGER.warn(messageHelper.getMessage(MessageConstants.ERROR_PROXY_SECURITY_CONFIG_MISSING));
        } else {
            String samlResponse = request.getParameter("SAMLResponse");
            if (StringUtils.isNotBlank(samlResponse)) {
                try {
                    Response decoded = CustomSamlClient.decodeSamlResponse(samlResponse);

                    String audience = ListUtils.emptyIfNull(decoded.getAssertions())
                            .stream().findFirst()
                            .map(Assertion::getConditions)
                            .map(conditions ->
                                    ListUtils.emptyIfNull(conditions.getAudienceRestrictions()).stream().findFirst())
                            .flatMap(Function.identity())
                            .map(audienceRestriction ->
                                    ListUtils.emptyIfNull(audienceRestriction.getAudiences()).stream().findFirst())
                            .flatMap(Function.identity())
                            .map(Audience::getAudienceURI)
                            .orElse(StringUtils.EMPTY);

                    LOGGER.debug("Received SAMLResponse for audience: {}", audience);

                    Optional<ExternalServiceEndpoint> endpointOpt = externalServices.stream()
                        .filter(e -> !StringUtils.EMPTY.equals(audience) &&
                                e.getEndpointId().equals(audience)).findFirst();

                    if (endpointOpt.isPresent()) {
                        authenticate(samlResponse, decoded, audience, endpointOpt.get());
                    }
                } catch (SAMLException e) {
                    LOGGER.warn(e.getMessage(), e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String samlResponse, Response decoded, String endpointId,
                              ExternalServiceEndpoint endpoint) throws IOException, SAMLException {
        try (FileReader metadataReader = new FileReader(new File(endpoint.getMetadataPath()))) {
            CustomSamlClient client = CustomSamlClient.fromMetadata(
                endpointId, metadataReader, RESPONSE_SKEW);
            client.setMaxAuthenticationAge(maxAuthentificationAge);

            SamlResponse parsedResponse = client.validate(decoded);
            String userName = parsedResponse.getNameID().toUpperCase();
            final Map<String, List<String>> attributes = readAttributes(parsedResponse);
            final List<String> groups = readAuthorities(attributes, endpoint.getAuthorities());
            final Map<String, String> userAttributes = readAttributes(attributes, endpoint.getSamlAttributes());

            UserContext userContext = accessService.parseUser(userName, groups, userAttributes);
            LOGGER.debug("Found user by name {}", userName);

            userContext.setExternal(endpoint.isExternal());
            SecurityContextHolder.getContext()
                    .setAuthentication(new SAMLProxyAuthentication(samlResponse, userContext));
        }
    }

    private Map<String, String> readAttributes(final Map<String, List<String>> attributes,
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
                        LOGGER.error("Can not parse saml user attributes property.");
                        return null;
                    }
                    List<String> attributeValues = attributes.get(value);
                    return ListUtils.emptyIfNull(attributeValues)
                            .stream()
                            .findFirst()
                            .map(v -> new ImmutablePair<>(key, v))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ImmutablePair::getLeft, ImmutablePair::getRight, (v1, v2) -> v1));
    }

    private List<String> readAuthorities(final Map<String, List<String>> attributes,
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
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    private Map<String, List<String>> readAttributes(final SamlResponse parsedResponse) {
        return ListUtils.emptyIfNull(
                parsedResponse.getAssertion().getAttributeStatements())
                .stream()
                .map(this::readStatement)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        ListUtils::sum));
    }

    private Map<String, List<String>> readStatement(final AttributeStatement statement) {
        return statement.getAttributes()
                .stream()
                .collect(Collectors.toMap(
                    Attribute::getName,
                    a -> a.getAttributeValues()
                        .stream()
                        .map(value -> {
                            if (value instanceof XSString) {
                                return ((XSString) value).getValue();
                            } else if (value instanceof XSAny) {
                                return ((XSAny) value).getTextContent();
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList())));
    }

    private boolean urlMatches(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String[] parts = url.split("restapi");
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        return parts.length > 1 && antPathMatcher.match("/proxy/**", parts[1]);
    }
}
