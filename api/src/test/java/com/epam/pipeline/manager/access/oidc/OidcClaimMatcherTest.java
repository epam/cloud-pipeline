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

import com.epam.pipeline.security.oidc.OidcClaimOperator;
import com.epam.pipeline.security.oidc.OidcClaimRule;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class OidcClaimMatcherTest {

    private static final String CLAIM = "repository_id";
    private static final String OTHER_CLAIM = "environment";
    private static final String GROUPS_CLAIM = "groups";
    private static final String VALUE = "456789";
    private static final String DEPLOYERS = "deployers";
    private static final String PROD = "prod";
    private static final String SUBJECT_CLAIM = "sub";
    private static final String ACCESS_AS_USER = "access_as_user";
    private static final String USER_READ = "User.Read";
    private static final long REPOSITORY_ID = 456_789L;
    private static final int REPOSITORY_ID_INT = 456_789;
    private static final double REPOSITORY_ID_DOUBLE = 456_789.0d;
    private static final double FRACTIONAL = 1.5d;
    private static final String BEYOND_LONG = "18446744073709551617";
    private static final double BEYOND_LONG_DOUBLE = 1.0e30d;
    private static final double EXPONENTIAL_REPOSITORY_ID = 4.56789e5d;
    private static final long GLOB_TIMEOUT_MS = 5000L;
    private static final int LONG_CLAIM_LENGTH = 600;

    @Test
    public void shouldMatchStringClaimExactly() {
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, VALUE, claims(CLAIM, VALUE))).isTrue();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, VALUE, claims(CLAIM, "456788"))).isFalse();
    }

    @Test
    public void shouldCompareStringClaimCaseSensitively() {
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.EQUALS, PROD, claims(OTHER_CLAIM, "PROD")))
                .isFalse();
    }

    /**
     * The same identifier arrives as a JSON string from one provider and as a JSON number from
     * another, and a configuration written by hand holds text either way.
     */
    @Test
    public void shouldMatchNumericClaimAgainstItsTextForm() {
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, VALUE, claims(CLAIM, REPOSITORY_ID))).isTrue();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, VALUE, claims(CLAIM, REPOSITORY_ID_INT))).isTrue();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, VALUE, claims(CLAIM, REPOSITORY_ID_DOUBLE))).isTrue();
    }

    @Test
    public void shouldMatchFractionalClaimAgainstItsOwnTextForm() {
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, "1.5", claims(CLAIM, FRACTIONAL))).isTrue();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, "1", claims(CLAIM, FRACTIONAL))).isFalse();
    }

    @Test
    public void shouldMatchBooleanClaim() {
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, "true", claims(CLAIM, Boolean.TRUE))).isTrue();
    }

    @Test
    public void shouldNotMatchAnyOperatorWhenTheClaimIsAbsent() {
        for (final OidcClaimOperator operator : OidcClaimOperator.values()) {
            assertThat(matches(CLAIM, operator, VALUE, claims(OTHER_CLAIM, PROD)))
                    .as(operator.name())
                    .isFalse();
        }
    }

    @Test
    public void shouldFindValueInListClaim() {
        final JWTClaimsSet claims = claims(GROUPS_CLAIM, Arrays.asList(DEPLOYERS, "readers"));

        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.CONTAINS, DEPLOYERS, claims)).isTrue();
        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.CONTAINS, "admins", claims)).isFalse();
    }

    @Test
    public void shouldFindNumericValueInListClaim() {
        final JWTClaimsSet claims = claims(GROUPS_CLAIM, Collections.singletonList(REPOSITORY_ID));

        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.CONTAINS, VALUE, claims)).isTrue();
    }

    /**
     * A scalar is not treated as a list of one, or the two operators would overlap and a
     * mistakenly chosen one would still produce a match.
     */
    @Test
    public void shouldNotTreatScalarClaimAsAListForContains() {
        assertThat(matches(CLAIM, OidcClaimOperator.CONTAINS, VALUE, claims(CLAIM, VALUE))).isFalse();
    }

    /**
     * The library turns a single-string {@code aud} into a list of one, so the obvious condition on
     * an audience has to work.
     */
    @Test
    public void shouldMatchListClaimOfOneEntryWithEqualsAndLike() {
        final JWTClaimsSet claims = claims(GROUPS_CLAIM, Collections.singletonList(DEPLOYERS));

        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.EQUALS, DEPLOYERS, claims)).isTrue();
        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.LIKE, "deploy*", claims)).isTrue();
    }

    @Test
    public void shouldNotMatchLongerListClaimWithEqualsOrLike() {
        final JWTClaimsSet claims = claims(GROUPS_CLAIM, Arrays.asList(DEPLOYERS, "readers"));

        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.EQUALS, DEPLOYERS, claims)).isFalse();
        assertThat(matches(GROUPS_CLAIM, OidcClaimOperator.LIKE, "deploy*", claims)).isFalse();
    }

    @Test
    public void shouldNotMatchClaimThatIsAnObject() {
        final JWTClaimsSet claims = claims(CLAIM, Collections.singletonMap("nested", VALUE));

        for (final OidcClaimOperator operator : OidcClaimOperator.values()) {
            assertThat(matches(CLAIM, operator, VALUE, claims)).as(operator.name()).isFalse();
        }
    }

    @Test
    public void shouldNotMatchWhenTheConditionIsIncomplete() {
        final JWTClaimsSet claims = claims(CLAIM, VALUE);

        assertThat(OidcClaimMatcher.matches(null, claims)).isFalse();
        assertThat(OidcClaimMatcher.matches(new OidcClaimRule(null, OidcClaimOperator.EQUALS, VALUE), claims))
                .isFalse();
        assertThat(OidcClaimMatcher.matches(new OidcClaimRule(CLAIM, null, VALUE), claims)).isFalse();
        assertThat(OidcClaimMatcher.matches(new OidcClaimRule(CLAIM, OidcClaimOperator.EQUALS, null), claims))
                .isFalse();
    }

    /**
     * Beyond a long, a provider's numeric identifier must still read as itself: rendering it
     * through a long would saturate every such value onto one string that a single rule matched.
     */
    @Test
    public void shouldRenderNumbersLargerThanALongWithoutSaturating() {
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, BEYOND_LONG,
                claims(CLAIM, new BigInteger(BEYOND_LONG)))).isTrue();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, BEYOND_LONG,
                claims(CLAIM, new BigDecimal(BEYOND_LONG)))).isTrue();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, "1",
                claims(CLAIM, new BigDecimal(BEYOND_LONG)))).isFalse();
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, Long.toString(Long.MAX_VALUE),
                claims(CLAIM, BEYOND_LONG_DOUBLE))).isFalse();
    }

    @Test
    public void shouldRenderExponentialNotationAsAWholeNumber() {
        assertThat(matches(CLAIM, OidcClaimOperator.EQUALS, VALUE, claims(CLAIM, EXPONENTIAL_REPOSITORY_ID))).isTrue();
    }

    /**
     * A translation of this glob into a regular expression backtracks for seconds on a few
     * wildcards, and the claim is attacker-controlled on a shared issuer.
     */
    @Test(timeout = GLOB_TIMEOUT_MS)
    public void shouldMatchGlobsWithManyWildcardsWithoutBacktracking() {
        final StringBuilder value = new StringBuilder();
        for (int index = 0; index < LONG_CLAIM_LENGTH; index++) {
            value.append('a');
        }
        final JWTClaimsSet claims = claims(SUBJECT_CLAIM, value.toString());

        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "*a*a*a*a*a*b", claims)).isFalse();
        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "a*/*/*/*/*:end", claims)).isFalse();
        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "*a*a*a*a*a", claims)).isTrue();
    }

    @Test
    public void shouldMatchConsecutiveWildcards() {
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "**d", claims(OTHER_CLAIM, PROD))).isTrue();
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "p**", claims(OTHER_CLAIM, PROD))).isTrue();
    }

    @Test
    public void shouldMatchGlobPatterns() {
        final JWTClaimsSet claims = claims(SUBJECT_CLAIM, "repo:org/repo:ref:refs/heads/main");

        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "repo:org/*", claims)).isTrue();
        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "*refs/heads/main", claims)).isTrue();
        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "repo:*:ref:*", claims)).isTrue();
        assertThat(matches(SUBJECT_CLAIM, OidcClaimOperator.LIKE, "repo:other/*", claims)).isFalse();
    }

    @Test
    public void shouldMatchSingleCharacterWildcard() {
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "prod?", claims(OTHER_CLAIM, "prod1")))
                .isTrue();
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "prod?", claims(OTHER_CLAIM, PROD)))
                .isFalse();
    }

    @Test
    public void shouldRequireTheWholeClaimToMatchThePattern() {
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "pro", claims(OTHER_CLAIM, PROD)))
                .isFalse();
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "rod", claims(OTHER_CLAIM, PROD)))
                .isFalse();
    }

    /**
     * A glob is not a regular expression, so a character that is special in one has to be matched
     * literally.
     */
    @Test
    public void shouldTreatRegularExpressionCharactersAsLiterals() {
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "a.c", claims(OTHER_CLAIM, "abc")))
                .isFalse();
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "a.c", claims(OTHER_CLAIM, "a.c")))
                .isTrue();
        assertThat(matches(OTHER_CLAIM, OidcClaimOperator.LIKE, "a+*", claims(OTHER_CLAIM, "a+b")))
                .isTrue();
    }

    @Test
    public void shouldReadScopesFromSpaceSeparatedString() {
        assertThat(OidcClaimMatcher.readScopes("access_as_user User.Read"))
                .containsExactly(ACCESS_AS_USER, USER_READ);
        assertThat(OidcClaimMatcher.readScopes("  access_as_user   User.Read  "))
                .containsExactly(ACCESS_AS_USER, USER_READ);
    }

    @Test
    public void shouldReadScopesFromList() {
        assertThat(OidcClaimMatcher.readScopes(Arrays.asList(ACCESS_AS_USER, USER_READ)))
                .containsExactly(ACCESS_AS_USER, USER_READ);
    }

    @Test
    public void shouldReadNoScopesFromAbsentOrBlankClaim() {
        assertThat(OidcClaimMatcher.readScopes(null)).isEmpty();
        assertThat(OidcClaimMatcher.readScopes("   ")).isEmpty();
        assertThat(OidcClaimMatcher.readScopes(Collections.emptyList())).isEmpty();
    }

    private static boolean matches(final String claim, final OidcClaimOperator operator,
                                   final String value, final JWTClaimsSet claims) {
        return OidcClaimMatcher.matches(new OidcClaimRule(claim, operator, value), claims);
    }

    private static JWTClaimsSet claims(final String name, final Object value) {
        return new JWTClaimsSet.Builder().claim(name, value).build();
    }
}
