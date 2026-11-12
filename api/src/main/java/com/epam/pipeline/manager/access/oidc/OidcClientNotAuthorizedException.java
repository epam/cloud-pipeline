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
 * The subject token is genuine, but the caller presenting it has been granted nothing.
 * <p>
 * Kept apart from {@link OidcTokenVerificationException} because the two mean different things to
 * the caller: that one says the token itself was not accepted, this one says no configured client
 * matches it, or the client that matches may not act as the user it names. As with that one, the
 * message is for the log and never for the response.
 */
public class OidcClientNotAuthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OidcClientNotAuthorizedException(final String message) {
        super(message);
    }
}
