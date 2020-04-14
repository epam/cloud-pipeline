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

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.epam.pipeline.security.jwt.RestAuthenticationEntryPoint;
import com.epam.pipeline.security.saml.SAMLProxyAuthenticationProvider;
import com.epam.pipeline.security.saml.SAMLProxyFilter;
import com.epam.pipeline.entity.user.DefaultRoles;

@Configuration
@Order(1)
public class ProxySecurityConfig extends WebSecurityConfigurerAdapter {

    private static final String RESTAPI_PROXY = "/restapi/proxy/**";

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(proxyAuthenticationProvider());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .exceptionHandling().authenticationEntryPoint(new RestAuthenticationEntryPoint())
            .and()
            .requestMatcher(new AntPathRequestMatcher(RESTAPI_PROXY))
            .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS).permitAll()
                .antMatchers(RESTAPI_PROXY)
                    .hasAnyAuthority(DefaultRoles.ROLE_ADMIN.getName(), DefaultRoles.ROLE_USER.getName())
                .anyRequest().permitAll()
            .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .and()
                .addFilterBefore(getSAMLProxyFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    public FilterRegistrationBean registration(SAMLProxyFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    protected SAMLProxyAuthenticationProvider proxyAuthenticationProvider() {
        return new SAMLProxyAuthenticationProvider();
    }

    @Bean
    protected SAMLProxyFilter getSAMLProxyFilter() {
        return new SAMLProxyFilter();
    }
}
