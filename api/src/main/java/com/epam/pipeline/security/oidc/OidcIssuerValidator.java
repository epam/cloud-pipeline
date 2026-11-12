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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks a token exchange configuration before it is saved.
 * <p>
 * Two principles run through it. A list that is present but empty is always an error, never a way
 * of saying "allow everything" — an administrator who means "no limit" leaves the field out. And a
 * client must identify its caller with at least one exact condition: identity providers share an
 * issuer between callers, so the conditions are the only boundary there is, and a client built out
 * of patterns alone is too easy to widen by accident.
 */
public final class OidcIssuerValidator {

    /**
     * Only asymmetric algorithms, because the keys arrive as a public key set. An allow-list rather
     * than a ban on {@code none} and the HMAC family: those are the dangerous values, but a
     * mistyped one such as {@code RS255} would otherwise save and then fail every exchange, with
     * nothing to tell the administrator why.
     */
    private static final Set<String> SUPPORTED_ALGORITHMS = new HashSet<>(Arrays.asList(
            "RS256", "RS384", "RS512",
            "ES256", "ES384", "ES512",
            "PS256", "PS384", "PS512",
            "EdDSA"));
    private static final String HTTPS_PROTOCOL = "https";
    private static final String JWKS_KEYS_FIELD = "keys";
    private static final String JWKS_KEY_TYPE_FIELD = "kty";
    private static final String ANY_CHARACTERS_WILDCARD = "*";
    private static final String SINGLE_CHARACTER_WILDCARD = "?";

    private OidcIssuerValidator() {
        // utility class
    }

    /**
     * An absent or empty configuration simply means the exchange is not set up, so it is valid.
     * Everything nested inside a configuration that does exist must be complete.
     */
    public static boolean isValid(final List<OidcIssuer> issuers) {
        if (CollectionUtils.isEmpty(issuers)) {
            return true;
        }
        return hasNoDuplicates(issuers, OidcIssuer::getIssuer)
                && issuers.stream().allMatch(OidcIssuerValidator::isValidIssuer);
    }

    private static boolean isValidIssuer(final OidcIssuer issuer) {
        return Objects.nonNull(issuer)
                && isSecureUrl(issuer.getIssuer())
                && isValidKeySource(issuer)
                && isPresentWithoutBlanks(issuer.getAudiences())
                && isValidAlgorithms(issuer.getAlgorithms())
                && isValidLifetime(issuer.getMaxTokenLifetime())
                && isValidClients(issuer.getClients());
    }

    /**
     * Both the issuer, which is the base for discovery, and an explicit key set URL are fetched
     * over the network to decide whether a token's signature is genuine. Over plain HTTP anyone on
     * the path can answer with their own key set and have tokens they signed themselves accepted,
     * so the scheme is part of the check rather than a deployment detail.
     */
    private static boolean isSecureUrl(final String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            return HTTPS_PROTOCOL.equalsIgnoreCase(new URL(value).getProtocol());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * An inline key set and a key set URL are alternatives, so setting both would leave the
     * precedence between them to the implementation rather than to the administrator. A key set
     * URL that is present but blank counts as set, or it would slip past that rule.
     */
    private static boolean isValidKeySource(final OidcIssuer issuer) {
        if (Objects.nonNull(issuer.getJwksUri())) {
            return Objects.isNull(issuer.getJwks()) && isSecureUrl(issuer.getJwksUri());
        }
        return Objects.isNull(issuer.getJwks()) || isValidInlineKeySet(issuer.getJwks());
    }

    /**
     * A structural check only — the key material itself is parsed when the issuer is first used.
     * It catches the paste that brought across the wrong part of the provider's document.
     */
    private static boolean isValidInlineKeySet(final Map<String, Object> jwks) {
        final Object keys = jwks.get(JWKS_KEYS_FIELD);
        if (!(keys instanceof Collection) || CollectionUtils.isEmpty((Collection<?>) keys)) {
            return false;
        }
        return ((Collection<?>) keys).stream().allMatch(OidcIssuerValidator::isValidInlineKey);
    }

    private static boolean isValidInlineKey(final Object key) {
        return key instanceof Map
                && ((Map<?, ?>) key).get(JWKS_KEY_TYPE_FIELD) instanceof String
                && StringUtils.isNotBlank((String) ((Map<?, ?>) key).get(JWKS_KEY_TYPE_FIELD));
    }

    private static boolean isValidAlgorithms(final List<String> algorithms) {
        if (Objects.isNull(algorithms)) {
            return true;
        }
        return isPresentWithoutBlanks(algorithms)
                && algorithms.stream().allMatch(algorithm -> SUPPORTED_ALGORITHMS.contains(algorithm.trim()));
    }

    private static boolean isValidClients(final List<OidcClient> clients) {
        return CollectionUtils.isNotEmpty(clients)
                && hasNoDuplicates(clients, OidcClient::getId)
                && clients.stream().allMatch(OidcIssuerValidator::isValidClient);
    }

    private static boolean isValidClient(final OidcClient client) {
        return Objects.nonNull(client)
                && StringUtils.isNotBlank(client.getId())
                && isValidRules(client.getRules())
                && isValidRequiredScopes(client.getRequiredScopes())
                && isValidStrategy(client.getAssumeStrategy());
    }

    /**
     * A scope is a single token and cannot contain a space, so an entry holding one could never
     * match either shape the claim arrives in — a space-separated string or a list.
     */
    private static boolean isValidRequiredScopes(final List<String> scopes) {
        return isAbsentOrWithoutBlanks(scopes)
                && (Objects.isNull(scopes) || scopes.stream().noneMatch(StringUtils::containsWhitespace));
    }

    private static boolean isValidRules(final List<OidcClaimRule> rules) {
        return CollectionUtils.isNotEmpty(rules)
                && rules.stream().allMatch(OidcIssuerValidator::isValidRule)
                && rules.stream().anyMatch(rule -> OidcClaimOperator.EQUALS == rule.getOperator());
    }

    private static boolean isValidRule(final OidcClaimRule rule) {
        return Objects.nonNull(rule)
                && StringUtils.isNotBlank(rule.getClaim())
                && Objects.nonNull(rule.getOperator())
                && StringUtils.isNotBlank(rule.getValue())
                && isValidPattern(rule);
    }

    /**
     * A pattern of nothing but wildcards matches every value the claim could hold, which makes the
     * condition decoration rather than a check.
     */
    private static boolean isValidPattern(final OidcClaimRule rule) {
        if (OidcClaimOperator.LIKE != rule.getOperator()) {
            return true;
        }
        return StringUtils.isNotBlank(rule.getValue()
                .replace(ANY_CHARACTERS_WILDCARD, StringUtils.EMPTY)
                .replace(SINGLE_CHARACTER_WILDCARD, StringUtils.EMPTY));
    }

    private static boolean isValidStrategy(final OidcAssumeStrategy strategy) {
        return Objects.nonNull(strategy)
                && Objects.nonNull(strategy.getType())
                && isValidStrategyUser(strategy)
                && isAbsentOrWithoutBlanks(strategy.getAuthoritiesToKeep())
                && isAbsentOrWithoutBlanks(strategy.getAuthoritiesToDrop())
                && haveNoOverlap(strategy.getAuthoritiesToKeep(), strategy.getAuthoritiesToDrop())
                && isValidLifetime(strategy.getMaxTokenLifetime());
    }

    /**
     * Rejects a type it does not know rather than falling through to one of the branches: a
     * constant added to the enum later would otherwise be silently held to the wrong rules.
     */
    private static boolean isValidStrategyUser(final OidcAssumeStrategy strategy) {
        switch (strategy.getType()) {
            case SPECIFIC_USER:
                return isValidSpecificUserStrategy(strategy);
            case REQUESTED_USER:
                return isValidRequestedUserStrategy(strategy);
            default:
                return false;
        }
    }

    private static boolean isValidSpecificUserStrategy(final OidcAssumeStrategy strategy) {
        return StringUtils.isNotBlank(strategy.getUser())
                && StringUtils.isBlank(strategy.getUserClaim())
                && Objects.isNull(strategy.getAllowedUsers())
                && Objects.isNull(strategy.getDeniedUsers());
    }

    private static boolean isValidRequestedUserStrategy(final OidcAssumeStrategy strategy) {
        return StringUtils.isNotBlank(strategy.getUserClaim())
                && StringUtils.isBlank(strategy.getUser())
                && isAbsentOrWithoutBlanks(strategy.getAllowedUsers())
                && isAbsentOrWithoutBlanks(strategy.getDeniedUsers());
    }

    private static boolean isValidLifetime(final Long lifetime) {
        return Objects.isNull(lifetime) || lifetime > 0;
    }

    private static boolean isPresentWithoutBlanks(final List<String> values) {
        return CollectionUtils.isNotEmpty(values)
                && values.stream().noneMatch(StringUtils::isBlank);
    }

    private static boolean isAbsentOrWithoutBlanks(final List<String> values) {
        return Objects.isNull(values) || isPresentWithoutBlanks(values);
    }

    private static boolean haveNoOverlap(final List<String> first, final List<String> second) {
        if (CollectionUtils.isEmpty(first) || CollectionUtils.isEmpty(second)) {
            return true;
        }
        return second.stream().noneMatch(new HashSet<>(first)::contains);
    }

    private static <T> boolean hasNoDuplicates(final List<T> values,
                                               final Function<T, String> key) {
        final List<String> keys = values.stream()
                .filter(Objects::nonNull)
                .map(key)
                .collect(Collectors.toList());
        return keys.size() == new HashSet<>(keys).size();
    }
}
