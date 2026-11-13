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
import com.epam.pipeline.security.oidc.OidcClaimOperator;
import com.epam.pipeline.security.oidc.OidcClaimRule;
import com.epam.pipeline.security.oidc.OidcClient;
import com.epam.pipeline.security.oidc.OidcIssuer;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.LockedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OidcTokenExchangeManagerTest {

    private static final String ISSUER_URL = "https://token.actions.githubusercontent.com";
    private static final String SUBJECT = "repo:org/repo:environment:prod";
    private static final String CLIENT_ID = "github-prod-deploy";
    private static final String SUBJECT_TOKEN = "subject-token";
    private static final String ISSUED_TOKEN = "issued-token";
    private static final String USER_NAME = "GITHUB_CI_DEPLOY";
    private static final String USER_CLAIM = "preferred_username";
    private static final Long USER_ID = 7L;
    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String READER = "READER";
    private static final String GROUP = "BIOINFORMATICS";
    private static final int GLOBAL_CAP = 3600;
    private static final long HALF_HOUR = 1800L;
    private static final long TEN_MINUTES = 600L;
    private static final long FIVE_MINUTES_MS = 300_000L;
    private static final long MILLIS_IN_SECOND = 1000L;

    private OidcTokenVerifier tokenVerifier;
    private OidcClientResolver clientResolver;
    private UserManager userManager;
    private UserAccessService userAccessService;
    private JwtTokenGenerator jwtTokenGenerator;
    private PreferenceManager preferenceManager;
    private OidcTokenExchangeManager manager;

    @Before
    public void setUp() {
        tokenVerifier = Mockito.mock(OidcTokenVerifier.class);
        clientResolver = Mockito.mock(OidcClientResolver.class);
        userManager = Mockito.mock(UserManager.class);
        userAccessService = Mockito.mock(UserAccessService.class);
        jwtTokenGenerator = Mockito.mock(JwtTokenGenerator.class);
        preferenceManager = Mockito.mock(PreferenceManager.class);
        manager = new OidcTokenExchangeManager(tokenVerifier, clientResolver, userManager,
                userAccessService, jwtTokenGenerator, preferenceManager);

        Mockito.when(preferenceManager.getPreference(
                SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_TOKEN_LIFETIME)).thenReturn(GLOBAL_CAP);
        Mockito.when(jwtTokenGenerator.encodeToken(Mockito.any(), Mockito.anyLong()))
                .thenReturn(ISSUED_TOKEN);
        storedUser(ROLE_ADMIN, ROLE_USER, READER);
    }

    @Test
    public void shouldIssueATokenForTheFixedUserOfTheClient() {
        configure(specificUser(), subjectClaims().build());

        final OidcExchangedToken exchanged = manager.exchange(SUBJECT_TOKEN);

        assertThat(exchanged.getToken()).isEqualTo(ISSUED_TOKEN);
        assertThat(issuedClaims().getUserName()).isEqualTo(USER_NAME);
        assertThat(issuedClaims().getUserId()).isEqualTo(String.valueOf(USER_ID));
    }

    @Test
    public void shouldIssueATokenForTheUserNamedByTheConfiguredClaim() {
        configure(requestedUser(), subjectClaims().claim(USER_CLAIM, USER_NAME).build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getUserName()).isEqualTo(USER_NAME);
    }

    /**
     * The same normalisation the SAML login applies, so a provider sending a lower-case address
     * reaches the same platform user.
     */
    @Test
    public void shouldUpperCaseTheUserNameFromTheClaim() {
        configure(requestedUser(), subjectClaims().claim(USER_CLAIM, "github_ci_deploy").build());

        manager.exchange(SUBJECT_TOKEN);

        Mockito.verify(userManager).loadUserByName(USER_NAME);
    }

    @Test
    public void shouldRejectWhenTheConfiguredClaimNamesNoUser() {
        configure(requestedUser(), subjectClaims().build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectWhenNoClientMatches() {
        final OidcIssuer issuer = issuer(specificUser());
        Mockito.when(tokenVerifier.verify(SUBJECT_TOKEN))
                .thenReturn(new OidcVerifiedToken(issuer, subjectClaims().build()));
        Mockito.when(clientResolver.resolve(Mockito.any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);
    }

    @Test
    public void shouldRejectWhenTheUserDoesNotExist() {
        configure(specificUser(), subjectClaims().build());
        Mockito.when(userManager.loadUserByName(USER_NAME)).thenReturn(null);

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    /**
     * Wrapped rather than let through, so that one exception type leaves the manager for every
     * refusal and the endpoint can answer uniformly.
     */
    @Test
    public void shouldRejectWhenTheUserIsBlocked() {
        configure(specificUser(), subjectClaims().build());
        Mockito.doThrow(new LockedException("blocked"))
                .when(userAccessService).validateUserBlockStatus(Mockito.any());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectWhenOneOfTheUsersGroupsIsBlocked() {
        configure(specificUser(), subjectClaims().build());
        Mockito.doThrow(new LockedException("blocked"))
                .when(userAccessService).validateUserGroupsBlockStatus(Mockito.any());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectAClientThatSaysNothingAboutTheUserItActsAs() {
        final OidcIssuer withoutStrategy = issuer(specificUser());
        withoutStrategy.getClients().get(0).setAssumeStrategy(null);
        configure(withoutStrategy, subjectClaims().build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);

        final OidcAssumeStrategy withoutType = specificUser();
        withoutType.setType(null);
        configure(withoutType, subjectClaims().build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);
    }

    /**
     * A drop list that removes nothing leaves an ordinary token. Marking it scoped would pin its
     * authorities for its whole life, so a role granted to the user later would never be picked up.
     */
    @Test
    public void shouldNotMarkATokenScopedWhenShapingRemovedNothing() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setRolesToDrop(Collections.singletonList("ROLE_BILLING_MANAGER"));
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getRoles()).containsExactlyInAnyOrder(ROLE_ADMIN, ROLE_USER, READER);
        assertThat(issuedClaims().isAuthoritiesScoped()).isFalse();
    }

    @Test
    public void shouldReportAMisconfiguredGlobalCapAsItsOwnFault() {
        Mockito.when(preferenceManager.getPreference(
                SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_TOKEN_LIFETIME)).thenReturn(0);
        configure(specificUser(), subjectClaims().build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class)
                .hasMessageContaining(SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_TOKEN_LIFETIME.getKey());
    }

    @Test
    public void shouldRejectAUserOutsideTheAllowedList() {
        final OidcAssumeStrategy strategy = requestedUser();
        strategy.setAllowedUsers(Collections.singletonList("SOMEONE_ELSE"));
        configure(strategy, subjectClaims().claim(USER_CLAIM, USER_NAME).build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);
    }

    @Test
    public void shouldRejectADeniedUserEvenWhenAllowed() {
        final OidcAssumeStrategy strategy = requestedUser();
        strategy.setAllowedUsers(Collections.singletonList(USER_NAME));
        strategy.setDeniedUsers(Collections.singletonList(USER_NAME.toLowerCase()));
        configure(strategy, subjectClaims().claim(USER_CLAIM, USER_NAME).build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);
    }

    @Test
    public void shouldGiveTheUsersWholeAuthoritySetWhenNothingIsShaped() {
        configure(specificUser(), subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getRoles()).containsExactlyInAnyOrder(ROLE_ADMIN, ROLE_USER, READER);
        assertThat(issuedClaims().getGroups()).containsExactly(GROUP);
        assertThat(issuedClaims().isAuthoritiesScoped()).isFalse();
    }

    /**
     * Groups are left alone: they carry the storage permissions the user plainly has, and taking
     * them away would make the token unable to read what its user reads interactively.
     */
    @Test
    public void shouldKeepOnlyTheRolesTheClientAllowsAndLeaveGroupsAlone() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setRolesToKeep(Arrays.asList(ROLE_USER, READER));
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getRoles()).containsExactlyInAnyOrder(ROLE_USER, READER);
        assertThat(issuedClaims().getGroups()).containsExactly(GROUP);
        assertThat(issuedClaims().isAuthoritiesScoped()).isTrue();
    }

    @Test
    public void shouldNotNarrowGroupsEvenWhenNamedInTheRoleLists() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setRolesToDrop(Collections.singletonList(GROUP));
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getGroups()).containsExactly(GROUP);
        assertThat(issuedClaims().isAuthoritiesScoped()).isFalse();
    }

    @Test
    public void shouldDropTheRolesTheClientExcludes() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setRolesToDrop(Collections.singletonList(ROLE_ADMIN));
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getRoles()).containsExactlyInAnyOrder(ROLE_USER, READER);
        assertThat(issuedClaims().isAuthoritiesScoped()).isTrue();
    }

    /**
     * Shaping can only narrow: naming a role the user does not hold does not grant it.
     */
    @Test
    public void shouldNotGrantARoleTheUserDoesNotHold() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setRolesToKeep(Collections.singletonList("ROLE_BILLING_MANAGER"));
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getRoles()).isEmpty();
    }

    @Test
    public void shouldApplyTheGlobalCapWhenNothingElseIsSmaller() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setIgnoreSubjectTokenLifetime(true);
        configure(strategy, subjectClaims().build());

        assertThat(manager.exchange(SUBJECT_TOKEN).getExpiresIn()).isEqualTo(GLOBAL_CAP);
    }

    @Test
    public void shouldApplyTheIssuerLimit() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setIgnoreSubjectTokenLifetime(true);
        final OidcIssuer issuer = issuer(strategy);
        issuer.setMaxTokenLifetime(HALF_HOUR);
        configure(issuer, subjectClaims().build());

        assertThat(manager.exchange(SUBJECT_TOKEN).getExpiresIn()).isEqualTo(HALF_HOUR);
    }

    @Test
    public void shouldApplyTheClientLimitOverTheIssuerOne() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setIgnoreSubjectTokenLifetime(true);
        strategy.setMaxTokenLifetime(TEN_MINUTES);
        final OidcIssuer issuer = issuer(strategy);
        issuer.setMaxTokenLifetime(HALF_HOUR);
        configure(issuer, subjectClaims().build());

        assertThat(manager.exchange(SUBJECT_TOKEN).getExpiresIn()).isEqualTo(TEN_MINUTES);
    }

    /**
     * A GitHub Actions token expires minutes after it is issued, so by default the platform token
     * it is exchanged for cannot outlive it.
     */
    @Test
    public void shouldCapByWhatIsLeftOfTheSubjectToken() {
        configure(specificUser(), subjectClaims().build());

        final long expiresIn = manager.exchange(SUBJECT_TOKEN).getExpiresIn();

        assertThat(expiresIn).isLessThanOrEqualTo(FIVE_MINUTES_MS / MILLIS_IN_SECOND);
        assertThat(expiresIn).isGreaterThan(0L);
    }

    @Test
    public void shouldIgnoreTheSubjectTokenLifetimeWhenTheClientAsksTo() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setIgnoreSubjectTokenLifetime(true);
        configure(strategy, subjectClaims().build());

        assertThat(manager.exchange(SUBJECT_TOKEN).getExpiresIn()).isEqualTo(GLOBAL_CAP);
    }

    @Test
    public void shouldRejectAnAlreadyExpiredSubjectToken() {
        configure(specificUser(), subjectClaims()
                .expirationTime(new Date(System.currentTimeMillis() - FIVE_MINUTES_MS))
                .build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldMarkTheTokenExternalWhenTheClientSaysSo() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setExternal(true);
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().isExternal()).isTrue();
    }

    /**
     * The exchanged token's life is tied to the provider's, and trading it for another would break
     * that tie, so it cannot be traded unless the client asks for it.
     */
    @Test
    public void shouldIssueANonExchangeableTokenByDefault() {
        configure(specificUser(), subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().isExchangeable()).isFalse();
    }

    @Test
    public void shouldIssueAnExchangeableTokenOnlyWhenTheClientAsksFor() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setExchangeable(true);
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().isExchangeable()).isTrue();
    }

    @Test
    public void shouldRecordWhereTheTokenCameFrom() {
        configure(specificUser(), subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getOriginIssuer()).isEqualTo(ISSUER_URL);
        assertThat(issuedClaims().getOriginSubject()).isEqualTo(SUBJECT);
        assertThat(issuedClaims().getOriginClient()).isEqualTo(CLIENT_ID);
        assertThat(issuedClaims().getJwtTokenId()).isNotBlank();
    }

    private JwtTokenClaims issuedClaims() {
        final ArgumentCaptor<JwtTokenClaims> captor = ArgumentCaptor.forClass(JwtTokenClaims.class);
        Mockito.verify(jwtTokenGenerator).encodeToken(captor.capture(), Mockito.anyLong());
        return captor.getValue();
    }

    private void configure(final OidcAssumeStrategy strategy, final JWTClaimsSet claims) {
        configure(issuer(strategy), claims);
    }

    private void configure(final OidcIssuer issuer, final JWTClaimsSet claims) {
        Mockito.when(tokenVerifier.verify(SUBJECT_TOKEN))
                .thenReturn(new OidcVerifiedToken(issuer, claims));
        Mockito.when(clientResolver.resolve(Mockito.any()))
                .thenReturn(Optional.of(issuer.getClients().get(0)));
    }

    private static OidcIssuer issuer(final OidcAssumeStrategy strategy) {
        final OidcClient client = new OidcClient();
        client.setId(CLIENT_ID);
        client.setRules(Collections.singletonList(
                new OidcClaimRule("repository_id", OidcClaimOperator.EQUALS, "456789")));
        client.setAssumeStrategy(strategy);
        final OidcIssuer issuer = new OidcIssuer();
        issuer.setIssuer(ISSUER_URL);
        issuer.setClients(Collections.singletonList(client));
        return issuer;
    }

    private static JWTClaimsSet.Builder subjectClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER_URL)
                .subject(SUBJECT)
                .expirationTime(new Date(System.currentTimeMillis() + FIVE_MINUTES_MS));
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

    private PipelineUser storedUser(final String... roles) {
        final List<Role> stored = Arrays.stream(roles).map(Role::new).collect(Collectors.toList());
        final PipelineUser user = PipelineUser.builder()
                .id(USER_ID)
                .userName(USER_NAME)
                .roles(stored)
                .groups(Collections.singletonList(GROUP))
                .build();
        Mockito.when(userManager.loadUserByName(USER_NAME)).thenReturn(user);
        return user;
    }
}
