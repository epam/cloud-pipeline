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
 * Which user a matched {@link OidcClient} acts as, and what the token issued for it may do.
 * <p>
 * One flat object rather than a type hierarchy: every field is optional here, and the combinations
 * that cannot work are rejected when the preference is saved.
 */
@Getter
@Setter
public class OidcAssumeStrategy {

    /**
     * Where the user name comes from.
     */
    private OidcAssumeStrategyType type;

    /**
     * The fixed user name, for {@link OidcAssumeStrategyType#SPECIFIC_USER} only.
     */
    private String user;

    /**
     * The claim naming the user, for {@link OidcAssumeStrategyType#REQUESTED_USER} only.
     */
    private String userClaim;

    /**
     * The only users this client may act as. Absent means no limit; empty is a configuration
     * error rather than a way of saying "anyone".
     */
    private List<String> allowedUsers;

    /**
     * Users this client may never act as. Checked after {@link #allowedUsers}, so a user named in
     * both is denied. Naming the same user in both lists is allowed, since narrowing to a group and
     * then excluding one of its members is a reasonable thing to configure.
     */
    private List<String> deniedUsers;

    /**
     * Keep only these authorities out of the ones the user already holds. Absent means keep them
     * all. Never widens: an authority the user does not hold is not granted by listing it here.
     */
    private List<String> authoritiesToKeep;

    /**
     * Remove these authorities, applied after {@link #authoritiesToKeep}.
     * <p>
     * Listing an authority in both lists is rejected when the configuration is saved, because one
     * of the two entries is certainly not what was meant. The ordering still matters for a
     * configuration that predates that check or was written straight into the database, and there a
     * drop wins.
     */
    private List<String> authoritiesToDrop;

    /**
     * The longest life, in seconds, of a token issued through this client.
     */
    private Long maxTokenLifetime;

    /**
     * Let the issued token outlive the subject token it was exchanged for.
     * <p>
     * Off by default, so a platform token normally dies with the identity provider's token and
     * ending a user's session at the provider ends their platform access too. It exists for CI
     * tokens, which are short by design — a GitHub Actions token expires minutes after it is
     * issued, which would otherwise kill any long-running step — and which carry no revocable
     * session to begin with.
     */
    private boolean ignoreSubjectTokenLifetime;

    /**
     * Mark the issued token external, which limits data storage access to shared storages.
     * <p>
     * Off by default, unlike
     * {@link com.epam.pipeline.security.ExternalServiceEndpoint#isExternal()}, which defaults to on.
     * The two are configured for different things: that one exists for services outside the
     * platform, whereas a caller here is as often a CI job that has to reach ordinary storages. A
     * deployment that wants the restriction turns it on per client.
     */
    private boolean external;
}
