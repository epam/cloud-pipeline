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
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks that a subject token really was issued by a configured provider, and that its own claims
 * allow it to be used now.
 * <p>
 * This settles only whether the token is genuine. Which caller it belongs to, and which user it may
 * act as, are decided afterwards from the verified claims.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OidcTokenVerifier {

    private static final int CLOCK_SKEW_SECONDS = 60;
    private static final long MILLIS_IN_SECOND = 1000L;
    private static final Set<JWSAlgorithm> DEFAULT_ALGORITHMS =
            new HashSet<>(Arrays.asList(JWSAlgorithm.RS256, JWSAlgorithm.ES256));

    /**
     * Every asymmetric algorithm the exchange will verify with. The configured list is narrowed to
     * this, so a value that reached the preference without passing its validator — written
     * straight into the table, or saved before the validator existed — cannot widen what is
     * accepted. An HMAC entry here would have a public key read as a shared secret.
     */
    private static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS = new HashSet<>(Arrays.asList(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512,
            JWSAlgorithm.EdDSA));
    private static final Set<String> REQUIRED_CLAIMS =
            new HashSet<>(Arrays.asList("iss", "aud", "exp"));

    /**
     * An access token may be typed {@code at+jwt} (RFC 9068), which Okta, Auth0 and Keycloak do,
     * or {@code JWT}, which Entra ID and GitHub Actions do, or carry no type at all. The library's
     * default allows only the last two, which would refuse the first group outright.
     */
    private static final JOSEObjectTypeVerifier<SecurityContext> TYPE_VERIFIER =
            new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, new JOSEObjectType("at+jwt"), null);

    private final PreferenceManager preferenceManager;
    private final OidcKeySourceProvider keySourceProvider;

    public OidcVerifiedToken verify(final String token) {
        final SignedJWT jwt = parse(token);
        final OidcIssuer issuer = findIssuer(readIssuerName(jwt));
        final JWTClaimsSet claims = process(jwt, issuer);
        verifyExpiry(claims);
        verifyIssuedAt(claims);
        return new OidcVerifiedToken(issuer, claims);
    }

    /**
     * Parsing as a signed token rejects an unsigned one before any key is looked at: a token with
     * {@code alg} of {@code none} carries an empty signature and does not parse here.
     */
    private SignedJWT parse(final String token) {
        if (StringUtils.isBlank(token)) {
            throw new OidcTokenVerificationException("Subject token is missing");
        }
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new OidcTokenVerificationException("Subject token is not a signed JWT", e);
        }
    }

    /**
     * Reads {@code iss} from claims that have not been verified yet, which is sound because the
     * value is used for one thing only: choosing which configured entry to check the token
     * against. An issuer that is not configured is refused here, before anything is fetched over
     * the network.
     */
    private String readIssuerName(final SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            throw new OidcTokenVerificationException("Subject token has unreadable claims", e);
        }
    }

    private OidcIssuer findIssuer(final String issuerName) {
        if (StringUtils.isBlank(issuerName)) {
            throw new OidcTokenVerificationException("Subject token names no issuer");
        }
        final List<OidcIssuer> issuers = preferenceManager.getPreference(
                SystemPreferences.SYSTEM_OIDC_TOKEN_EXCHANGE_ISSUERS);
        // the configured list is read on every exchange anyway, which makes this the one place
        // that can tell the provider an issuer has been deleted rather than merely edited
        keySourceProvider.retainConfigured(configuredNames(issuers));
        if (CollectionUtils.isEmpty(issuers)) {
            throw new OidcTokenVerificationException("Token exchange is not configured");
        }
        return issuers.stream()
                .filter(candidate -> issuerName.equals(candidate.getIssuer()))
                .findFirst()
                .orElseThrow(() -> new OidcTokenVerificationException(String.format(
                        "Issuer %s is not configured for token exchange", issuerName)));
    }

    private static Set<String> configuredNames(final List<OidcIssuer> issuers) {
        return ListUtils.emptyIfNull(issuers).stream()
                .map(OidcIssuer::getIssuer)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private JWTClaimsSet process(final SignedJWT jwt, final OidcIssuer issuer) {
        final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSTypeVerifier(TYPE_VERIFIER);
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                algorithms(issuer), keySourceProvider.getKeySource(issuer)));
        processor.setJWTClaimsSetVerifier(claimsVerifier(issuer));
        try {
            return processor.process(jwt, null);
        } catch (BadJOSEException | JOSEException e) {
            throw new OidcTokenVerificationException(String.format(
                    "Subject token from issuer %s was rejected", issuer.getIssuer()), e);
        }
    }

    /**
     * The allow-list is what keeps a token from choosing how it is verified. Without it a caller
     * could present an HMAC-signed token and have a public key read as a shared secret.
     */
    private Set<JWSAlgorithm> algorithms(final OidcIssuer issuer) {
        if (CollectionUtils.isEmpty(issuer.getAlgorithms())) {
            return DEFAULT_ALGORITHMS;
        }
        // parse accepts any string, so the configured names are narrowed to the ones this class
        // is prepared to verify rather than taken as given
        final Set<JWSAlgorithm> configured = issuer.getAlgorithms().stream()
                .map(String::trim)
                .map(JWSAlgorithm::parse)
                .filter(SUPPORTED_ALGORITHMS::contains)
                .collect(Collectors.toSet());
        if (configured.isEmpty()) {
            throw new OidcTokenVerificationException(String.format(
                    "Issuer %s allows no algorithm this exchange can verify with", issuer.getIssuer()));
        }
        return configured;
    }

    private DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier(final OidcIssuer issuer) {
        if (CollectionUtils.isEmpty(issuer.getAudiences())) {
            throw new OidcTokenVerificationException(String.format(
                    "Issuer %s names no audience, so no token can be checked against it",
                    issuer.getIssuer()));
        }
        final DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(
                new HashSet<>(issuer.getAudiences()),
                new JWTClaimsSet.Builder().issuer(issuer.getIssuer()).build(),
                REQUIRED_CLAIMS,
                null);
        verifier.setMaxClockSkew(CLOCK_SKEW_SECONDS);
        return verifier;
    }

    /**
     * Listing {@code exp} as required only makes the library check that the claim is present. Its
     * expiry check is skipped when the value does not read back as a time, and a null value reads
     * back that way, so a token carrying {@code "exp": null} would be accepted and never expire.
     */
    private void verifyExpiry(final JWTClaimsSet claims) {
        if (Objects.isNull(claims.getExpirationTime())) {
            throw new OidcTokenVerificationException("Subject token carries no usable expiry time");
        }
    }

    /**
     * A token issued in the future is a clock that disagrees with ours, or a token built by hand.
     * The processor checks {@code exp} and {@code nbf} but not this.
     */
    private void verifyIssuedAt(final JWTClaimsSet claims) {
        final Date issuedAt = claims.getIssueTime();
        if (Objects.isNull(issuedAt)) {
            return;
        }
        final long latestAccepted = System.currentTimeMillis() + CLOCK_SKEW_SECONDS * MILLIS_IN_SECOND;
        if (issuedAt.getTime() > latestAccepted) {
            throw new OidcTokenVerificationException(String.format(
                    "Subject token is issued at %s, which is in the future", issuedAt));
        }
    }
}
