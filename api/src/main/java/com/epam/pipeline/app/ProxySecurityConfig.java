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
import com.epam.pipeline.security.saml.SAMLProxyAuthenticationProvider;
import com.epam.pipeline.security.saml.SAMLProxyFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// @Configuration - Disabled SAML proxy configuration
// @Order(1)
public class ProxySecurityConfig implements WebMvcConfigurer {

    private static final String RESTAPI_PROXY = "/restapi/proxy/**";

    /*@Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(proxyAuthenticationProvider());
    }*/

    /*@Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .exceptionHandling().authenticationEntryPoint(new RestAuthenticationEntryPoint())
            .and()
            .requestMatcher(new AntPathRequestMatcher(RESTAPI_PROXY))
            .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS).permitAll()
                .antMatchers(RESTAPI_PROXY).authenticated()
                .anyRequest().permitAll()
            .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .and()
                .addFilterBefore(getSAMLProxyFilter(), UsernamePasswordAuthenticationFilter.class);
    }*/

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
