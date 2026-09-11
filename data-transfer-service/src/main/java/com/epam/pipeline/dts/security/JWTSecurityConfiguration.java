/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.security;

import com.epam.pipeline.dts.security.service.JwtAuthenticationProvider;
import com.epam.pipeline.dts.security.service.JwtFilterAuthenticationFilter;
import com.epam.pipeline.dts.security.service.JwtTokenVerifier;
import com.epam.pipeline.dts.security.service.RestAuthenticationEntryPoint;
import com.epam.pipeline.dts.security.service.SecurityService;
import com.epam.pipeline.dts.security.service.impl.SecurityServiceImpl;
import com.epam.pipeline.dts.transfer.model.UsernameTransformation;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
public class JWTSecurityConfiguration {

    @Value("${jwt.public.key}")
    private String publicKey;

    @Bean
    public SecurityFilterChain jwtSecurityFilterChain(final HttpSecurity httpSecurity) throws Exception {
        httpSecurity
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(
                    exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                            new RestAuthenticationEntryPoint(),
                            new AntPathRequestMatcher(getSecuredResources())
                    ))
            .securityMatcher(getFullRequestMatcher())
            .authorizeHttpRequests(auth -> {
                //instead of adding "/error" to getUnsecuredResources method
                auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                auth.requestMatchers(HttpMethod.OPTIONS).permitAll();
                auth.requestMatchers(getUnsecuredResources()).permitAll();
                auth.requestMatchers(getSecuredResources()).authenticated();
            })
            .sessionManagement(session ->
                    session.sessionCreationPolicy(STATELESS))
            .addFilterBefore(getJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    @Bean
    public JwtTokenVerifier jwtTokenVerifier() {
        return new JwtTokenVerifier(publicKey);
    }

    @Bean
    protected JwtAuthenticationProvider jwtAuthenticationProvider() {
        return new JwtAuthenticationProvider(jwtTokenVerifier());
    }

    protected JwtFilterAuthenticationFilter getJwtAuthenticationFilter() {
        return new JwtFilterAuthenticationFilter(jwtTokenVerifier());
    }

    protected RequestMatcher getFullRequestMatcher() {
        return new AntPathRequestMatcher(getSecuredResources());
    }

    protected String getSecuredResources() {
        return "/**";
    }

    protected String[] getUnsecuredResources() {
        return new String[] {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
        };
    }

    @Bean
    public SecurityService securityService(@Value("${dts.impersonation.name.transformation}")
                                           final UsernameTransformation usernameTransformation) {
        return new SecurityServiceImpl(usernameTransformation);
    }
}
