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

/**
 * A subject token was not accepted.
 * <p>
 * The message says why, for the log. It is never returned to the caller: the exchange endpoint is
 * reachable without a platform token, so telling an unknown caller which check failed would help
 * them work out what a valid token has to look like.
 */
public class OidcTokenVerificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OidcTokenVerificationException(final String message) {
        super(message);
    }

    public OidcTokenVerificationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
