/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.security.saml.CustomSamlRelyingPartyRegistrationBuilder;
import com.epam.pipeline.security.saml.CustomSamlResponseAuthenticationConverter;
import com.epam.pipeline.security.saml.impersonation.ImpersonateFailureHandler;
import com.epam.pipeline.security.saml.impersonation.ImpersonateSuccessHandler;
import com.epam.pipeline.security.saml.impersonation.ImpersonationManager;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

@Order(2)
@Configuration
@EnableWebSecurity
public class SAMLSecurityConfiguration {

    private final String loginFailureRedirect;
    private final String acsEndpoint;
    private final boolean logoutInvalidateSession;
    private final String logoutEndpoint;
    private final CustomSamlResponseAuthenticationConverter responseAuthenticationConverter;
    private final CustomSamlRelyingPartyRegistrationBuilder relyingPartyRegistrationBuilder;
    private final String[] anonymousResources;
    private final List<String> excludeScripts;
    private final String[] swaggerAccessRoles;
    final String impersonationOperationsRootUrl;

    public SAMLSecurityConfiguration(
            @Value("${saml.login.failure.redirect:/error}") final String loginFailureRedirect,
            @Value("${saml.sso.acs.endpoint:/saml/SSO}") final String acsEndpoint,
            @Value("${saml.logout.invalidate.session:false}") final boolean logoutInvalidateSession,
            @Value("${saml.logout.endpoint:/saml/logout}") final String logoutEndpoint,
            final CustomSamlResponseAuthenticationConverter responseAuthenticationConverter,
            final CustomSamlRelyingPartyRegistrationBuilder relyingPartyRegistrationBuilder,
            @Value("${api.security.anonymous.urls:/restapi/route}") final String[] anonymousResources,
            @Value("#{'${api.security.public.urls}'.split(',')}") final List<String> excludeScripts,
            @Value("${api.security.swagger.access.roles:ROLE_ADMIN,ROLE_USER}") final String[] swaggerAccessRoles,
            @Value("${api.security.impersonation.operations.root.url:/restapi/user/impersonation}")
            final String impersonationOperationsRootUrl) {
        this.loginFailureRedirect = loginFailureRedirect;
        this.acsEndpoint = acsEndpoint;
        this.logoutInvalidateSession = logoutInvalidateSession;
        this.logoutEndpoint = logoutEndpoint;
        this.responseAuthenticationConverter = responseAuthenticationConverter;
        this.relyingPartyRegistrationBuilder = relyingPartyRegistrationBuilder;
        this.anonymousResources = anonymousResources;
        this.excludeScripts = excludeScripts;
        this.swaggerAccessRoles = swaggerAccessRoles;
        this.impersonationOperationsRootUrl = impersonationOperationsRootUrl;
    }

    @Bean
    public DefaultCookieSerializerCustomizer cookieSerializerCustomizer() {
        return cookieSerializer -> {
            // SAML breaks when using spring session as it sets LAX on cookie by default
            cookieSerializer.setSameSite(null);
        };
    }

    @Bean
    public SecurityFilterChain samlFilterChain(final HttpSecurity http) throws Exception {
        final var relyingPartyRegistrationRepository = relyingPartyRegistrationRepository();
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .securityMatcher(getFullRequestMatcher())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(toAntMatchers(getUnsecuredResources()))
                            .permitAll();
                    auth.requestMatchers(toAntMatchers(anonymousResources))
                            .hasAnyAuthority(
                                    DefaultRoles.ROLE_ADMIN.getName(),
                                    DefaultRoles.ROLE_USER.getName(),
                                    DefaultRoles.ROLE_ANONYMOUS_USER.getName());
                    auth.requestMatchers(toAntMatchers(getSwaggerResources()))
                            .hasAnyAuthority(swaggerAccessRoles);
                    auth.requestMatchers(toAntMatchers(getSecuredResourcesRoot()))
                            .hasAnyAuthority(
                                    DefaultRoles.ROLE_ADMIN.getName(),
                                    DefaultRoles.ROLE_USER.getName());
                })
                .saml2Login(saml2 -> saml2
                        .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
                        .successHandler(successRedirectHandler())
                        .failureHandler(authenticationFailureHandler())
                        .authenticationManager(samlAuthenticationProviderManager(samlAuthenticationProvider()))
                        .loginProcessingUrl(acsEndpoint)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutSuccessHandler())
                        .logoutUrl(logoutEndpoint)
                        .invalidateHttpSession(logoutInvalidateSession)
                        .clearAuthentication(true)
                )
                .saml2Logout(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .cors(cors -> cors
                        .configurationSource(request -> {
                            var config = new CorsConfiguration();
                            config.addAllowedOrigin("*"); // Replace with specific origins in production!
                            config.addAllowedMethod("*");
                            config.addAllowedHeader("*");
                            return config;
                        })
                )
                .build();
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistrationBuilder.build());
    }

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
        final var handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }

    @Bean
    public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
        final var handler = new SimpleUrlAuthenticationFailureHandler();
        handler.setDefaultFailureUrl(loginFailureRedirect);
        return handler;
    }

    @Bean
    public OpenSaml4AuthenticationProvider samlAuthenticationProvider() {
        final var authenticationProvider = new OpenSaml4AuthenticationProvider();
        authenticationProvider.setResponseAuthenticationConverter(responseAuthenticationConverter::covert);
        return authenticationProvider;
    }

    @Bean
    public ProviderManager samlAuthenticationProviderManager(
            final OpenSaml4AuthenticationProvider samlAuthenticationProvider) {
        return new ProviderManager(samlAuthenticationProvider);
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        final var handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }

    @Bean
    public SwitchUserFilter switchUserFilter(final ImpersonationManager impersonationManager) {
        final var filter = new SwitchUserFilter();
        final String impersonationStartUrl = getImpersonationStartUrl();
        final String impersonationStopUrl = getImpersonationStopUrl();
        filter.setUserDetailsService(impersonationManager);
        filter.setUserDetailsChecker(impersonationManager);
        filter.setSwitchUserMatcher(new AntPathRequestMatcher(impersonationStartUrl, "GET"));
        filter.setExitUserMatcher(new AntPathRequestMatcher(impersonationStopUrl, "GET"));
        filter.setFailureHandler(new ImpersonateFailureHandler(impersonationStartUrl, impersonationStopUrl));
        filter.setSuccessHandler(new ImpersonateSuccessHandler(impersonationStartUrl, impersonationStopUrl));
        return filter;
    }

    private RequestMatcher getFullRequestMatcher() {
        return new AntPathRequestMatcher(getSecuredResourcesRoot());
    }

    private AntPathRequestMatcher[] toAntMatchers(final String... patterns) {
        return Arrays.stream(patterns)
                .map(AntPathRequestMatcher::new)
                .toArray(AntPathRequestMatcher[]::new);
    }

    private String getSecuredResourcesRoot() {
        return "/**";
    }

    private String[] getUnsecuredResources() {
        final List<String> excludePaths = Arrays.asList(
                "/restapi/dockerRegistry/oauth",
                "/restapi/proxy/**",
                "/error",
                "/error/**",
                "/restapi/access/token",
                "/restapi/access/code");
        return ListUtils.union(excludePaths, ListUtils.emptyIfNull(excludeScripts)).toArray(new String[0]);
    }

    private String[] getSwaggerResources() {
        final List<String> paths = Arrays.asList(
                "/restapi/swagger-resources/**",
                "/restapi/swagger-ui.html",
                "/restapi/webjars/springfox-swagger-ui/**",
                "/restapi/v2/api-docs/**",
                "/restapi/v3/api-docs/**"
        );
        return paths.toArray(new String[0]);
    }

    private String getImpersonationStartUrl() {
        return impersonationOperationsRootUrl + "/start";
    }

    private String getImpersonationStopUrl() {
        return impersonationOperationsRootUrl + "/stop";
    }
}
