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

package com.epam.pipeline.security.jwt;

import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtTokenGeneratorTest {

    private static final String USER_NAME = "JANE";
    private static final String USER_ID = "1";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String READER = "READER";
    private static final String GROUP = "BIOINFORMATICS";
    private static final int EXPIRATION_SECONDS = 3600;
    private static final int KEY_SIZE = 2048;

    private JwtTokenGenerator generator;
    private JwtTokenVerifier verifier;

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(KEY_SIZE);
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();
        final String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        final String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        final PreferenceManager preferenceManager = Mockito.mock(PreferenceManager.class);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.LAUNCH_JWT_TOKEN_EXPIRATION))
                .thenReturn(EXPIRATION_SECONDS);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.LAUNCH_JWT_TOKEN_EXPIRATION_USER_LIMIT))
                .thenReturn(EXPIRATION_SECONDS);

        generator = new JwtTokenGenerator();
        ReflectionTestUtils.setField(generator, "privateKeyString", privateKey);
        ReflectionTestUtils.setField(generator, "publicKeyString", publicKey);
        ReflectionTestUtils.setField(generator, "preferenceManager", preferenceManager);
        generator.initKey();

        verifier = new JwtTokenVerifier(publicKey);
    }

    /**
     * Without this the restriction would be lost the moment the token was serialized, and a
     * narrowed token would behave as an ordinary one.
     */
    @Test
    public void shouldCarryTheScopedFlagThroughTheToken() {
        final JwtTokenClaims read = roundTrip(claims().authoritiesScoped(true).build());

        assertThat(read.isAuthoritiesScoped()).isTrue();
    }

    @Test
    public void shouldLeaveTheFlagOffWhenItWasNotAskedFor() {
        assertThat(roundTrip(claims().build()).isAuthoritiesScoped()).isFalse();
    }

    @Test
    public void shouldKeepTheFlagIndependentOfTheExternalFlag() {
        final JwtTokenClaims externalOnly = roundTrip(claims().external(true).build());
        assertThat(externalOnly.isExternal()).isTrue();
        assertThat(externalOnly.isAuthoritiesScoped()).isFalse();

        final JwtTokenClaims finalOnly = roundTrip(claims().authoritiesScoped(true).build());
        assertThat(finalOnly.isExternal()).isFalse();
        assertThat(finalOnly.isAuthoritiesScoped()).isTrue();

        final JwtTokenClaims both = roundTrip(claims().external(true).authoritiesScoped(true).build());
        assertThat(both.isExternal()).isTrue();
        assertThat(both.isAuthoritiesScoped()).isTrue();
    }

    /**
     * Absent means exchangeable, so every token issued before this claim existed keeps working.
     */
    @Test
    public void shouldTreatATokenWithoutTheClaimAsExchangeable() {
        assertThat(roundTrip(claims().build()).isExchangeable()).isTrue();
    }

    @Test
    public void shouldCarryAWithheldExchangeableFlagThroughTheToken() {
        assertThat(roundTrip(claims().exchangeable(false).build()).isExchangeable()).isFalse();
    }


    @Test
    public void shouldCarryTheNarrowedAuthoritiesThemselves() {
        final JwtTokenClaims read = roundTrip(claims()
                .roles(Collections.singletonList(ROLE_USER))
                .groups(Collections.<String>emptyList())
                .authoritiesScoped(true)
                .build());

        assertThat(read.getRoles()).containsExactly(ROLE_USER);
        assertThat(read.getGroups()).isEmpty();
    }

    private JwtTokenClaims roundTrip(final JwtTokenClaims claims) {
        return verifier.readClaims(generator.encodeToken(claims, (long) EXPIRATION_SECONDS));
    }

    private static JwtTokenClaims.JwtTokenClaimsBuilder claims() {
        return JwtTokenClaims.builder()
                .jwtTokenId(UUID.randomUUID().toString())
                .userName(USER_NAME)
                .userId(USER_ID)
                .orgUnitId("")
                .roles(Arrays.asList(ROLE_USER, READER))
                .groups(Collections.singletonList(GROUP));
    }
}
