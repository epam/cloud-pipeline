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

package com.epam.pipeline.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A successful token exchange, in the shape RFC 8693 defines.
 * <p>
 * Deliberately not wrapped in the platform's own {@code Result} envelope: an ordinary OAuth client
 * library reads this response, and it expects these field names at the top level.
 */
@Getter
@RequiredArgsConstructor
public class TokenExchangeResponse {

    @JsonProperty("access_token")
    private final String accessToken;

    @JsonProperty("issued_token_type")
    private final String issuedTokenType;

    @JsonProperty("token_type")
    private final String tokenType;

    @JsonProperty("expires_in")
    private final long expiresIn;
}
