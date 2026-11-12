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
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Decides whether one claim of a verified token satisfies one configured condition.
 * <p>
 * Every decision here fails closed. A claim the token does not carry, a claim whose shape does not
 * suit the operator, and a value that cannot be read as text all mean "does not match" rather than
 * "no condition to apply" — an identity provider shares its issuer between callers, so a condition
 * that quietly stopped applying would widen who is trusted.
 */
@Slf4j
public final class OidcClaimMatcher {

    private static final String SCOPE_SEPARATOR = "\\s+";

    private OidcClaimMatcher() {
        // utility class
    }

    public static boolean matches(final OidcClaimRule rule, final JWTClaimsSet claims) {
        if (Objects.isNull(rule) || Objects.isNull(rule.getClaim())
                || Objects.isNull(rule.getOperator()) || Objects.isNull(rule.getValue())) {
            // the preference validator refuses an incomplete condition; one that reached the
            // preference another way must still deny rather than fail
            log.error("An incomplete claim condition is configured and cannot match");
            return false;
        }
        final Object value = claims.getClaim(rule.getClaim());
        if (Objects.isNull(value)) {
            log.debug("Claim {} is absent, so its condition does not match", rule.getClaim());
            return false;
        }
        switch (rule.getOperator()) {
            case EQUALS:
                return rule.getValue().equals(asText(value));
            case LIKE:
                return matchesGlob(rule.getValue(), asText(value));
            case CONTAINS:
                return containsValue(rule.getValue(), value);
            default:
                log.error("Claim condition on {} uses an operator that is not supported", rule.getClaim());
                return false;
        }
    }

    /**
     * Splits a scope claim into its parts, taking either shape an identity provider may send: one
     * space-separated string, or a list.
     */
    public static Collection<String> readScopes(final Object value) {
        if (value instanceof Collection) {
            return textsOf((Collection<?>) value);
        }
        final String text = asText(value);
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        return Arrays.asList(StringUtils.trim(text).split(SCOPE_SEPARATOR));
    }

    /**
     * Only a list claim can contain a value. A scalar is deliberately not treated as a list of
     * one: the two operators would then overlap, and an administrator who wrote the wrong one
     * would get a match they had not reasoned about.
     */
    private static boolean containsValue(final String expected, final Object value) {
        if (!(value instanceof Collection)) {
            log.debug("A contains condition was configured for a claim that is not a list");
            return false;
        }
        return textsOf((Collection<?>) value).contains(expected);
    }

    private static List<String> textsOf(final Collection<?> values) {
        return values.stream()
                .map(OidcClaimMatcher::asText)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Claims are compared as text, because the same identifier arrives as a JSON string from one
     * provider and as a JSON number from another.
     * <p>
     * A list of exactly one entry reads as that entry. This is not leniency about lists in
     * general — a longer list still has no text form, so {@code EQUALS} means "the claim holds
     * precisely this and nothing else". It is here because the library normalises a single-string
     * {@code aud} into a list of one, and an administrator identifying a caller by its audience
     * will write the obvious condition and would otherwise never match anything.
     */
    private static String asText(final Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Collection) {
            final Collection<?> values = (Collection<?>) value;
            return values.size() == 1 ? asText(values.iterator().next()) : null;
        }
        if (value instanceof Number) {
            return numberAsText((Number) value);
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    /**
     * A whole number is rendered without a fractional part or an exponent, so a claim that arrived
     * as {@code 456789.0} or {@code 4.56789E5} still reads as {@code 456789} and matches a
     * configuration written by hand.
     * <p>
     * The conversion goes through {@link BigDecimal} rather than {@code longValue()}, which
     * saturates: every value at or above 2^63 would otherwise render as the same
     * {@code 9223372036854775807} and a single rule would match them all. An identifier larger
     * than a long is exactly the case a provider sends as a JSON number.
     */
    private static String numberAsText(final Number number) {
        if (number instanceof Long || number instanceof Integer || number instanceof Short
                || number instanceof Byte || number instanceof BigInteger) {
            return number.toString();
        }
        if (number instanceof BigDecimal) {
            return decimalAsText((BigDecimal) number);
        }
        final double asDouble = number.doubleValue();
        if (Double.isInfinite(asDouble) || Double.isNaN(asDouble)) {
            return number.toString();
        }
        return decimalAsText(BigDecimal.valueOf(asDouble));
    }

    private static String decimalAsText(final BigDecimal value) {
        try {
            return value.toBigIntegerExact().toString();
        } catch (ArithmeticException e) {
            return value.stripTrailingZeros().toPlainString();
        }
    }

    /**
     * Matches a glob against the whole claim: {@code *} stands for any run of characters, {@code ?}
     * for exactly one of any character, and everything else is literal.
     * <p>
     * Deliberately not done by translating the glob into a regular expression. That translation is
     * easy to write and produces a flat chain of {@code .*} separated by literals, which Java's
     * engine evaluates by backtracking — a pattern of four or five wildcards against a few hundred
     * characters takes seconds. The exchange endpoint needs no platform token, and a caller
     * controls the claim: on a shared issuer such as GitHub Actions anyone with a repository can
     * present a genuinely signed token whose {@code sub} is as long as they like. So the work is
     * done by two cursors instead, which cannot backtrack beyond the last wildcard and so stays
     * proportional to pattern length times claim length.
     */
    private static boolean matchesGlob(final String pattern, final String value) {
        if (Objects.isNull(value)) {
            return false;
        }
        int patternIndex = 0;
        int valueIndex = 0;
        int lastWildcard = -1;
        int valueAfterWildcard = -1;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length() && isLiteralHit(pattern, patternIndex, value, valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                lastWildcard = patternIndex++;
                valueAfterWildcard = valueIndex;
            } else if (lastWildcard < 0) {
                return false;
            } else {
                // the run the last wildcard swallowed was too short; extend it by one and retry
                patternIndex = lastWildcard + 1;
                valueIndex = ++valueAfterWildcard;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }

    private static boolean isLiteralHit(final String pattern, final int patternIndex,
                                        final String value, final int valueIndex) {
        final char expected = pattern.charAt(patternIndex);
        return expected == '?' || expected == value.charAt(valueIndex);
    }
}
