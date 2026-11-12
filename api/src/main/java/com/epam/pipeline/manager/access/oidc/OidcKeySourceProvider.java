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

import com.epam.pipeline.security.oidc.OidcIssuer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supplies the signing keys a configured issuer's tokens are checked against.
 * <p>
 * The keys come from whichever source the administrator configured, and from nowhere else. A token
 * never names where its own keys live: the {@code jku} and {@code x5u} headers a token may carry
 * are not read here, because following one would let a caller hand over the key their own
 * signature is checked against.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OidcKeySourceProvider {

    private static final String HTTPS_PROTOCOL = "https";
    private static final String KEYS_FIELD = "keys";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    /**
     * How long a failed attempt is remembered. The endpoint this serves needs no platform token,
     * so without it every token a caller posts would start another discovery request while the
     * provider is down — one outbound request per inbound one, and a request thread held for the
     * timeouts above each time.
     */
    private static final long FAILURE_BACKOFF_MS = 30_000L;

    private final Map<String, CachedKeySource> keySources = new ConcurrentHashMap<>();

    /**
     * Built under the map's own lock, so two tokens arriving together for the same issuer do not
     * both run discovery, and a configuration change replaces the entry rather than racing it. The
     * work is per issuer and happens once per configuration, so holding the lock across it costs
     * less than the duplicate fetches it prevents.
     */
    public JWKSource<SecurityContext> getKeySource(final OidcIssuer issuer) {
        final int fingerprint = fingerprint(issuer);
        return keySources.compute(issuer.getIssuer(), (name, existing) -> {
            if (Objects.nonNull(existing) && existing.isFresh(fingerprint)) {
                return existing;
            }
            close(existing);
            return buildCached(issuer, fingerprint);
        }).keySourceOrFail();
    }

    private CachedKeySource buildCached(final OidcIssuer issuer, final int fingerprint) {
        try {
            return CachedKeySource.succeeded(fingerprint, build(issuer));
        } catch (OidcTokenVerificationException e) {
            log.warn("Failed to build a key source for issuer {}", issuer.getIssuer(), e);
            return CachedKeySource.failed(fingerprint, e.getMessage());
        }
    }

    /**
     * Over the fields that decide where the keys come from, so editing any of them replaces the
     * cached source and editing anything else leaves it alone.
     */
    private static int fingerprint(final OidcIssuer issuer) {
        return Objects.hash(issuer.getIssuer(), issuer.getJwksUri(), issuer.getJwks());
    }

    private JWKSource<SecurityContext> build(final OidcIssuer issuer) {
        if (Objects.nonNull(issuer.getJwks())) {
            log.debug("Using the inline key set configured for issuer {}", issuer.getIssuer());
            return inlineKeySource(issuer.getJwks());
        }
        final URL keySetUrl = Objects.nonNull(issuer.getJwksUri())
                ? toSecureUrl(issuer.getJwksUri())
                : discoverKeySetUrl(issuer.getIssuer());
        log.debug("Using key set {} for issuer {}", keySetUrl, issuer.getIssuer());
        return remoteKeySource(keySetUrl);
    }

    /**
     * Caching and the rate-limited refetch on an unknown key id are on by default; the retry and
     * the fall back to the last known good set are not, and are asked for here. The default
     * timeouts are not asked for either: they are 500 ms, which a key set fetched across the
     * internet will miss on a slow handshake, and a missed fetch leaves nothing cached to fall
     * back to.
     */
    private JWKSource<SecurityContext> remoteKeySource(final URL keySetUrl) {
        return JWKSourceBuilder.<SecurityContext>create(keySetUrl,
                        new DefaultResourceRetriever(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS,
                                JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT))
                .retrying(true)
                .outageTolerant(true)
                .build();
    }

    /**
     * Parsing a key set is lenient: a key it cannot read is dropped rather than reported. So the
     * count is compared with what was configured, because dropping some of the keys is as much a
     * mistake as dropping all of them — half a set survives a provider's key rotation only until
     * the provider signs with the key that went missing, and an inline set cannot refetch to
     * recover.
     */
    private JWKSource<SecurityContext> inlineKeySource(final Map<String, Object> jwks) {
        final JWKSet keySet;
        try {
            keySet = JWKSet.parse(jwks);
        } catch (ParseException e) {
            throw new OidcTokenVerificationException("Configured inline key set cannot be parsed", e);
        }
        final int configured = configuredKeyCount(jwks);
        if (keySet.size() != configured) {
            throw new OidcTokenVerificationException(String.format(
                    "Configured inline key set holds %d keys but only %d of them can be read",
                    configured, keySet.size()));
        }
        return new ImmutableJWKSet<>(keySet);
    }

    private static int configuredKeyCount(final Map<String, Object> jwks) {
        final Object keys = jwks.get(KEYS_FIELD);
        return keys instanceof Collection ? ((Collection<?>) keys).size() : 0;
    }

    /**
     * Discovery runs against the <em>configured</em> issuer, never a value taken from a token.
     * {@link OIDCProviderMetadata#resolve} already refuses a document whose own issuer disagrees;
     * the same comparison is repeated below so that this class does not depend on the library
     * continuing to make it.
     */
    private URL discoverKeySetUrl(final String issuerName) {
        final Issuer expected = new Issuer(toSecureUrl(issuerName).toString());
        final OIDCProviderMetadata metadata = resolveMetadata(expected);
        if (!expected.equals(metadata.getIssuer())) {
            throw new OidcTokenVerificationException(String.format(
                    "Discovery document for issuer %s declares a different issuer %s",
                    issuerName, metadata.getIssuer()));
        }
        final URI keySetUri = metadata.getJWKSetURI();
        if (Objects.isNull(keySetUri)) {
            throw new OidcTokenVerificationException(String.format(
                    "Discovery document for issuer %s names no key set", issuerName));
        }
        return toSecureUrl(keySetUri.toString());
    }

    private OIDCProviderMetadata resolveMetadata(final Issuer expected) {
        try {
            return OIDCProviderMetadata.resolve(expected, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        } catch (GeneralException | IOException e) {
            throw new OidcTokenVerificationException(String.format(
                    "Failed to resolve the discovery document of issuer %s", expected), e);
        }
    }

    /**
     * Applied to every URL fetched here, including one read out of a discovery document, which has
     * not been through the check the preference validator makes when the issuer is saved.
     */
    private URL toSecureUrl(final String value) {
        try {
            final URL url = new URL(value);
            if (!HTTPS_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                throw new OidcTokenVerificationException(String.format("URL %s is not an https URL", value));
            }
            return url;
        } catch (MalformedURLException e) {
            throw new OidcTokenVerificationException(String.format("URL %s is malformed", value), e);
        }
    }

    /**
     * A remote source owns a background thread for its refresh-ahead cache, so a source being
     * replaced is closed rather than left to a finalizer.
     */
    private void close(final CachedKeySource cached) {
        if (Objects.isNull(cached) || !(cached.keySource instanceof Closeable)) {
            return;
        }
        try {
            ((Closeable) cached.keySource).close();
        } catch (IOException e) {
            log.warn("Failed to close a replaced key source", e);
        }
    }

    @RequiredArgsConstructor
    private static final class CachedKeySource {

        private final int fingerprint;
        private final JWKSource<SecurityContext> keySource;
        private final String failure;
        private final long failedAt;

        private static CachedKeySource succeeded(final int fingerprint,
                                                 final JWKSource<SecurityContext> keySource) {
            return new CachedKeySource(fingerprint, keySource, null, 0L);
        }

        private static CachedKeySource failed(final int fingerprint, final String failure) {
            return new CachedKeySource(fingerprint, null, failure, System.currentTimeMillis());
        }

        /**
         * A success is kept until the configuration changes; a failure only for the backoff, after
         * which it is worth trying the provider again.
         */
        private boolean isFresh(final int currentFingerprint) {
            if (fingerprint != currentFingerprint) {
                return false;
            }
            return Objects.nonNull(keySource)
                    || System.currentTimeMillis() - failedAt < FAILURE_BACKOFF_MS;
        }

        private JWKSource<SecurityContext> keySourceOrFail() {
            if (Objects.isNull(keySource)) {
                throw new OidcTokenVerificationException(failure);
            }
            return keySource;
        }
    }
}
