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

package com.epam.pipeline.manager.access.oidc;

import com.epam.pipeline.security.oidc.OidcIssuer;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A subject token whose signature and claims have been checked, together with the configured
 * issuer entry it was checked against.
 */
@Getter
@RequiredArgsConstructor
public class OidcVerifiedToken {

    /**
     * The configured entry the token's {@code iss} selected, carrying the clients to match it
     * against.
     */
    private final OidcIssuer issuer;

    /**
     * The verified claims. Safe to read for authorization, unlike the claims of an unverified
     * token.
     */
    private final JWTClaimsSet claims;
}
