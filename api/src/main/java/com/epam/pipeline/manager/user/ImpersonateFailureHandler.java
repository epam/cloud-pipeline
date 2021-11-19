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

package com.epam.pipeline.manager.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ImpersonateFailureHandler implements AuthenticationFailureHandler, ImpersonateRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImpersonateFailureHandler.class);
    private final String impersonationStartUrl;
    private final String impersonationStopUrl;


    public ImpersonateFailureHandler(final String impersonationStartUrl, final String impersonationStopUrl) {
        this.impersonationStartUrl = impersonationStartUrl;
        this.impersonationStopUrl = impersonationStopUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) {
        LOGGER.info("Failed impersonation action: " +
                getImpersonationAction(impersonationStartUrl, impersonationStopUrl, request) +
                ", message: " + exception.getMessage());
    }

}
