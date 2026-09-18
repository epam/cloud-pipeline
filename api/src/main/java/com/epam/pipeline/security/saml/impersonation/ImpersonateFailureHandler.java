/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.security.saml.impersonation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
public class ImpersonateFailureHandler implements AuthenticationFailureHandler, ImpersonateRequestHandler {

    private final String impersonationStartUrl;
    private final String impersonationStopUrl;

    public ImpersonateFailureHandler(final String impersonationStartUrl, final String impersonationStopUrl) {
        this.impersonationStartUrl = impersonationStartUrl;
        this.impersonationStopUrl = impersonationStopUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) {
        log.info("Failed impersonation action: {}, message: {}",
                getImpersonationAction(impersonationStartUrl, impersonationStopUrl, request), exception.getMessage());
    }

}
