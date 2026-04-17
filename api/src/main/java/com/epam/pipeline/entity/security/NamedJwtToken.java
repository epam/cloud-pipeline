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

package com.epam.pipeline.entity.security;

import lombok.Builder;
import lombok.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Persisted metadata for an issued user JWT registered in the named-token table (see issue #4327).
 */
@Value
@Builder
public class NamedJwtToken implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Pattern TOKEN_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    String jti;
    Long userId;
    Long createdBy;
    /**
     * Optional user-defined label to distinguish tokens in the registry (not part of the JWT payload).
     */
    String tokenName;
    LocalDateTime issuedAt;
    LocalDateTime expiresAt;
    /**
     * Raw JWT string. Present when the token is freshly issued; not loaded from the registry (list APIs omit it).
     */
    String token;

    /**
     * Trims the input; returns {@code null} if blank. Non-blank values must match
     * {@code [A-Za-z0-9_-]+} (optional {@code tokenId} / registry label on issue-token requests).
     */
    @Nullable
    public static String normalizeTokenId(@Nullable final String raw) {
        if (raw == null) {
            return null;
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!TOKEN_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Token registry label may contain only letters, digits, underscore (_), and hyphen (-).");
        }
        return trimmed;
    }
}
