/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.epam.pipeline.manager.security.JwtTokenRevocationManager;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.jwt.JwtAuthenticationProvider;
import com.epam.pipeline.security.jwt.JwtFilterAuthenticationFilter;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import com.epam.pipeline.security.jwt.RestAuthenticationEntryPoint;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ComponentScan(basePackages = {"com.epam.pipeline.security.jwt"})
public class JWTSecurityConfiguration {

    private static final String REST_API_PREFIX = "/restapi/**";

    @Value("${jwt.key.public}")
    private String publicKey;

    @Value("${jwt.use.for.all.requests:false}")
    private boolean useJwtAuthForAllRequests;

    @Value("${jwt.disable.session:true}")
    private boolean disableJwtSession;

    @Value("${api.security.redirected.urls:/restapi/route,/restapi/**/prolong**,/restapi/static-resources/**}")
    private String[] redirectedResources;
    
    @Getter
    @Value("${api.security.anonymous.urls:/restapi/route}")
    private String[] anonymousResources;

    @Value("#{'${api.security.public.urls}'.split(',')}")
    private List<String> excludeScripts;

    @Value("${api.security.swagger.access.roles:ROLE_ADMIN,ROLE_USER}")
    private String[] swaggerAccessRoles;

    @Value("${api.security.disable.logging:false}")
    private boolean disableLogging;

    @Autowired
    private UserAccessService userAccessService;

    @Autowired
    private JwtTokenRevocationManager jwtTokenRevocationManager;

    protected String getPublicKey() {
        return publicKey;
    }

    @Order(3)
    @Bean
    public SecurityFilterChain jwtSecurityFilterChain(final HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new RestAuthenticationEntryPoint(),
                    new AntPathRequestMatcher(getSecuredResources())))
            .securityMatcher(getFullRequestMatcher())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.name())).permitAll();
                auth.requestMatchers(toAntMatchers(getUnsecuredResources())).permitAll();
                auth.requestMatchers(toAntMatchers(getAnonymousResources()))
                    .hasAnyAuthority(
                        DefaultRoles.ROLE_ADMIN.getName(),
                        DefaultRoles.ROLE_USER.getName(),
                        DefaultRoles.ROLE_ANONYMOUS_USER.getName());
                auth.requestMatchers(toAntMatchers(getSwaggerResources())).hasAnyAuthority(swaggerAccessRoles);
                auth.requestMatchers(new AntPathRequestMatcher(getSecuredResources()))
                    .hasAnyAuthority(DefaultRoles.ROLE_ADMIN.getName(), DefaultRoles.ROLE_USER.getName());
            })
            .sessionManagement(session -> session.sessionCreationPolicy(
                disableJwtSession ? SessionCreationPolicy.NEVER : SessionCreationPolicy.IF_REQUIRED))
            .requestCache(cache -> cache.requestCache(requestCache()))
            .addFilterBefore(getJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(Collections.singletonList(jwtAuthenticationProvider()));
    }

    @Bean public JwtTokenVerifier jwtTokenVerifier() {
        return new JwtTokenVerifier(getPublicKey());
    }

    @Bean protected JwtAuthenticationProvider jwtAuthenticationProvider() {
        return new JwtAuthenticationProvider(jwtTokenVerifier(), jwtTokenRevocationManager, userAccessService);
    }

    protected JwtFilterAuthenticationFilter getJwtAuthenticationFilter() {
        return new JwtFilterAuthenticationFilter(jwtTokenVerifier(), jwtTokenRevocationManager, userAccessService,
                disableLogging);
    }

    protected RequestMatcher getFullRequestMatcher() {
        return new AntPathRequestMatcher(getSecuredResources());
    }

    protected String getSecuredResources() {
        return useJwtAuthForAllRequests ? "/**" : REST_API_PREFIX;
    }

    protected String[] getUnsecuredResources() {
        final List<String> excludePaths = Arrays.asList(
                "/restapi/dockerRegistry/oauth",
                "/restapi/proxy/**",
                "/error",
                "/error/**",
                "/restapi/access/token",
                "/restapi/access/code");
        return ListUtils.union(excludePaths, ListUtils.emptyIfNull(excludeScripts)).toArray(new String[0]);
    }

    protected String[] getSwaggerResources() {
        final List<String> paths = Arrays.asList(
                "/restapi/swagger-resources/**",
                "/restapi/swagger-ui.html",
                "/restapi/webjars/springfox-swagger-ui/**",
                "/restapi/v2/api-docs/**",
                "/restapi/v3/api-docs/**"
                );
        return paths.toArray(new String[0]);
    }

    private RequestCache requestCache() {
        final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(getRedirectRequestMatcher());
        return requestCache;
    }

    //Only one of redirectedUrls
    private RequestMatcher getRedirectRequestMatcher() {
        return new OrRequestMatcher(Arrays.stream(redirectedResources)
                .map(AntPathRequestMatcher::new)
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    private AntPathRequestMatcher[] toAntMatchers(String[] patterns) {
        return Arrays.stream(patterns)
                .map(AntPathRequestMatcher::new)
                .toArray(AntPathRequestMatcher[]::new);
    }
}
