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

import com.epam.pipeline.security.oidc.OidcAssumeStrategy;
import com.epam.pipeline.security.oidc.OidcAssumeStrategyType;
import com.epam.pipeline.security.oidc.OidcClaimOperator;
import com.epam.pipeline.security.oidc.OidcClaimRule;
import com.epam.pipeline.security.oidc.OidcClient;
import com.epam.pipeline.security.oidc.OidcIssuer;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class OidcClientResolverTest {

    private static final String ISSUER_URL = "https://token.actions.githubusercontent.com";
    private static final String REPOSITORY_CLAIM = "repository_id";
    private static final String ENVIRONMENT_CLAIM = "environment";
    private static final String SCOPE_CLAIM = "scp";
    private static final String STANDARD_SCOPE_CLAIM = "scope";
    private static final String REPOSITORY = "456789";
    private static final String PROD = "prod";
    private static final String ACCESS_AS_USER = "access_as_user";
    private static final String PROD_CLIENT = "prod-deploy";
    private static final String ANY_ENVIRONMENT_CLIENT = "any-environment";

    private OidcClientResolver resolver;

    @Before
    public void setUp() {
        resolver = new OidcClientResolver();
    }

    @Test
    public void shouldMatchClientWhoseEveryConditionPasses() {
        final OidcClient client = client(PROD_CLIENT,
                rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY),
                rule(ENVIRONMENT_CLAIM, OidcClaimOperator.EQUALS, PROD));

        final Optional<OidcClient> matched = resolve(claims(REPOSITORY, PROD), client);

        assertThat(matched.isPresent()).isTrue();
        assertThat(matched.get().getId()).isEqualTo(PROD_CLIENT);
    }

    @Test
    public void shouldRequireEveryConditionToPass() {
        final OidcClient client = client(PROD_CLIENT,
                rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY),
                rule(ENVIRONMENT_CLAIM, OidcClaimOperator.EQUALS, PROD));

        assertThat(resolve(claims(REPOSITORY, "staging"), client).isPresent()).isFalse();
        assertThat(resolve(claims("111", PROD), client).isPresent()).isFalse();
    }

    @Test
    public void shouldReturnNothingWhenNoClientMatches() {
        final OidcClient client = client(PROD_CLIENT, rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, "111"));

        assertThat(resolve(claims(REPOSITORY, PROD), client).isPresent()).isFalse();
    }

    @Test
    public void shouldTakeTheFirstMatchingClient() {
        final OidcClient narrow = client(PROD_CLIENT,
                rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY),
                rule(ENVIRONMENT_CLAIM, OidcClaimOperator.EQUALS, PROD));
        final OidcClient wide = client(ANY_ENVIRONMENT_CLIENT,
                rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY));

        final Optional<OidcClient> matched = resolve(claims(REPOSITORY, PROD), narrow, wide);

        assertThat(matched.isPresent()).isTrue();
        assertThat(matched.get().getId()).isEqualTo(PROD_CLIENT);
    }

    /**
     * The order is the administrator's to get right, so the behaviour is pinned: a wider entry
     * placed first hides a narrower one below it.
     */
    @Test
    public void shouldLetAWiderClientPlacedFirstShadowANarrowerOne() {
        final OidcClient wide = client(ANY_ENVIRONMENT_CLIENT,
                rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY));
        final OidcClient narrow = client(PROD_CLIENT,
                rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY),
                rule(ENVIRONMENT_CLAIM, OidcClaimOperator.EQUALS, PROD));

        final Optional<OidcClient> matched = resolve(claims(REPOSITORY, PROD), wide, narrow);

        assertThat(matched.isPresent()).isTrue();
        assertThat(matched.get().getId()).isEqualTo(ANY_ENVIRONMENT_CLIENT);
    }

    @Test
    public void shouldIgnoreClientWithoutConditions() {
        final OidcClient withoutRules = client(PROD_CLIENT);
        withoutRules.setRules(null);

        assertThat(resolve(claims(REPOSITORY, PROD), withoutRules).isPresent()).isFalse();
    }

    @Test
    public void shouldMatchRequiredScopeFromSpaceSeparatedClaim() {
        final OidcClient client = scopedClient();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(SCOPE_CLAIM, ACCESS_AS_USER + " User.Read")
                .build();

        assertThat(resolve(claims, client).isPresent()).isTrue();
    }

    @Test
    public void shouldMatchRequiredScopeFromListClaim() {
        final OidcClient client = scopedClient();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(SCOPE_CLAIM, Arrays.asList(ACCESS_AS_USER, "User.Read"))
                .build();

        assertThat(resolve(claims, client).isPresent()).isTrue();
    }

    @Test
    public void shouldMatchRequiredScopeFromTheStandardScopeClaim() {
        final OidcClient client = scopedClient();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(STANDARD_SCOPE_CLAIM, ACCESS_AS_USER)
                .build();

        assertThat(resolve(claims, client).isPresent()).isTrue();
    }

    @Test
    public void shouldFallBackToTheStandardScopeClaimWhenTheProviderOneIsEmpty() {
        final OidcClient client = scopedClient();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(SCOPE_CLAIM, "")
                .claim(STANDARD_SCOPE_CLAIM, ACCESS_AS_USER)
                .build();

        assertThat(resolve(claims, client).isPresent()).isTrue();
    }

    @Test
    public void shouldNotMatchWhenARequiredScopeIsMissing() {
        final OidcClient client = scopedClient();
        final JWTClaimsSet withOtherScope = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(SCOPE_CLAIM, "User.Read")
                .build();
        final JWTClaimsSet withoutScopes = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .build();

        assertThat(resolve(withOtherScope, client).isPresent()).isFalse();
        assertThat(resolve(withoutScopes, client).isPresent()).isFalse();
    }

    /**
     * A scope is a whole token, so a longer scope that merely starts with the required one grants
     * nothing.
     */
    @Test
    public void shouldNotAcceptAScopeThatOnlyStartsWithTheRequiredOne() {
        final OidcClient client = scopedClient();
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(SCOPE_CLAIM, ACCESS_AS_USER + "_admin")
                .build();

        assertThat(resolve(claims, client).isPresent()).isFalse();
    }

    @Test
    public void shouldRouteToTheClientWhoseScopeTheTokenCarries() {
        final OidcClient readOnly = scopedClient();
        readOnly.setId("read-only");
        readOnly.setRequiredScopes(Collections.singletonList("read_only"));
        final OidcClient full = scopedClient();

        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, REPOSITORY)
                .claim(SCOPE_CLAIM, ACCESS_AS_USER)
                .build();

        final Optional<OidcClient> matched = resolve(claims, readOnly, full);

        assertThat(matched.isPresent()).isTrue();
        assertThat(matched.get().getId()).isEqualTo(PROD_CLIENT);
    }

    private Optional<OidcClient> resolve(final JWTClaimsSet claims, final OidcClient... clients) {
        final OidcIssuer issuer = new OidcIssuer();
        issuer.setIssuer(ISSUER_URL);
        final List<OidcClient> configured = new ArrayList<>(Arrays.asList(clients));
        issuer.setClients(configured);
        return resolver.resolve(new OidcVerifiedToken(issuer, claims));
    }

    private static OidcClient scopedClient() {
        final OidcClient client = client(PROD_CLIENT, rule(REPOSITORY_CLAIM, OidcClaimOperator.EQUALS, REPOSITORY));
        client.setRequiredScopes(Collections.singletonList(ACCESS_AS_USER));
        return client;
    }

    private static OidcClient client(final String id, final OidcClaimRule... rules) {
        final OidcClient client = new OidcClient();
        client.setId(id);
        client.setRules(new ArrayList<>(Arrays.asList(rules)));
        final OidcAssumeStrategy strategy = new OidcAssumeStrategy();
        strategy.setType(OidcAssumeStrategyType.SPECIFIC_USER);
        strategy.setUser("GITHUB_CI_DEPLOY");
        client.setAssumeStrategy(strategy);
        return client;
    }

    private static OidcClaimRule rule(final String claim, final OidcClaimOperator operator, final String value) {
        return new OidcClaimRule(claim, operator, value);
    }

    private static JWTClaimsSet claims(final String repository, final String environment) {
        return new JWTClaimsSet.Builder()
                .claim(REPOSITORY_CLAIM, repository)
                .claim(ENVIRONMENT_CLAIM, environment)
                .build();
    }
}
