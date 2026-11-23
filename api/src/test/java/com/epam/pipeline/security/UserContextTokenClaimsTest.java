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

package com.epam.pipeline.security;

import com.epam.pipeline.entity.security.JwtRawToken;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The flag that stops a token being renewed.
 */
public class UserContextTokenClaimsTest {

    private static final String USER_NAME = "JANE";
    private static final String RAW_TOKEN = "token";

    /**
     * Nothing that works today may stop working, so a context is renewable unless the token
     * explicitly withholds it.
     */
    @Test
    public void shouldBeRenewableUnlessTheTokenSaysOtherwise() {
        assertThat(new UserContext(new JwtRawToken(RAW_TOKEN), claims()).isNonRenewable())
                .isFalse();
        assertThat(new UserContext().isNonRenewable()).isFalse();

        final JwtTokenClaims withheld = claimsBuilder().nonRenewable(true).build();
        assertThat(new UserContext(new JwtRawToken(RAW_TOKEN), withheld).isNonRenewable()).isTrue();
    }

    @Test
    public void shouldCarryTheNonRenewableFlagBackIntoClaims() {
        final JwtTokenClaims withheld = claimsBuilder().nonRenewable(true).build();
        final UserContext context = new UserContext(new JwtRawToken(RAW_TOKEN), withheld);

        assertThat(context.toClaims().isNonRenewable()).isTrue();
    }

    private static JwtTokenClaims claims() {
        return claimsBuilder().build();
    }

    private static JwtTokenClaims.JwtTokenClaimsBuilder claimsBuilder() {
        return JwtTokenClaims.builder()
                .jwtTokenId(UUID.randomUUID().toString())
                .userName(USER_NAME)
                .userId("1")
                .orgUnitId("")
                .roles(Collections.singletonList("ROLE_USER"))
                .groups(Collections.<String>emptyList());
    }
}
