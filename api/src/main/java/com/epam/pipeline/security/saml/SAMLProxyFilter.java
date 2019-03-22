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
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.common.SAMLException;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Audience;
import org.opensaml.saml2.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_EXTERNAL_SERVICES_ENDPOINTS;

public class SAMLProxyFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAMLProxyFilter.class);
    private static final int MAX_AUTHENTICATION_AGE = 93600;
    private static final int RESPONSE_SKEW = 1200;

    @Autowired
    private UserManager userManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

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
            client.setMaxAuthenticationAge(MAX_AUTHENTICATION_AGE);

            SamlResponse parsedResponse = client.validate(decoded);
            String userName = parsedResponse.getNameID().toUpperCase();

            PipelineUser loadedUser = userManager.loadUserByName(userName);
            if (loadedUser == null) {
                throw new UsernameNotFoundException(messageHelper.getMessage(
                    MessageConstants.ERROR_USER_NAME_NOT_FOUND, userName));
            }

            LOGGER.debug("Found user by name {}", userName);

            UserContext userContext = new UserContext(loadedUser);
            userContext.setExternal(endpoint.isExternal());
            SecurityContextHolder.getContext().setAuthentication(new SAMLProxyAuthentication(
                samlResponse, parsedResponse, userContext));
        }
    }

    private boolean urlMatches(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String[] parts = url.split("restapi");
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        return parts.length > 1 && antPathMatcher.match("/proxy/**", parts[1]);
    }
}
