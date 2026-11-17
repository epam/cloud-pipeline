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

package com.epam.pipeline.security.oidc;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * An identity provider whose access tokens may be exchanged for a platform token.
 * <p>
 * This entry carries only what belongs to the provider itself — how to find and check its signing
 * keys, and which audiences and algorithms are acceptable. Authorization lives in {@link #clients}.
 */
@Getter
@Setter
public class OidcIssuer {

    /**
     * Matched against the subject token's {@code iss} claim, exactly, and used as the base for
     * discovery. The token selects a configured entry; it never names where its own keys live.
     */
    private String issuer;

    /**
     * An inline JWK Set, for a deployment whose API cannot reach the provider. It cannot pick up a
     * key rotation on its own, so it must list every key the provider publishes and be updated
     * whenever they change.
     */
    private Map<String, Object> jwks;

    /**
     * An explicit JWKS URL, which skips discovery.
     */
    private String jwksUri;

    /**
     * The subject token's {@code aud} claim must contain one of these. A value unique to this
     * deployment, such as its API URL, keeps a token minted for one deployment from being replayed
     * at another.
     */
    private List<String> audiences;

    /**
     * Accepted signing algorithms. Absent means the defaults, {@code RS256} and {@code ES256}.
     */
    private List<String> algorithms;

    /**
     * The longest life, in seconds, of any token issued under this issuer.
     */
    private Long maxTokenLifetime;

    /**
     * The callers trusted under this issuer, tried in order, the first match winning.
     */
    private List<OidcClient> clients;
}
