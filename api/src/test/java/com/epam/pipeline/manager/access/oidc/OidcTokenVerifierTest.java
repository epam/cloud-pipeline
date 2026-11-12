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

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.security.oidc.OidcIssuer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OidcTokenVerifierTest {

    private static final String ISSUER_URL = "https://token.actions.githubusercontent.com";
    private static final String AUDIENCE = "https://cloud-pipeline.local/pipeline/restapi";
    private static final String SUBJECT = "repo:org/repo:environment:prod";
    private static final String KEY_ID = "signing-key";
    private static final String OTHER_KEY_ID = "another-key";
    private static final String KEYS_FIELD = "keys";
    private static final String RS256 = "RS256";
    private static final long FIVE_MINUTES_MS = 300_000L;
    private static final long ONE_HOUR_MS = 3_600_000L;

    private RSAKey signingKey;
    private PreferenceManager preferenceManager;
    private OidcTokenVerifier verifier;

    @Before
    public void setUp() throws JOSEException {
        signingKey = generateKey(KEY_ID);
        preferenceManager = Mockito.mock(PreferenceManager.class);
        verifier = new OidcTokenVerifier(preferenceManager, new OidcKeySourceProvider());
        configure(issuer());
    }

    @Test
    public void shouldAcceptGenuineToken() throws JOSEException {
        final OidcVerifiedToken verified = verifier.verify(token(claims().build(), signingKey, JWSAlgorithm.RS256));

        assertThat(verified.getClaims().getSubject()).isEqualTo(SUBJECT);
        assertThat(verified.getIssuer().getIssuer()).isEqualTo(ISSUER_URL);
    }

    @Test
    public void shouldRejectTokenFromUnconfiguredIssuer() throws JOSEException {
        final String token = token(claims().issuer("https://evil.local").build(), signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenWhenExchangeIsNotConfigured() throws JOSEException {
        final String token = token(claims().build(), signingKey, JWSAlgorithm.RS256);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_ISSUERS))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenWithNoIssuer() throws JOSEException {
        final JWTClaimsSet withoutIssuer = new JWTClaimsSet.Builder()
                .audience(AUDIENCE)
                .subject(SUBJECT)
                .expirationTime(new Date(System.currentTimeMillis() + FIVE_MINUTES_MS))
                .build();
        final String token = token(withoutIssuer, signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenSignedByAKeyOutsideTheConfiguredKeySet() throws JOSEException {
        final String token = token(claims().build(), generateKey(OTHER_KEY_ID), JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenWhoseKeyIdIsUnknown() throws JOSEException {
        final RSAKey unknownKey = new RSAKey.Builder(signingKey).keyID(OTHER_KEY_ID).build();
        final String token = token(claims().build(), unknownKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenMeantForAnotherAudience() throws JOSEException {
        final String token = token(claims().audience("https://other.local/restapi").build(),
                signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectExpiredToken() throws JOSEException {
        final String token = token(claims()
                .expirationTime(new Date(System.currentTimeMillis() - ONE_HOUR_MS))
                .build(), signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenWithoutExpiry() throws JOSEException {
        final JWTClaimsSet withoutExpiry = new JWTClaimsSet.Builder()
                .issuer(ISSUER_URL)
                .audience(AUDIENCE)
                .subject(SUBJECT)
                .issueTime(new Date())
                .build();
        final String token = token(withoutExpiry, signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenThatIsNotYetValid() throws JOSEException {
        final String token = token(claims()
                .notBeforeTime(new Date(System.currentTimeMillis() + ONE_HOUR_MS))
                .build(), signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectTokenIssuedInTheFuture() throws JOSEException {
        final String token = token(claims()
                .issueTime(new Date(System.currentTimeMillis() + ONE_HOUR_MS))
                .build(), signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldToleratePastIssueTimeWithinClockSkew() throws JOSEException {
        final String token = token(claims()
                .issueTime(new Date(System.currentTimeMillis() - FIVE_MINUTES_MS))
                .build(), signingKey, JWSAlgorithm.RS256);

        assertThat(verifier.verify(token).getClaims().getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    public void shouldRejectTokenSignedWithAnAlgorithmTheIssuerDoesNotAllow() throws JOSEException {
        final String token = token(claims().build(), signingKey, JWSAlgorithm.RS512);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectUnsignedToken() {
        final String token = new PlainJWT(claims().build()).serialize();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldIgnoreAKeySetUrlNamedByTheTokenItself() throws JOSEException {
        final RSAKey attackerKey = generateKey(KEY_ID);
        final SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .jwkURL(URI.create("https://attacker.local/keys"))
                .x509CertURL(URI.create("https://attacker.local/cert"))
                .build(), claims().build());
        jwt.sign(new RSASSASigner(attackerKey));

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectHmacTokenSignedWithThePublicModulusAsSecret() throws JOSEException {
        final byte[] secret = signingKey.toRSAPublicKey().getModulus().toByteArray();
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).build(), claims().build());
        jwt.sign(new MACSigner(secret));

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldAcceptAccessTokenTypedAsRfc9068() throws JOSEException {
        final SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .type(new JOSEObjectType("at+jwt"))
                .build(), claims().build());
        jwt.sign(new RSASSASigner(signingKey));

        assertThat(verifier.verify(jwt.serialize()).getClaims().getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    public void shouldRejectTokenWhoseExpiryIsPresentButNull() throws JOSEException {
        final JWTClaimsSet nullExpiry = new JWTClaimsSet.Builder()
                .issuer(ISSUER_URL)
                .audience(AUDIENCE)
                .subject(SUBJECT)
                .issueTime(new Date())
                .claim("exp", null)
                .build();
        final String token = token(nullExpiry, signingKey, JWSAlgorithm.RS256);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    /**
     * Removing an issuer from the preference takes effect on the next exchange, because the
     * configured list is read every time rather than held.
     */
    @Test
    public void shouldStopAcceptingTokensOnceTheIssuerIsRemovedFromTheConfiguration() throws JOSEException {
        final String token = token(claims().build(), signingKey, JWSAlgorithm.RS256);
        assertThat(verifier.verify(token).getClaims().getSubject()).isEqualTo(SUBJECT);

        Mockito.when(preferenceManager.getPreference(SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_ISSUERS))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRejectBlankToken() {
        assertThatThrownBy(() -> verifier.verify("  "))
                .isInstanceOf(OidcTokenVerificationException.class);
        assertThatThrownBy(() -> verifier.verify(null))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    private void configure(final OidcIssuer issuer) {
        final List<OidcIssuer> issuers = Collections.singletonList(issuer);
        Mockito.when(preferenceManager.getPreference(SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_ISSUERS))
                .thenReturn(issuers);
    }

    private OidcIssuer issuer() {
        final OidcIssuer issuer = new OidcIssuer();
        issuer.setIssuer(ISSUER_URL);
        issuer.setAudiences(Collections.singletonList(AUDIENCE));
        issuer.setAlgorithms(Collections.singletonList(RS256));
        issuer.setJwks(publicKeySet(signingKey));
        return issuer;
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER_URL)
                .audience(AUDIENCE)
                .subject(SUBJECT)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + FIVE_MINUTES_MS));
    }

    private static String token(final JWTClaimsSet claims, final RSAKey key, final JWSAlgorithm algorithm)
            throws JOSEException {
        final SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(algorithm).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey generateKey(final String keyId) throws JOSEException {
        return new RSAKeyGenerator(RSAKeyGenerator.MIN_KEY_SIZE_BITS)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    private static Map<String, Object> publicKeySet(final JWK key) {
        final Map<String, Object> keySet = new HashMap<>();
        keySet.put(KEYS_FIELD, Collections.singletonList(key.toPublicJWK().toJSONObject()));
        return keySet;
    }
}
