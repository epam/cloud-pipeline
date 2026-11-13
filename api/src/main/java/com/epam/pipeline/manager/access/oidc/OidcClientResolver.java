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

import com.epam.pipeline.security.oidc.OidcClaimRule;
import com.epam.pipeline.security.oidc.OidcClient;
import com.epam.pipeline.security.oidc.OidcIssuer;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;

/**
 * Works out which configured caller a verified token belongs to.
 * <p>
 * The clients of an issuer are tried in the order they are configured and the first one that
 * matches wins, so a narrower entry has to be placed above a wider one that would also match. The
 * matched client is named in the log of every exchange, which is the only way an administrator can
 * see that an entry they added is being shadowed by one above it.
 */
@Service
@Slf4j
public class OidcClientResolver {

    private static final String SCOPE_CLAIM = "scp";
    private static final String STANDARD_SCOPE_CLAIM = "scope";

    /**
     * Empty when the token matches no configured client. That is not the same as a token that
     * failed verification: the token is genuine, but no caller has been granted anything with it.
     */
    public Optional<OidcClient> resolve(final OidcVerifiedToken token) {
        final OidcIssuer issuer = token.getIssuer();
        for (final OidcClient client : ListUtils.emptyIfNull(issuer.getClients())) {
            if (matches(client, token.getClaims(), issuer.getIssuer())) {
                log.debug("Token from issuer {} matched client {}", issuer.getIssuer(), client.getId());
                return Optional.of(client);
            }
        }
        log.warn("Token from issuer {} with subject {} matched no configured client",
                issuer.getIssuer(), token.getClaims().getSubject());
        return Optional.empty();
    }

    private boolean matches(final OidcClient client, final JWTClaimsSet claims, final String issuerName) {
        if (Objects.isNull(client) || CollectionUtils.isEmpty(client.getRules())) {
            // a client with no conditions would match every token of this issuer; the preference
            // validator refuses one, and it is refused again here
            log.error("Client {} of issuer {} has no conditions and is ignored",
                    Objects.isNull(client) ? null : client.getId(), issuerName);
            return false;
        }
        for (final OidcClaimRule rule : client.getRules()) {
            if (!OidcClaimMatcher.matches(rule, claims)) {
                log.debug("Client {} did not match: condition on claim {} failed",
                        client.getId(), rule.getClaim());
                return false;
            }
        }
        return hasRequiredScopes(client, claims);
    }

    /**
     * Scopes are part of matching rather than a separate gate, so two clients may differ only by
     * the scope they require and a token is routed to the one it actually carries the scope for.
     */
    private boolean hasRequiredScopes(final OidcClient client, final JWTClaimsSet claims) {
        if (CollectionUtils.isEmpty(client.getRequiredScopes())) {
            return true;
        }
        final Collection<String> granted = grantedScopes(claims);
        if (!granted.containsAll(client.getRequiredScopes())) {
            log.debug("Client {} did not match: it requires scopes {} and the token carries {}",
                    client.getId(), client.getRequiredScopes(), granted);
            return false;
        }
        return true;
    }

    /**
     * Entra ID and Okta name the claim {@code scp}; the claim registered by OAuth itself, which
     * other providers use, is {@code scope}.
     */
    private Collection<String> grantedScopes(final JWTClaimsSet claims) {
        final Collection<String> provider = OidcClaimMatcher.readScopes(claims.getClaim(SCOPE_CLAIM));
        // falling back on an unreadable or empty scp, not merely an absent one, so a provider that
        // sends both an empty scp and real scopes in scope is still understood
        final Collection<String> scopes = provider.isEmpty()
                ? OidcClaimMatcher.readScopes(claims.getClaim(STANDARD_SCOPE_CLAIM))
                : provider;
        return new HashSet<>(scopes);
    }
}
