/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.external.datastorage.app;

import com.epam.pipeline.external.datastorage.security.SAMLUserDetailsService;
import com.epam.pipeline.external.datastorage.security.SamlRelyingPartyRegistrationBuilder;
import com.epam.pipeline.external.datastorage.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.DefaultRelyingPartyRegistrationResolver;
import org.springframework.security.saml2.provider.service.web.RelyingPartyRegistrationResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import org.springframework.lang.Nullable;
import java.time.Duration;
import java.util.Objects;

import static org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters.CLOCK_SKEW;
import static org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters.STMT_AUTHN_MAX_TIME;
@Configuration
@EnableWebSecurity
@ComponentScan(basePackages = "com.epam.pipeline.external.datastorage.security")
@Slf4j
public class SecurityConfiguration {

    private static final String[] SECURED_RESOURCES_ROOT = new String[] {
        "/",
        "/index.html",
        "/static/**",
        "/saml/**",
        "/restapi/**"
    };

    private static final String SAML_REGISTRATION_ID = "SSO";

    private static final int RESPONSE_SKEW = 1200;

    @Value("${server.ssl.endpoint.id}")
    private String endpointId;

    @Value("${server.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${server.ssl.keyAlias}")
    private String keyAlias;

    @Value("${saml.sign.key}")
    private String signingKey;

    private final String loginFailureRedirect;
    private final Long maxAuthentificationAge;
    private final SAMLUserDetailsService userDetailsService;
    private final SamlRelyingPartyRegistrationBuilder relyingPartyRegistrationBuilder;

    public SecurityConfiguration(
            @Value("${saml.login.failure.redirect:/error}") final String loginFailureRedirect,
            @Value("${saml.authn.max.authentication.age:93600}") final Long maxAuthentificationAge,
            final SAMLUserDetailsService userDetailsService,
            final SamlRelyingPartyRegistrationBuilder relyingPartyRegistrationBuilder) {
        this.loginFailureRedirect = loginFailureRedirect;
        this.maxAuthentificationAge = maxAuthentificationAge;
        this.userDetailsService = userDetailsService;
        this.relyingPartyRegistrationBuilder = relyingPartyRegistrationBuilder;
    }

    @Bean
    public SecurityFilterChain samlFilterChain(final HttpSecurity http) throws Exception {
        final var relyingPartyRepo = relyingPartyRegistrationRepository();
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .securityMatcher("/**")
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS).permitAll();
                    auth.requestMatchers(SECURED_RESOURCES_ROOT).authenticated();
                    auth.requestMatchers("/saml/web/**").permitAll();
                    auth.anyRequest().permitAll();
                })
                .saml2Login(saml2 -> saml2
                        .relyingPartyRegistrationRepository(relyingPartyRepo)
                        .successHandler(successRedirectHandler())
                        .failureHandler(authenticationFailureHandler())
                        .authenticationManager(samlAuthenticationProviderManager(samlAuthenticationProvider()))
                        .loginProcessingUrl("/saml/SSO")
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(logoutSuccessHandler())
                        .logoutUrl("/saml/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                ).build();
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        return new InMemoryRelyingPartyRegistrationRepository(relyingPartyRegistrationBuilder.build());
    }

    @Bean
    public RelyingPartyRegistrationResolver relyingPartyRegistrationResolver(
            final RelyingPartyRegistrationRepository repository) {
        final DefaultRelyingPartyRegistrationResolver defaultResolver =
                new DefaultRelyingPartyRegistrationResolver(repository);
        return (request, registrationId) -> {
            RelyingPartyRegistration registration = defaultResolver.resolve(request, registrationId);
            if (registration == null) {
                registration = defaultResolver.resolve(request, SAML_REGISTRATION_ID);
            }
            return registration;
        };
    }

    @Bean
    public OpenSaml4AuthenticationProvider samlAuthenticationProvider() {
        final var authenticationProvider = new OpenSaml4AuthenticationProvider();
        authenticationProvider.setResponseAuthenticationConverter(this::convertSAMLResponse);
        authenticationProvider.setAssertionValidator(OpenSaml4AuthenticationProvider
                .createDefaultAssertionValidatorWithParameters(params -> {
                    params.put(CLOCK_SKEW, Duration.ofSeconds(RESPONSE_SKEW));
                    params.put(STMT_AUTHN_MAX_TIME, Duration.ofSeconds(maxAuthentificationAge));
                }));
        return authenticationProvider;
    }

    @Bean
    public ProviderManager samlAuthenticationProviderManager(
            final OpenSaml4AuthenticationProvider samlAuthenticationProvider) {
        return new ProviderManager(samlAuthenticationProvider);
    }

    @Bean
    public SavedRequestAwareAuthenticationSuccessHandler successRedirectHandler() {
        final var handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        handler.setAlwaysUseDefaultTargetUrl(false);
        return handler;
    }

    @Bean
    public SimpleUrlAuthenticationFailureHandler authenticationFailureHandler() {
        final var handler = new SimpleUrlAuthenticationFailureHandler();
        handler.setDefaultFailureUrl(loginFailureRedirect);
        return handler;
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        final var handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }

    @Nullable
    private UsernamePasswordAuthenticationToken convertSAMLResponse(
            final OpenSaml4AuthenticationProvider.ResponseToken responseToken) {
        final Saml2Authentication authentication = OpenSaml4AuthenticationProvider
                .createDefaultResponseAuthenticationConverter()
                .convert(responseToken);
        if (Objects.isNull(authentication)) {
            log.debug("Cannot parse SAML2 response.");
            return null;
        }
        final var credential = (DefaultSaml2AuthenticatedPrincipal) authentication.getPrincipal();
        final UserContext userContext = userDetailsService.loadUserBySAML(
                credential, authentication.getSaml2Response());

        final var authenticationToken = new UsernamePasswordAuthenticationToken(
                userContext, authentication.getSaml2Response(), userContext.getAuthorities());
        authenticationToken.setDetails(responseToken.getToken().getDetails());
        return authenticationToken;
    }
}
