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

package com.epam.pipeline.app;

import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.security.jwt.JwtAuthenticationProvider;
import com.epam.pipeline.security.jwt.JwtFilterAuthenticationFilter;
import com.epam.pipeline.security.jwt.JwtTokenVerifier;
import com.epam.pipeline.security.jwt.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.saml.SAMLAuthenticationProvider;
import org.springframework.security.saml.SAMLEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;


@Configuration
@ComponentScan(basePackages = {"com.epam.pipeline.security.jwt"})
@Order(2)
public class JWTSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final String REST_API_PREFIX = "/restapi/**";
    private static final String ROUTE_URL = "/restapi/route";
    private static final String PROLONG_URL = "/restapi/run/**/prolong**";

    @Value("${jwt.key.public}")
    private String publicKey;

    @Value("${jwt.use.for.all.requests:false}")
    private boolean useJwtAuthForAllRequests;
    
    @Value("${api.security.anonymous.urls:/restapi/route}")
    private String[] anonymousResources;

    @Autowired
    private SAMLAuthenticationProvider samlAuthenticationProvider;

    @Autowired
    private SAMLEntryPoint samlEntryPoint;

    protected String getPublicKey() {
        return publicKey;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(samlAuthenticationProvider);
        auth.authenticationProvider(jwtAuthenticationProvider());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .exceptionHandling()
                    .defaultAuthenticationEntryPointFor(
                            samlEntryPoint, getRedirectRequestMatcher())
                    .defaultAuthenticationEntryPointFor(
                            new RestAuthenticationEntryPoint(), new AntPathRequestMatcher(getSecuredResources()))
                .and()
                .requestMatcher(getFullRequestMatcher())
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS).permitAll()
                .antMatchers(getUnsecuredResources()).permitAll()
                .antMatchers(getAnonymousResources())
                    .hasAnyAuthority(roles(DefaultRoles.ROLE_ADMIN, DefaultRoles.ROLE_USER, 
                            DefaultRoles.ROLE_ANONYMOUS_USER))
                .antMatchers(getSecuredResources())
                    .hasAnyAuthority(roles(DefaultRoles.ROLE_ADMIN, DefaultRoles.ROLE_USER))
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .requestCache().requestCache(requestCache())
                .and()
                .addFilterBefore(getJwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);
    }

    @Bean public JwtTokenVerifier jwtTokenVerifier() {
        return new JwtTokenVerifier(getPublicKey());
    }

    @Bean protected JwtAuthenticationProvider jwtAuthenticationProvider() {
        return new JwtAuthenticationProvider(jwtTokenVerifier());
    }

    protected JwtFilterAuthenticationFilter getJwtAuthenticationFilter() {
        return new JwtFilterAuthenticationFilter(jwtTokenVerifier());
    }

    protected RequestMatcher getFullRequestMatcher() {
        return new AntPathRequestMatcher(getSecuredResources());
    }

    protected String getSecuredResources() {
        return useJwtAuthForAllRequests ? "/**" : REST_API_PREFIX;
    }

    protected String[] getUnsecuredResources() {
        return new String[] {
            "/restapi/dockerRegistry/oauth",
            "/restapi/swagger-resources/**",
            "/restapi/swagger-ui.html",
            "/restapi/webjars/springfox-swagger-ui/**",
            "/restapi/v2/api-docs/**",
            "/restapi/proxy/**",
            "/launch.sh", "/PipelineCLI.tar.gz",
            "/pipe-common.tar.gz", "/commit-run-scripts/**", "/pipe",
            "/fsbrowser.tar.gz", "/error", "/error/**", "/pipe.zip", "/pipe.tar.gz",
            "/pipe-el6", "/pipe-el6.tar.gz"
        };
    }

    public String[] getAnonymousResources() {
        return anonymousResources;
    }

    private String[] roles(final DefaultRoles... roles) {
        return Arrays.stream(roles)
                .map(DefaultRoles::getRole)
                .map(Role::getName)
                .toArray(String[]::new);
    }

    //List of urls under REST that should be redirected back after authorization
    private String[] redirectedUrls() {
        return new String[] { ROUTE_URL, PROLONG_URL };
    }

    private RequestCache requestCache() {
        final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(getRedirectRequestMatcher());
        return requestCache;
    }

    //Only one of redirectedUrls
    private RequestMatcher getRedirectRequestMatcher() {
        return new OrRequestMatcher(Arrays.stream(redirectedUrls())
                .map(AntPathRequestMatcher::new)
                .collect(Collectors.toCollection(ArrayList::new)));
    }

}
