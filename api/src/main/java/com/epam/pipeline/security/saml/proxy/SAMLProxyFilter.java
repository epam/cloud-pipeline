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

package com.epam.pipeline.security.saml.proxy;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.impl.ResponseUnmarshaller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2PostAuthenticationRequest;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_EXTERNAL_SERVICES_ENDPOINTS;
import static org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters.CLOCK_SKEW;
import static org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters.SC_VALID_IN_RESPONSE_TO;
import static org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters.STMT_AUTHN_MAX_TIME;

@Slf4j
@Component
public class SAMLProxyFilter extends OncePerRequestFilter {
    private static final int RESPONSE_SKEW = 1200;
    private static final String ACS_ENDPOINT = "saml/SSO";

    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final SAMLProxyAuthenticationProvider authenticationProvider;
    private final Long maxAuthentificationAge;

    public SAMLProxyFilter(final MessageHelper messageHelper,
                           final PreferenceManager preferenceManager,
                           final SAMLProxyAuthenticationProvider authenticationProvider,
                           @Value("${saml.authn.max.authentication.age:93600}")
                           final Long maxAuthentificationAge) {
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.authenticationProvider = authenticationProvider;
        this.maxAuthentificationAge = maxAuthentificationAge;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        if (!urlMatches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        final List<ExternalServiceEndpoint> externalServices = preferenceManager.getPreference(
                SYSTEM_EXTERNAL_SERVICES_ENDPOINTS);
        if (CollectionUtils.isEmpty(externalServices)) {
            log.warn(messageHelper.getMessage(MessageConstants.ERROR_PROXY_SECURITY_CONFIG_MISSING));
            filterChain.doFilter(request, response);
            return;
        }

        final String rawSamlResponse = request.getParameter("SAMLResponse");
        if (StringUtils.isBlank(rawSamlResponse)) {
            log.debug("Endpoint matches '/proxy/**' but no SAMLResponse provided.");
            filterChain.doFilter(request, response);
            return;
        }

        final var samlResponse = decodeSamlResponse(rawSamlResponse);
        final String audience = extractAudienceURI(samlResponse);
        log.debug("Received SAMLResponse for audience: {}", audience);

        if (StringUtils.isBlank(audience)) {
            filterChain.doFilter(request, response);
            return;
        }

        externalServices.stream()
                .filter(e -> !StringUtils.EMPTY.equals(audience) &&
                        e.getEndpointId().equals(audience))
                .findFirst()
                .ifPresentOrElse(endpoint -> authenticate(samlResponse, endpoint),
                        () -> log.debug("No service endpoint found for audience: {}", audience));

        filterChain.doFilter(request, response);
    }

    private void authenticate(final String samlResponse, final ExternalServiceEndpoint endpoint) {
        final Authentication authentication = attemptAuthentication(samlResponse, endpoint);
        SecurityContextHolder.getContext()
                .setAuthentication(authenticationProvider.authenticate(authentication, samlResponse, endpoint));
    }

    private Authentication attemptAuthentication(final String samlResponse, final ExternalServiceEndpoint endpoint) {
        final String inResponseTo = extractInResponseTo(samlResponse);
        final String registrationId = "proxy-saml-" + RandomStringUtils.secure().next(10);
        final var registration = RelyingPartyRegistrations
                .fromMetadataLocation("file:" + endpoint.getMetadataPath())
                .registrationId(registrationId)
                .entityId(endpoint.getEndpointId())
                .assertionConsumerServiceLocation(buildAcsUrl(endpoint.getEndpointId()))
                .build();
        final var artificialAuthnRequest = buildAuthenticationRequest(registration, inResponseTo, registrationId);
        final var token = new Saml2AuthenticationToken(registration, samlResponse, artificialAuthnRequest);
        return buildAuthenticationProvider(inResponseTo)
                .authenticate(token);
    }

    private OpenSaml4AuthenticationProvider buildAuthenticationProvider(final String inResponseTo) {
        final var authenticationProvider = new OpenSaml4AuthenticationProvider();
        authenticationProvider.setAssertionValidator(assertionToken -> OpenSaml4AuthenticationProvider
                .createDefaultAssertionValidatorWithParameters(params -> {
                    params.put(SC_VALID_IN_RESPONSE_TO, inResponseTo);
                    params.put(CLOCK_SKEW, Duration.ofSeconds(RESPONSE_SKEW));
                    params.put(STMT_AUTHN_MAX_TIME, Duration.ofSeconds(maxAuthentificationAge));
                })
                .convert(assertionToken));
        return authenticationProvider;
    }

    private String buildAcsUrl(final String endpointId) {
        return ProviderUtils.withoutTrailingDelimiter(endpointId) + ProviderUtils.withLeadingDelimiter(ACS_ENDPOINT);
    }

    @Nullable
    private Response parseSamlResponse(final String samlResponse) {
        final XMLObjectProviderRegistry registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
        final var responseUnmarshaller = (ResponseUnmarshaller) registry.getUnmarshallerFactory()
                .getUnmarshaller(Response.DEFAULT_ELEMENT_NAME);

        if (Objects.isNull(responseUnmarshaller)) {
            return null;
        }

        final var parserPool = registry.getParserPool();
        try {
            final Document document = parserPool
                    .parse(new ByteArrayInputStream(samlResponse.getBytes(StandardCharsets.UTF_8)));
            final Element element = document.getDocumentElement();
            return (Response) responseUnmarshaller.unmarshall(element);
        } catch (XMLParserException | UnmarshallingException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private String extractInResponseTo(final String samlResponse) {
        final var response = parseSamlResponse(samlResponse);
        if (Objects.isNull(response)) {
            return null;
        }
        return ListUtils.emptyIfNull(response.getAssertions()).stream()
                .findFirst()
                .map(Assertion::getSubject)
                .map(subject -> ListUtils.emptyIfNull(subject.getSubjectConfirmations()).getFirst())
                .map(subjectConfirmation
                        -> subjectConfirmation.getSubjectConfirmationData().getInResponseTo())
                .orElse(StringUtils.EMPTY);
    }

    private Saml2PostAuthenticationRequest buildAuthenticationRequest(final RelyingPartyRegistration registration,
                                                                      final String inResponseTo,
                                                                      final String registrationId) {
        // An artificial authentication request object is used here to avoid validation failures.
        // Since there’s no real request, there’s no InResponseTo value stored.
        // However, Spring Security still checks for InResponseTo if it’s present in the SAML response,
        // so this object is required to satisfy those checks.
        return Saml2PostAuthenticationRequest.withRelyingPartyRegistration(registration)
                .id(inResponseTo)
                .samlRequest(registrationId) // dummy string to avoid hasText check failure
                .build();
    }

    @Nullable
    private String extractAudienceURI(final String samlResponse) {
        final var response = parseSamlResponse(samlResponse);
        if (Objects.isNull(response)) {
            return null;
        }
        return ListUtils.emptyIfNull(response.getAssertions()).stream()
                .findFirst()
                .map(Assertion::getConditions)
                .map(conditions -> ListUtils.emptyIfNull(conditions.getAudienceRestrictions()).stream()
                        .findFirst())
                .flatMap(Function.identity())
                .map(audienceRestriction
                        -> ListUtils.emptyIfNull(audienceRestriction.getAudiences()).stream()
                        .findFirst())
                .flatMap(Function.identity())
                .map(Audience::getURI)
                .orElse(StringUtils.EMPTY);
    }

    private String decodeSamlResponse(final String rawSamlResponse) {
        final var decodedResponse = new String(Base64.decode(rawSamlResponse), StandardCharsets.UTF_8);
        log.trace("Validating SAML response: {}", decodedResponse);
        return decodedResponse;
    }

    private boolean urlMatches(final HttpServletRequest request) {
        final String url = request.getRequestURL().toString();
        final String[] parts = url.split("restapi");
        final var antPathMatcher = new AntPathMatcher();
        return parts.length > 1 && antPathMatcher.match("/proxy/**", parts[1]);
    }
}
