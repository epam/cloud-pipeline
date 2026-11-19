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

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.metadata.MetadataManager;
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
import com.epam.pipeline.security.oidc.OidcUserMapping;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private static final String OWNER_CLAIM = "repository_owner";
    private static final String OWNER_VALUE = "octo-org";
    private static final String OWNER_KEY = "github_owner";
    private static final Long OTHER_USER_ID = 11L;
    private static final long NUMERIC_CLAIM = 456789L;
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
    private MetadataManager metadataManager;
    private UserAccessService userAccessService;
    private JwtTokenGenerator jwtTokenGenerator;
    private PreferenceManager preferenceManager;
    private OidcTokenExchangeManager manager;

    @BeforeEach
    public void setUp() {
        tokenVerifier = Mockito.mock(OidcTokenVerifier.class);
        clientResolver = Mockito.mock(OidcClientResolver.class);
        userManager = Mockito.mock(UserManager.class);
        metadataManager = Mockito.mock(MetadataManager.class);
        userAccessService = Mockito.mock(UserAccessService.class);
        jwtTokenGenerator = Mockito.mock(JwtTokenGenerator.class);
        preferenceManager = Mockito.mock(PreferenceManager.class);
        manager = new OidcTokenExchangeManager(tokenVerifier, clientResolver, userManager,
                metadataManager, userAccessService, jwtTokenGenerator, preferenceManager);

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
    public void shouldGiveTheUsersWholeRoleSetWhenNothingIsShaped() {
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
     * The exchanged token's life is tied to the provider's, and renewing it would break that tie,
     * so it cannot be renewed unless the client asks for it.
     */
    @Test
    public void shouldIssueANonRenewableTokenByDefault() {
        configure(specificUser(), subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().isNonRenewable()).isTrue();
    }

    @Test
    public void shouldIssueARenewableTokenOnlyWhenTheClientAsksFor() {
        final OidcAssumeStrategy strategy = specificUser();
        strategy.setRenewable(true);
        configure(strategy, subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().isNonRenewable()).isFalse();
    }

    /**
     * The exchange is traced through the audit line, which records the issuer, the subject and the
     * client against this identifier.
     */
    @Test
    public void shouldGiveTheTokenAnIdentifierTheAuditCanBeFoundBy() {
        configure(specificUser(), subjectClaims().build());

        manager.exchange(SUBJECT_TOKEN);

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

    private static OidcAssumeStrategy mappedUser() {
        final OidcAssumeStrategy strategy = new OidcAssumeStrategy();
        strategy.setType(OidcAssumeStrategyType.MAPPED_USER);
        strategy.setUserMapping(mapping(OWNER_CLAIM, OWNER_KEY));
        return strategy;
    }

    private static OidcUserMapping mapping(final String claim, final String metadataKey) {
        final OidcUserMapping mapping = new OidcUserMapping();
        mapping.setClaim(claim);
        mapping.setMetadataKey(metadataKey);
        return mapping;
    }

    private void metadataMatches(final Long... userIds) {
        Mockito.when(metadataManager.searchMetadataByClassAndKeyValue(
                        AclClass.PIPELINE_USER, OWNER_KEY, OWNER_VALUE))
                .thenReturn(Arrays.stream(userIds)
                        .map(id -> new EntityVO(id, AclClass.PIPELINE_USER))
                        .collect(Collectors.toList()));
    }

    /**
     * The repository load is used for the name only, so it stands in here for what JPA really
     * returns: the groups are transient and the roles lazy, so neither is populated.
     */
    private void repositoryReturns(final Long... userIds) {
        Mockito.when(userManager.loadUsersById(Arrays.asList(userIds)))
                .thenReturn(Arrays.stream(userIds)
                        .map(id -> PipelineUser.builder()
                                .id(id)
                                .userName(USER_ID.equals(id) ? USER_NAME : "USER_" + id)
                                .build())
                        .collect(Collectors.toList()));
    }

    @Test
    public void shouldIssueATokenForTheUserCarryingTheClaimValueInItsMetadata() {
        metadataMatches(USER_ID);
        repositoryReturns(USER_ID);
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        final OidcExchangedToken exchanged = manager.exchange(SUBJECT_TOKEN);

        assertThat(exchanged.getToken()).isEqualTo(ISSUED_TOKEN);
        assertThat(issuedClaims().getUserName()).isEqualTo(USER_NAME);
        assertThat(issuedClaims().getUserId()).isEqualTo(String.valueOf(USER_ID));
    }

    /**
     * The claim's value is what is looked for, under the configured attribute.
     */
    @Test
    public void shouldLookTheUserUpByTheClaimValueUnderTheConfiguredMetadataKey() {
        metadataMatches(USER_ID);
        repositoryReturns(USER_ID);
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        manager.exchange(SUBJECT_TOKEN);

        Mockito.verify(metadataManager)
                .searchMetadataByClassAndKeyValue(AclClass.PIPELINE_USER, OWNER_KEY, OWNER_VALUE);
    }

    /**
     * The mapped user has to be loaded the same way as a named one. The repository load that finds
     * it by metadata gives back no groups and no roles — they are transient and lazy on the entity
     * — so building the token from that object would quietly strip the permissions a user holds
     * through a group, which is how storage access is usually granted.
     */
    @Test
    public void shouldCarryTheRolesAndGroupsOfTheMappedUserIntoTheToken() {
        storedUser(ROLE_USER, READER);
        metadataMatches(USER_ID);
        repositoryReturns(USER_ID);
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getRoles()).contains(ROLE_USER, READER);
        assertThat(issuedClaims().getGroups()).containsExactly(GROUP);
    }

    @Test
    public void shouldRefuseWhenNoUserCarriesTheClaimValue() {
        metadataMatches();
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class)
                .hasMessageContaining("No user has");
    }

    /**
     * Choosing among several would hand out a token for an account nobody meant to expose, so an
     * ambiguous mapping issues nothing at all.
     */
    @Test
    public void shouldRefuseWhenTheMappingMatchesMoreThanOneUser() {
        metadataMatches(USER_ID, OTHER_USER_ID);
        repositoryReturns(USER_ID, OTHER_USER_ID);
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class)
                .hasMessageContaining("matches 2 users");
        Mockito.verify(jwtTokenGenerator, Mockito.never())
                .encodeToken(Mockito.any(), Mockito.anyLong());
    }

    /**
     * Metadata outlives the user it described, so a stale row must not break a mapping that still
     * points at exactly one live user.
     */
    @Test
    public void shouldIgnoreMetadataLeftBehindByADeletedUser() {
        metadataMatches(USER_ID, OTHER_USER_ID);
        Mockito.when(userManager.loadUsersById(Arrays.asList(USER_ID, OTHER_USER_ID)))
                .thenReturn(Collections.singletonList(
                        PipelineUser.builder().id(USER_ID).userName(USER_NAME).build()));
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getUserName()).isEqualTo(USER_NAME);
    }

    @Test
    public void shouldRefuseWhenTheMappedClaimIsAbsentFromTheToken() {
        configure(mappedUser(), subjectClaims().build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcTokenVerificationException.class)
                .hasMessageContaining("has no value to map a user by");
        Mockito.verifyNoInteractions(metadataManager);
    }

    /**
     * A claim that arrives as a JSON number still maps, since an identifier is as often sent that
     * way as it is sent as a string.
     */
    @Test
    public void shouldMapByAClaimThatArrivesAsANumber() {
        Mockito.when(metadataManager.searchMetadataByClassAndKeyValue(
                        AclClass.PIPELINE_USER, OWNER_KEY, String.valueOf(NUMERIC_CLAIM)))
                .thenReturn(Collections.singletonList(new EntityVO(USER_ID, AclClass.PIPELINE_USER)));
        repositoryReturns(USER_ID);
        configure(mappedUser(), subjectClaims().claim(OWNER_CLAIM, NUMERIC_CLAIM).build());

        manager.exchange(SUBJECT_TOKEN);

        assertThat(issuedClaims().getUserName()).isEqualTo(USER_NAME);
    }

    /**
     * The validator refuses an incomplete mapping, so reaching this means a preference written
     * straight into the database. It is a configuration fault, and has to be reported as one: an
     * invalid_grant would send an administrator looking for a problem at the identity provider.
     */
    @Test
    public void shouldRefuseAnIncompleteMappingAsAClientFault() {
        final OidcAssumeStrategy withoutMapping = mappedUser();
        withoutMapping.setUserMapping(null);
        configure(withoutMapping, subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());
        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class)
                .hasMessageContaining("says nothing about how to map a user");

        final OidcAssumeStrategy withoutKey = mappedUser();
        withoutKey.setUserMapping(mapping(OWNER_CLAIM, null));
        configure(withoutKey, subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());
        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);

        final OidcAssumeStrategy withoutClaim = mappedUser();
        withoutClaim.setUserMapping(mapping(null, OWNER_KEY));
        configure(withoutClaim, subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());
        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class);

        Mockito.verifyNoInteractions(metadataManager);
    }

    @Test
    public void shouldRefuseAMappedUserOutsideTheClientsAllowedUsers() {
        metadataMatches(USER_ID);
        repositoryReturns(USER_ID);
        final OidcAssumeStrategy strategy = mappedUser();
        strategy.setAllowedUsers(Collections.singletonList("SOMEBODY_ELSE"));
        configure(strategy, subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class)
                .hasMessageContaining("may not act as");
    }

    /**
     * A mapped user is still subject to the client's own limits, checked on the user the lookup
     * arrived at rather than on anything the configuration named directly.
     */
    @Test
    public void shouldRefuseAMappedUserTheClientMayNotActAs() {
        metadataMatches(USER_ID);
        repositoryReturns(USER_ID);
        final OidcAssumeStrategy strategy = mappedUser();
        strategy.setDeniedUsers(Collections.singletonList(USER_NAME));
        configure(strategy, subjectClaims().claim(OWNER_CLAIM, OWNER_VALUE).build());

        assertThatThrownBy(() -> manager.exchange(SUBJECT_TOKEN))
                .isInstanceOf(OidcClientNotAuthorizedException.class)
                .hasMessageContaining("denied acting as");
    }
}
