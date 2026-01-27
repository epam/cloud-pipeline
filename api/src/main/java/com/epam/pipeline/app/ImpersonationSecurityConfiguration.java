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

package com.epam.pipeline.app;

import com.epam.pipeline.security.saml.impersonation.ImpersonateFailureHandler;
import com.epam.pipeline.security.saml.impersonation.ImpersonateSuccessHandler;
import com.epam.pipeline.security.saml.impersonation.ImpersonationManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
@Order(1)
public class ImpersonationSecurityConfiguration {
    private final String impersonationOperationsRootUrl;
    private final ImpersonationManager impersonationManager;

    public ImpersonationSecurityConfiguration(
            @Value("${api.security.impersonation.operations.root.url:/restapi/user/impersonation}")
            final String impersonationOperationsRootUrl,
            final ImpersonationManager impersonationManager) {
        this.impersonationOperationsRootUrl = impersonationOperationsRootUrl;
        this.impersonationManager = impersonationManager;
    }

    @Bean
    public SecurityFilterChain impersonationFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher(getImpersonationStartUrl()),
                        new AntPathRequestMatcher(getImpersonationStopUrl())))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.NEVER)
                )
                .addFilterAfter(switchUserFilter(), AuthorizationFilter.class)
                .build();
    }

    private SwitchUserFilter switchUserFilter() {
        final var filter = new SwitchUserFilter();
        final String impersonationStartUrl = getImpersonationStartUrl();
        final String impersonationStopUrl = getImpersonationStopUrl();
        filter.setSwitchUserUrl(impersonationStartUrl);
        filter.setExitUserUrl(impersonationStopUrl);
        filter.setUserDetailsService(impersonationManager);
        filter.setUserDetailsChecker(impersonationManager);
        filter.setSwitchUserMatcher(new AntPathRequestMatcher(impersonationStartUrl, HttpMethod.GET.name()));
        filter.setExitUserMatcher(new AntPathRequestMatcher(impersonationStopUrl, HttpMethod.GET.name()));
        filter.setFailureHandler(new ImpersonateFailureHandler(impersonationStartUrl, impersonationStopUrl));
        filter.setSuccessHandler(new ImpersonateSuccessHandler(impersonationStartUrl, impersonationStopUrl));
        return filter;
    }

    private String getImpersonationStartUrl() {
        return impersonationOperationsRootUrl + "/start";
    }

    private String getImpersonationStopUrl() {
        return impersonationOperationsRootUrl + "/stop";
    }
}
