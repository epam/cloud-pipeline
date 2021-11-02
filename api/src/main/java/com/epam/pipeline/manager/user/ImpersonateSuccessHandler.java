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
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ImpersonateSuccessHandler implements AuthenticationSuccessHandler, ImpersonateRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImpersonateSuccessHandler.class);

    private final String impersonationStartUrl;
    private final String impersonationStopUrl;

    public ImpersonateSuccessHandler(final String impersonationStartUrl, final String impersonationStopUrl) {
        this.impersonationStartUrl = impersonationStartUrl;
        this.impersonationStopUrl = impersonationStopUrl;
    }

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response,
                                        final Authentication authentication) throws IOException {
        LOGGER.info("Successful impersonation action: " +
                getImpersonationAction(impersonationStartUrl, impersonationStopUrl, request) +
                ", user: " + authentication.getName());
        response.sendRedirect("/");
    }

}
