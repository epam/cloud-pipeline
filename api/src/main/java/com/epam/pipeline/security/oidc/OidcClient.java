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

/**
 * One caller trusted under an {@link OidcIssuer}.
 * <p>
 * An issuer is not a caller: every repository on github.com shares one issuer, and so does every
 * application registered in one Entra ID tenant. A client is the unit that authorization is
 * actually expressed against — the claims that identify the caller, and the user it may act as.
 */
@Getter
@Setter
public class OidcClient {

    /**
     * Identifies this client in the audit log, so an administrator can tell which one a token was
     * issued through. A stable name rather than a position, because a position shifts whenever the
     * list above it is edited.
     * <p>
     * Unique within its issuer rather than globally, so the audit line names the issuer alongside
     * it.
     */
    private String id;

    /**
     * The conditions identifying the caller. All of them must pass.
     */
    private List<OidcClaimRule> rules;

    /**
     * Scopes the subject token must carry, read from its {@code scp} claim.
     * <p>
     * Its own field rather than a rule, because the claim's shape differs between providers: Entra
     * ID sends one space-separated string and Okta sends a list, and a substring match on the
     * string form would accept {@code access_as_user_admin} for {@code access_as_user}.
     */
    private List<String> requiredScopes;

    /**
     * Which user a token is issued for, and what it may do.
     */
    private OidcAssumeStrategy assumeStrategy;
}
