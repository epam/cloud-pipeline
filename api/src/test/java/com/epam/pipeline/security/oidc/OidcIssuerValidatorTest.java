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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class OidcIssuerValidatorTest {

    private static final String ISSUER_URL = "https://token.actions.githubusercontent.com";
    private static final String OTHER_ISSUER_URL = "https://login.microsoftonline.com/tenant/v2.0";
    private static final String AUDIENCE = "https://cloud-pipeline.local/pipeline/restapi";
    private static final String RS256 = "RS256";
    private static final String CLIENT_ID = "github-prod-deploy";
    private static final String CLAIM = "repository_id";
    private static final String CLAIM_VALUE = "456789";
    private static final String USER_NAME = "GITHUB_CI_DEPLOY";
    private static final String USER_CLAIM = "preferred_username";
    private static final String ROLE_USER = "ROLE_USER";
    private static final String JWKS_URI = "https://provider.local/keys";
    private static final String KEYS_FIELD = "keys";
    private static final String BLANK = "  ";

    @Test
    public void shouldAcceptMissingConfigurationBecauseTheExchangeIsThenSimplyNotSetUp() {
        assertThat(OidcIssuerValidator.isValid(null)).isTrue();
        assertThat(OidcIssuerValidator.isValid(Collections.emptyList())).isTrue();
    }

    @Test
    public void shouldAcceptMinimalConfiguration() {
        assertThat(isValid(issuer())).isTrue();
    }

    @Test
    public void shouldRejectBlankIssuer() {
        final OidcIssuer issuer = issuer();
        issuer.setIssuer(BLANK);
        assertThat(isValid(issuer)).isFalse();
    }

    @Test
    public void shouldRejectIssuerThatIsNotAnHttpsUrl() {
        for (final String url : Arrays.asList("http://token.actions.githubusercontent.com",
                                              "token.actions.githubusercontent.com",
                                              "not a url")) {
            final OidcIssuer issuer = issuer();
            issuer.setIssuer(url);
            assertThat(isValid(issuer)).as(url).isFalse();
        }
    }

    @Test
    public void shouldRejectKeysUriThatIsNotAnHttpsUrl() {
        for (final String url : Arrays.asList("http://provider.local/keys", "provider.local/keys", BLANK)) {
            final OidcIssuer issuer = issuer();
            issuer.setJwksUri(url);
            assertThat(isValid(issuer)).as(url).isFalse();
        }
    }

    @Test
    public void shouldRejectBlankKeysUriAlongsideInlineKeysBecauseItStillPicksTheKeySource() {
        final OidcIssuer issuer = issuer();
        issuer.setJwks(jwks());
        issuer.setJwksUri(BLANK);
        assertThat(isValid(issuer)).isFalse();
    }

    @Test
    public void shouldRejectDuplicateIssuers() {
        assertThat(OidcIssuerValidator.isValid(Arrays.asList(issuer(), issuer()))).isFalse();
    }

    @Test
    public void shouldAcceptDistinctIssuers() {
        final OidcIssuer other = issuer();
        other.setIssuer(OTHER_ISSUER_URL);
        assertThat(OidcIssuerValidator.isValid(Arrays.asList(issuer(), other))).isTrue();
    }

    @Test
    public void shouldRejectMissingOrEmptyAudiences() {
        final OidcIssuer withoutAudiences = issuer();
        withoutAudiences.setAudiences(null);
        assertThat(isValid(withoutAudiences)).isFalse();

        final OidcIssuer withEmptyAudiences = issuer();
        withEmptyAudiences.setAudiences(new ArrayList<>());
        assertThat(isValid(withEmptyAudiences)).isFalse();
    }

    @Test
    public void shouldAcceptAbsentAlgorithmsBecauseDefaultsApply() {
        final OidcIssuer issuer = issuer();
        issuer.setAlgorithms(null);
        assertThat(isValid(issuer)).isTrue();
    }

    @Test
    public void shouldRejectEmptyAlgorithmsBecauseEmptyNeverMeansEverything() {
        final OidcIssuer issuer = issuer();
        issuer.setAlgorithms(new ArrayList<>());
        assertThat(isValid(issuer)).isFalse();
    }

    @Test
    public void shouldRejectUnsignedAndHmacAlgorithms() {
        for (final String algorithm : Arrays.asList("none", "NONE", "HS256", "hs512")) {
            final OidcIssuer issuer = issuer();
            issuer.setAlgorithms(Arrays.asList(RS256, algorithm));
            assertThat(isValid(issuer)).as(algorithm).isFalse();
        }
    }

    @Test
    public void shouldRejectUnsupportedAlgorithmBecauseAnAllowListCatchesTypos() {
        for (final String algorithm : Arrays.asList("RS255", "rs256", "RSA256", "A128KW")) {
            final OidcIssuer issuer = issuer();
            issuer.setAlgorithms(Collections.singletonList(algorithm));
            assertThat(isValid(issuer)).as(algorithm).isFalse();
        }
    }

    @Test
    public void shouldAcceptEverySupportedAlgorithm() {
        for (final String algorithm : Arrays.asList("RS256", "RS384", "RS512", "ES256", "ES384",
                                                    "ES512", "PS256", "PS384", "PS512", "EdDSA")) {
            final OidcIssuer issuer = issuer();
            issuer.setAlgorithms(Collections.singletonList(algorithm));
            assertThat(isValid(issuer)).as(algorithm).isTrue();
        }
    }

    @Test
    public void shouldRejectBothInlineKeysAndKeysUri() {
        final OidcIssuer issuer = issuer();
        issuer.setJwks(jwks());
        issuer.setJwksUri(JWKS_URI);
        assertThat(isValid(issuer)).isFalse();
    }

    @Test
    public void shouldAcceptEitherKeySourceAlone() {
        final OidcIssuer withInlineKeys = issuer();
        withInlineKeys.setJwks(jwks());
        assertThat(isValid(withInlineKeys)).isTrue();

        final OidcIssuer withKeysUri = issuer();
        withKeysUri.setJwksUri(JWKS_URI);
        assertThat(isValid(withKeysUri)).isTrue();
    }

    @Test
    public void shouldRejectInlineKeySetWithoutKeys() {
        final OidcIssuer withoutKeysField = issuer();
        withoutKeysField.setJwks(new HashMap<>());
        assertThat(isValid(withoutKeysField)).isFalse();

        final OidcIssuer withEmptyKeys = issuer();
        final Map<String, Object> empty = new HashMap<>();
        empty.put(KEYS_FIELD, new ArrayList<>());
        withEmptyKeys.setJwks(empty);
        assertThat(isValid(withEmptyKeys)).isFalse();
    }

    @Test
    public void shouldRejectInlineKeySetWhoseEntriesAreNotKeys() {
        final OidcIssuer notObjects = issuer();
        final Map<String, Object> strings = new HashMap<>();
        strings.put(KEYS_FIELD, Collections.singletonList("not-a-key"));
        notObjects.setJwks(strings);
        assertThat(isValid(notObjects)).isFalse();

        final OidcIssuer withoutKeyType = issuer();
        final Map<String, Object> untyped = new HashMap<>();
        untyped.put(KEYS_FIELD, Collections.singletonList(new HashMap<String, Object>()));
        withoutKeyType.setJwks(untyped);
        assertThat(isValid(withoutKeyType)).isFalse();
    }

    @Test
    public void shouldRejectMissingOrEmptyClients() {
        final OidcIssuer withoutClients = issuer();
        withoutClients.setClients(null);
        assertThat(isValid(withoutClients)).isFalse();

        final OidcIssuer withEmptyClients = issuer();
        withEmptyClients.setClients(new ArrayList<>());
        assertThat(isValid(withEmptyClients)).isFalse();
    }

    @Test
    public void shouldRejectBlankOrDuplicateClientId() {
        final OidcIssuer blankId = issuer();
        blankId.getClients().get(0).setId(BLANK);
        assertThat(isValid(blankId)).isFalse();

        final OidcIssuer duplicateIds = issuer();
        duplicateIds.setClients(Arrays.asList(client(), client()));
        assertThat(isValid(duplicateIds)).isFalse();
    }

    @Test
    public void shouldRejectMissingOrEmptyRulesBecauseTheyWouldMatchEveryToken() {
        final OidcIssuer withoutRules = issuer();
        withoutRules.getClients().get(0).setRules(null);
        assertThat(isValid(withoutRules)).isFalse();

        final OidcIssuer withEmptyRules = issuer();
        withEmptyRules.getClients().get(0).setRules(new ArrayList<>());
        assertThat(isValid(withEmptyRules)).isFalse();
    }

    @Test
    public void shouldRejectRulesWithoutAnExactCondition() {
        final OidcIssuer issuer = issuer();
        issuer.getClients().get(0).setRules(Collections.singletonList(
                rule(CLAIM, OidcClaimOperator.LIKE, "4567*")));
        assertThat(isValid(issuer)).isFalse();
    }

    @Test
    public void shouldAcceptPatternsAlongsideAnExactCondition() {
        final OidcIssuer issuer = issuer();
        issuer.getClients().get(0).setRules(Arrays.asList(
                rule(CLAIM, OidcClaimOperator.EQUALS, CLAIM_VALUE),
                rule("environment", OidcClaimOperator.LIKE, "prod*"),
                rule("groups", OidcClaimOperator.CONTAINS, "deployers")));
        assertThat(isValid(issuer)).isTrue();
    }

    @Test
    public void shouldRejectIncompleteRule() {
        assertThat(isValid(withRule(rule(BLANK, OidcClaimOperator.EQUALS, CLAIM_VALUE)))).isFalse();
        assertThat(isValid(withRule(rule(CLAIM, null, CLAIM_VALUE)))).isFalse();
        assertThat(isValid(withRule(rule(CLAIM, OidcClaimOperator.EQUALS, BLANK)))).isFalse();
    }

    @Test
    public void shouldRejectPatternOfWildcardsOnlyBecauseItMatchesEverything() {
        for (final String pattern : Arrays.asList("*", "**", "?", "*?*", "* *", " *")) {
            final OidcIssuer issuer = issuer();
            issuer.getClients().get(0).setRules(Arrays.asList(
                    rule(CLAIM, OidcClaimOperator.EQUALS, CLAIM_VALUE),
                    rule("repository", OidcClaimOperator.LIKE, pattern)));
            assertThat(isValid(issuer)).as(pattern).isFalse();
        }
    }

    @Test
    public void shouldRejectEmptyRequiredScopesButAcceptAbsentOnes() {
        final OidcIssuer withEmptyScopes = issuer();
        withEmptyScopes.getClients().get(0).setRequiredScopes(new ArrayList<>());
        assertThat(isValid(withEmptyScopes)).isFalse();

        final OidcIssuer withoutScopes = issuer();
        withoutScopes.getClients().get(0).setRequiredScopes(null);
        assertThat(isValid(withoutScopes)).isTrue();
    }

    @Test
    public void shouldRejectScopeHoldingASpaceBecauseItCanMatchNeitherClaimShape() {
        final OidcIssuer issuer = issuer();
        issuer.getClients().get(0).setRequiredScopes(Collections.singletonList("read write"));
        assertThat(isValid(issuer)).isFalse();
    }

    @Test
    public void shouldRejectMissingStrategyOrType() {
        final OidcIssuer withoutStrategy = issuer();
        withoutStrategy.getClients().get(0).setAssumeStrategy(null);
        assertThat(isValid(withoutStrategy)).isFalse();

        final OidcIssuer withoutType = issuer();
        withoutType.getClients().get(0).getAssumeStrategy().setType(null);
        assertThat(isValid(withoutType)).isFalse();
    }

    @Test
    public void shouldRejectSpecificUserStrategyWithoutItsUser() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setUser(null);
        assertThat(isValid(withStrategy(strategy))).isFalse();
    }

    @Test
    public void shouldRejectSpecificUserStrategyCarryingRequestedUserFields() {
        final OidcAssumeStrategy withUserClaim = specificUser();
        withUserClaim.setUserClaim(USER_CLAIM);
        assertThat(isValid(withStrategy(withUserClaim))).isFalse();

        final OidcAssumeStrategy withAllowedUsers = specificUser();
        withAllowedUsers.setAllowedUsers(Collections.singletonList(USER_NAME));
        assertThat(isValid(withStrategy(withAllowedUsers))).isFalse();

        final OidcAssumeStrategy withDeniedUsers = specificUser();
        withDeniedUsers.setDeniedUsers(Collections.singletonList(USER_NAME));
        assertThat(isValid(withStrategy(withDeniedUsers))).isFalse();
    }

    @Test
    public void shouldAcceptRequestedUserStrategy() {
        assertThat(isValid(withStrategy(requestedUser()))).isTrue();
    }

    @Test
    public void shouldRejectRequestedUserStrategyWithoutItsClaim() {
        final OidcAssumeStrategy strategy = requestedUser();
        strategy.setUserClaim(null);
        assertThat(isValid(withStrategy(strategy))).isFalse();
    }

    @Test
    public void shouldRejectRequestedUserStrategyNamingAFixedUser() {
        final OidcAssumeStrategy strategy = requestedUser();
        strategy.setUser(USER_NAME);
        assertThat(isValid(withStrategy(strategy))).isFalse();
    }

    @Test
    public void shouldRejectEmptyUserListsButAcceptAbsentOnes() {
        final OidcAssumeStrategy withEmptyAllowed = requestedUser();
        withEmptyAllowed.setAllowedUsers(new ArrayList<>());
        assertThat(isValid(withStrategy(withEmptyAllowed))).isFalse();

        final OidcAssumeStrategy withEmptyDenied = requestedUser();
        withEmptyDenied.setDeniedUsers(new ArrayList<>());
        assertThat(isValid(withStrategy(withEmptyDenied))).isFalse();

        final OidcAssumeStrategy withPopulatedLists = requestedUser();
        withPopulatedLists.setAllowedUsers(Collections.singletonList(USER_NAME));
        withPopulatedLists.setDeniedUsers(Collections.singletonList("SOMEONE_ELSE"));
        assertThat(isValid(withStrategy(withPopulatedLists))).isTrue();
    }

    @Test
    public void shouldRejectAnAuthorityThatIsBothKeptAndDropped() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setAuthoritiesToKeep(Arrays.asList(ROLE_USER, "READER"));
        strategy.setAuthoritiesToDrop(Collections.singletonList(ROLE_USER));
        assertThat(isValid(withStrategy(strategy))).isFalse();
    }

    @Test
    public void shouldAcceptDisjointKeepAndDropLists() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setAuthoritiesToKeep(Collections.singletonList(ROLE_USER));
        strategy.setAuthoritiesToDrop(Collections.singletonList("ROLE_ADMIN"));
        assertThat(isValid(withStrategy(strategy))).isTrue();
    }

    @Test
    public void shouldRejectEmptyAuthorityLists() {
        final OidcAssumeStrategy withEmptyKeep = specificUser();
        withEmptyKeep.setAuthoritiesToKeep(new ArrayList<>());
        assertThat(isValid(withStrategy(withEmptyKeep))).isFalse();

        final OidcAssumeStrategy withEmptyDrop = specificUser();
        withEmptyDrop.setAuthoritiesToDrop(new ArrayList<>());
        assertThat(isValid(withStrategy(withEmptyDrop))).isFalse();
    }

    @Test
    public void shouldRejectNonPositiveLifetimes() {
        final OidcIssuer issuerLifetime = issuer();
        issuerLifetime.setMaxTokenLifetime(0L);
        assertThat(isValid(issuerLifetime)).isFalse();

        final OidcAssumeStrategy strategy = specificUser();
        strategy.setMaxTokenLifetime(-1L);
        assertThat(isValid(withStrategy(strategy))).isFalse();
    }

    private static boolean isValid(final OidcIssuer issuer) {
        return OidcIssuerValidator.isValid(Collections.singletonList(issuer));
    }

    private static OidcIssuer withRule(final OidcClaimRule rule) {
        final OidcIssuer issuer = issuer();
        issuer.getClients().get(0).setRules(Collections.singletonList(rule));
        return issuer;
    }

    private static OidcIssuer withStrategy(final OidcAssumeStrategy strategy) {
        final OidcIssuer issuer = issuer();
        issuer.getClients().get(0).setAssumeStrategy(strategy);
        return issuer;
    }

    private static OidcIssuer issuer() {
        final OidcIssuer issuer = new OidcIssuer();
        issuer.setIssuer(ISSUER_URL);
        issuer.setAudiences(Collections.singletonList(AUDIENCE));
        issuer.setAlgorithms(Collections.singletonList(RS256));
        issuer.setClients(new ArrayList<>(Collections.singletonList(client())));
        return issuer;
    }

    private static OidcClient client() {
        final OidcClient client = new OidcClient();
        client.setId(CLIENT_ID);
        client.setRules(Collections.singletonList(rule(CLAIM, OidcClaimOperator.EQUALS, CLAIM_VALUE)));
        client.setAssumeStrategy(specificUser());
        return client;
    }

    private static OidcClaimRule rule(final String claim, final OidcClaimOperator operator, final String value) {
        return new OidcClaimRule(claim, operator, value);
    }

    private static OidcAssumeStrategy specificUser() {
        final OidcAssumeStrategy strategy = new OidcAssumeStrategy();
        strategy.setType(OidcAssumeStrategyType.SPECIFIC_USER);
        strategy.setUser(USER_NAME);
        return strategy;
    }

    private static OidcAssumeStrategy requestedUser() {
        final OidcAssumeStrategy strategy = new OidcAssumeStrategy();
        strategy.setType(OidcAssumeStrategyType.REQUESTED_USER);
        strategy.setUserClaim(USER_CLAIM);
        return strategy;
    }

    private static Map<String, Object> jwks() {
        final Map<String, Object> key = new HashMap<>();
        key.put("kty", "RSA");
        key.put("kid", "test-key");
        final Map<String, Object> keySet = new HashMap<>();
        keySet.put(KEYS_FIELD, Collections.singletonList(key));
        return keySet;
    }
}
