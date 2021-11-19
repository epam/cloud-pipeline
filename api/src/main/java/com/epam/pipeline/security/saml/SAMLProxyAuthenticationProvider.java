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

import static com.epam.pipeline.manager.preference.SystemPreferences.SYSTEM_EXTERNAL_SERVICES_ENDPOINTS;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.common.SAMLException;
import org.opensaml.saml2.core.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.security.ExternalServiceEndpoint;

@Slf4j
public class SAMLProxyAuthenticationProvider implements AuthenticationProvider {

    private static final int RESPONSE_SKEW = 1200;

    @Value("${saml.authn.max.authentication.age:93600}")
    private Long maxAuthentificationAge;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        SAMLProxyAuthentication auth = (SAMLProxyAuthentication) authentication;

        List<ExternalServiceEndpoint> externalServices = preferenceManager.getPreference(
            SYSTEM_EXTERNAL_SERVICES_ENDPOINTS);
        if (CollectionUtils.isEmpty(externalServices)) {
            throw new AuthenticationServiceException(
                    messageHelper.getMessage(MessageConstants.ERROR_PROXY_SECURITY_CONFIG_MISSING));
        }

        if (StringUtils.isNotBlank(auth.getRawSamlResponse())) {
            try {
                Response decoded = CustomSamlClient.decodeSamlResponse(auth.getRawSamlResponse());
                String endpointId = decoded.getDestination()    // cut out SSO endpoint
                    .substring(0, decoded.getDestination().length() - CustomSamlClient.SSO_ENDPOINT.length());
                Optional<ExternalServiceEndpoint> endpointOpt = externalServices.stream()
                    .filter(e -> e.getEndpointId().equals(endpointId)).findFirst();

                if (endpointOpt.isPresent()) {
                    Authentication validated = validateAuthentication(auth, decoded, endpointId, endpointOpt.get());
                    log.debug("Successfully authenticate user with name: " + auth.getName());
                    return validated;
                } else {
                    throw new AuthenticationServiceException("Authentication error: unexpected external service");
                }
            } catch (SAMLException e) {
                throw new AuthenticationServiceException("Authentication error: ", e);
            }
        } else {
            throw new AuthenticationServiceException("Authentication error: missing SAML token");
        }
    }

    private Authentication validateAuthentication(SAMLProxyAuthentication auth, Response decoded, String endpointId,
                                                  ExternalServiceEndpoint endpoint) throws SAMLException {
        try (FileReader metadataReader = new FileReader(new File(endpoint.getMetadataPath()))) {
            CustomSamlClient client = CustomSamlClient.fromMetadata(endpointId, metadataReader,
                                                                    RESPONSE_SKEW);

            client.setMaxAuthenticationAge(maxAuthentificationAge);
            client.validate(decoded);
            return auth;

        } catch (IOException e) {
            throw new AuthenticationServiceException("Could not read proxy metadata", e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (SAMLProxyAuthentication.class.isAssignableFrom(authentication));
    }
}
