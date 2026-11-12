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

import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserAccessService;
import com.epam.pipeline.security.jwt.JwtTokenGenerator;
import com.epam.pipeline.security.oidc.OidcAssumeStrategy;
import com.epam.pipeline.security.oidc.OidcAssumeStrategyType;
import com.epam.pipeline.security.oidc.OidcClient;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exchanges an identity provider's access token for a platform token, as RFC 8693 describes.
 * <p>
 * The order of the checks matters. The token is shown to be genuine first, then matched to a
 * configured caller, and only then is a user involved — so an unknown caller never causes a user
 * lookup, and nothing about the platform's users can be learned by presenting tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OidcTokenExchangeManager {

    private static final long MILLIS_IN_SECOND = 1000L;

    private final OidcTokenVerifier tokenVerifier;
    private final OidcClientResolver clientResolver;
    private final UserManager userManager;
    private final UserAccessService userAccessService;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final PreferenceManager preferenceManager;

    public OidcExchangedToken exchange(final String subjectToken) {
        final OidcVerifiedToken verified = tokenVerifier.verify(subjectToken);
        final OidcClient client = clientResolver.resolve(verified)
                .orElseThrow(() -> new OidcClientNotAuthorizedException(String.format(
                        "No client of issuer %s matches the presented token",
                        verified.getIssuer().getIssuer())));
        final OidcAssumeStrategy strategy = strategyOf(client);
        final PipelineUser user = resolveUser(verified, client, strategy);
        checkNotBlocked(user, client);

        final long lifetime = resolveLifetime(verified, client, strategy);
        final JwtTokenClaims claims = buildClaims(verified, client, strategy, user);
        final String token = jwtTokenGenerator.encodeToken(claims, lifetime);

        log.info("Exchanged a token of issuer {} (subject {}) through client {} for user {}, "
                        + "valid {}s, jti {}{}",
                verified.getIssuer().getIssuer(), verified.getClaims().getSubject(), client.getId(),
                user.getUserName(), lifetime, claims.getJwtTokenId(),
                claims.isAuthoritiesScoped() ? ", authorities scoped" : StringUtils.EMPTY);
        return new OidcExchangedToken(token, lifetime);
    }

    /**
     * The preference validator requires a strategy, so one missing here means a configuration that
     * reached the preference another way. It is refused rather than dereferenced, which would turn
     * a bad configuration into a server error on an endpoint that needs no platform token.
     */
    private OidcAssumeStrategy strategyOf(final OidcClient client) {
        final OidcAssumeStrategy strategy = client.getAssumeStrategy();
        if (Objects.isNull(strategy) || Objects.isNull(strategy.getType())) {
            throw new OidcClientNotAuthorizedException(String.format(
                    "Client %s says nothing about which user it may act as", client.getId()));
        }
        return strategy;
    }

    /**
     * Wrapped so that one exception type leaves this class for every refusal. The block checks
     * throw a message naming the user, and the endpoint must be able to answer uniformly without
     * having to know which of several types it might be catching.
     */
    private void checkNotBlocked(final PipelineUser user, final OidcClient client) {
        try {
            userAccessService.validateUserBlockStatus(user);
            userAccessService.validateUserGroupsBlockStatus(user);
        } catch (LockedException e) {
            throw new OidcTokenVerificationException(String.format(
                    "User %s named through client %s is blocked", user.getUserName(), client.getId()), e);
        }
    }

    private PipelineUser resolveUser(final OidcVerifiedToken verified, final OidcClient client,
                                     final OidcAssumeStrategy strategy) {
        final String userName = userNameOf(verified, client, strategy);
        checkUserAllowed(userName, client, strategy);
        final PipelineUser user = userManager.loadUserByName(userName);
        if (Objects.isNull(user)) {
            // the exchange never creates a user; an unknown name means the configuration names
            // somebody who does not exist, or the provider sent a user the platform has not seen
            throw new OidcTokenVerificationException(String.format(
                    "User %s named through client %s does not exist", userName, client.getId()));
        }
        return user;
    }

    /**
     * Upper-cased, so that a provider sending a lower-case address names the same platform user
     * either way. The SAML login upper-cases too, though with the default locale rather than a
     * fixed one; a fixed locale is used here so the result does not depend on where the server
     * runs.
     */
    private String userNameOf(final OidcVerifiedToken verified, final OidcClient client,
                              final OidcAssumeStrategy strategy) {
        if (OidcAssumeStrategyType.SPECIFIC_USER == strategy.getType()) {
            return normalize(strategy.getUser());
        }
        final Object claim = verified.getClaims().getClaim(strategy.getUserClaim());
        if (!(claim instanceof String) || StringUtils.isBlank((String) claim)) {
            throw new OidcTokenVerificationException(String.format(
                    "Claim %s of the token presented through client %s names no user",
                    strategy.getUserClaim(), client.getId()));
        }
        return normalize((String) claim);
    }

    private void checkUserAllowed(final String userName, final OidcClient client,
                                  final OidcAssumeStrategy strategy) {
        final Set<String> allowed = normalizeAll(strategy.getAllowedUsers());
        if (CollectionUtils.isNotEmpty(allowed) && !allowed.contains(userName)) {
            throw new OidcClientNotAuthorizedException(String.format(
                    "Client %s may not act as user %s", client.getId(), userName));
        }
        if (normalizeAll(strategy.getDeniedUsers()).contains(userName)) {
            throw new OidcClientNotAuthorizedException(String.format(
                    "Client %s is denied acting as user %s", client.getId(), userName));
        }
    }

    /**
     * The smallest of what the client allows, what the issuer allows, the platform-wide cap, and
     * what is left of the subject token — the last unless the client asks to ignore it.
     */
    private long resolveLifetime(final OidcVerifiedToken verified, final OidcClient client,
                                 final OidcAssumeStrategy strategy) {
        final int cap = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_TOKEN_LIFETIME);
        if (cap <= 0) {
            // said separately from the refusal below, so that a preference set to nonsense does
            // not send an administrator looking for a fault at the identity provider
            throw new OidcTokenVerificationException(String.format(
                    "Preference %s is %d, so no token can be issued",
                    SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_TOKEN_LIFETIME.getKey(), cap));
        }
        long lifetime = cap;
        lifetime = atMost(lifetime, verified.getIssuer().getMaxTokenLifetime());
        lifetime = atMost(lifetime, strategy.getMaxTokenLifetime());
        if (!strategy.isIgnoreSubjectTokenLifetime()) {
            lifetime = atMost(lifetime, remainingLifeOf(verified.getClaims()));
        }
        if (lifetime <= 0) {
            throw new OidcTokenVerificationException(String.format(
                    "Nothing is left of the token presented through client %s to exchange",
                    client.getId()));
        }
        return lifetime;
    }

    private static long atMost(final long lifetime, final Long limit) {
        return Objects.isNull(limit) ? lifetime : Math.min(lifetime, limit);
    }

    private static Long remainingLifeOf(final JWTClaimsSet claims) {
        final Date expiry = claims.getExpirationTime();
        if (Objects.isNull(expiry)) {
            return null;
        }
        return (expiry.getTime() - System.currentTimeMillis()) / MILLIS_IN_SECOND;
    }

    private JwtTokenClaims buildClaims(final OidcVerifiedToken verified, final OidcClient client,
                                       final OidcAssumeStrategy strategy, final PipelineUser user) {
        final List<String> allRoles = roleNames(user);
        final List<String> allGroups = ListUtils.emptyIfNull(user.getGroups());
        final List<String> roles = shape(allRoles, strategy);
        final List<String> groups = shape(allGroups, strategy);
        final boolean narrowed = roles.size() < allRoles.size() || groups.size() < allGroups.size();
        if (groups.size() < allGroups.size()) {
            // said out loud because a keep list of roles alone removes every group, and the
            // permissions a group carries then disappear with no other sign of it
            log.info("Client {} removed {} of the {} groups of user {} from the issued token",
                    client.getId(), allGroups.size() - groups.size(), allGroups.size(),
                    user.getUserName());
        }
        return JwtTokenClaims.builder()
                .jwtTokenId(UUID.randomUUID().toString())
                // guarded the way UserContext.toClaims does it, so a user without an id yields a
                // null rather than the literal string "null", which reads back as an absent id
                .userId(Optional.ofNullable(user.getId()).map(Objects::toString).orElse(null))
                .userName(user.getUserName())
                .orgUnitId(StringUtils.EMPTY)
                .roles(roles)
                .groups(groups)
                .external(strategy.isExternal())
                .authoritiesScoped(narrowed)
                .exchangeable(strategy.isExchangeable())
                .originIssuer(verified.getIssuer().getIssuer())
                .originSubject(verified.getClaims().getSubject())
                .originClient(client.getId())
                .build();
    }

    /**
     * Keeps only what the client allows out of what the user already holds, then removes what it
     * drops. Both steps can only make the set smaller, so an authority the user does not hold is
     * never granted by naming it in the configuration.
     */
    private List<String> shape(final Collection<String> authorities, final OidcAssumeStrategy strategy) {
        final Set<String> kept = new HashSet<>(ListUtils.emptyIfNull(strategy.getAuthoritiesToKeep()));
        final Set<String> dropped = new HashSet<>(ListUtils.emptyIfNull(strategy.getAuthoritiesToDrop()));
        return authorities.stream()
                .filter(authority -> kept.isEmpty() || kept.contains(authority))
                .filter(authority -> !dropped.contains(authority))
                .collect(Collectors.toList());
    }

    private static List<String> roleNames(final PipelineUser user) {
        return ListUtils.emptyIfNull(user.getRoles()).stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    private static String normalize(final String userName) {
        return StringUtils.upperCase(StringUtils.trimToEmpty(userName), Locale.ENGLISH);
    }

    private static Set<String> normalizeAll(final Collection<String> userNames) {
        if (Objects.isNull(userNames)) {
            return Collections.emptySet();
        }
        return userNames.stream()
                .map(OidcTokenExchangeManager::normalize)
                .collect(Collectors.toSet());
    }
}
