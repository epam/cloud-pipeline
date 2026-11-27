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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.ExternalServiceEndpoint;
import com.epam.pipeline.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

/**
 * Resolves the user of a SAML response an external service passed to the proxy endpoints.
 * <p>
 * Only a registered user is let in: the proxy flow neither creates users nor updates their groups
 * and attributes, which is the login only the platform's own SSO does.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SAMLProxyAuthenticationProvider {

    private final UserManager userManager;
    private final MessageHelper messageHelper;

    public SAMLProxyAuthentication authenticate(final Authentication authentication,
                                                final String samlResponse,
                                                final ExternalServiceEndpoint endpoint) {
        final var principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
        final String userName = principal.getName().toUpperCase();
        final PipelineUser loadedUser = userManager.loadUserByName(userName);
        if (loadedUser == null) {
            throw new UsernameNotFoundException(messageHelper.getMessage(
                    MessageConstants.ERROR_USER_NAME_NOT_FOUND, userName));
        }
        log.debug("Found user by name {}", userName);

        final UserContext userContext = new UserContext(loadedUser);
        userContext.setExternal(endpoint.isExternal());
        return new SAMLProxyAuthentication(samlResponse, userContext);
    }
}
