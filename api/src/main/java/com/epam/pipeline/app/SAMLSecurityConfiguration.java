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

import com.epam.pipeline.security.saml.CustomSamlRelyingPartyRegistrationBuilder;
import com.epam.pipeline.security.saml.CustomSamlResponseAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.session.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
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
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableWebSecurity
public class SAMLSecurityConfiguration {

    private final String loginFailureRedirect;
    private final String acsEndpoint;
    private final CustomSamlResponseAuthenticationConverter responseAuthenticationConverter;
    private final CustomSamlRelyingPartyRegistrationBuilder relyingPartyRegistrationBuilder;

    public SAMLSecurityConfiguration(
            @Value("${saml.login.failure.redirect:/error}") final  String loginFailureRedirect,
            @Value("${saml.sso.acs.endpoint:/saml/SSO}") final String acsEndpoint,
            final CustomSamlResponseAuthenticationConverter responseAuthenticationConverter,
            final CustomSamlRelyingPartyRegistrationBuilder relyingPartyRegistrationBuilder) {
        this.loginFailureRedirect = loginFailureRedirect;
        this.acsEndpoint = acsEndpoint;
        this.responseAuthenticationConverter = responseAuthenticationConverter;
        this.relyingPartyRegistrationBuilder = relyingPartyRegistrationBuilder;
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
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .saml2Login(saml2 -> saml2
                        .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository())
                        .successHandler(successRedirectHandler())
                        .failureHandler(authenticationFailureHandler())
                        .authenticationManager(authenticationProvider())
                        .loginProcessingUrl(acsEndpoint)
                )
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
    public ProviderManager authenticationProvider() {
        final var authenticationProvider = new OpenSaml4AuthenticationProvider();
        authenticationProvider.setResponseAuthenticationConverter(responseAuthenticationConverter::covert);
        return new ProviderManager(authenticationProvider);
    }
}
