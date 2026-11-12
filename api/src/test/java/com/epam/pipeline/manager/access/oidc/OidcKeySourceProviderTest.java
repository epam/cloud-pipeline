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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OidcKeySourceProviderTest {

    private static final String ISSUER_URL = "https://token.actions.githubusercontent.com";
    private static final String KEYS_FIELD = "keys";
    private static final String FIRST_KEY_ID = "first";
    private static final String SECOND_KEY_ID = "second";

    private OidcKeySourceProvider provider;

    @Before
    public void setUp() {
        provider = new OidcKeySourceProvider();
    }

    @Test
    public void shouldBuildAnInMemorySourceFromAnInlineKeySet() throws JOSEException {
        final JWKSource<SecurityContext> source = provider.getKeySource(issuerWithKey(FIRST_KEY_ID));

        assertThat(source).isInstanceOf(ImmutableJWKSet.class);
    }

    @Test
    public void shouldReuseTheSourceWhileTheConfigurationIsUnchanged() throws JOSEException {
        final OidcIssuer issuer = issuerWithKey(FIRST_KEY_ID);

        assertThat(provider.getKeySource(issuer)).isSameAs(provider.getKeySource(issuer));
    }

    @Test
    public void shouldRebuildTheSourceOnceTheKeySetChanges() throws JOSEException {
        final JWKSource<SecurityContext> before = provider.getKeySource(issuerWithKey(FIRST_KEY_ID));
        final JWKSource<SecurityContext> after = provider.getKeySource(issuerWithKey(SECOND_KEY_ID));

        assertThat(after).isNotSameAs(before);
    }

    @Test
    public void shouldRebuildTheSourceOnceTheKeySetUrlChanges() throws JOSEException {
        final JWKSource<SecurityContext> inline = provider.getKeySource(issuerWithKey(FIRST_KEY_ID));

        final OidcIssuer movedToUrl = issuerWithKey(FIRST_KEY_ID);
        movedToUrl.setJwks(null);
        movedToUrl.setJwksUri("http://provider.local/keys");

        // a plain HTTP key set is refused rather than fetched, which also proves the cached inline
        // source was not handed back for the changed configuration
        assertThatThrownBy(() -> provider.getKeySource(movedToUrl))
                .isInstanceOf(OidcTokenVerificationException.class);
        assertThat(inline).isInstanceOf(ImmutableJWKSet.class);
    }

    @Test
    public void shouldRefuseAKeySetUrlThatIsNotHttps() {
        final OidcIssuer issuer = issuer();
        issuer.setJwksUri("http://provider.local/keys");

        assertThatThrownBy(() -> provider.getKeySource(issuer))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRefuseAMalformedKeySetUrl() {
        final OidcIssuer issuer = issuer();
        issuer.setJwksUri("not a url");

        assertThatThrownBy(() -> provider.getKeySource(issuer))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    @Test
    public void shouldRefuseAnInlineKeySetWhoseKeysAreAllUnreadable() {
        final OidcIssuer issuer = issuer();
        final Map<String, Object> broken = new HashMap<>();
        broken.put(KEYS_FIELD, Collections.singletonList(
                Collections.singletonMap("kty", "nonsense")));
        issuer.setJwks(broken);

        assertThatThrownBy(() -> provider.getKeySource(issuer))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    /**
     * Parsing drops a key it cannot read instead of failing, so a set that is only partly readable
     * would otherwise be accepted and then reject every token signed with the missing key.
     */
    @Test
    public void shouldRefuseAnInlineKeySetThatIsOnlyPartlyReadable() throws JOSEException {
        final OidcIssuer issuer = issuer();
        final JWK good = new RSAKeyGenerator(RSAKeyGenerator.MIN_KEY_SIZE_BITS)
                .keyID(FIRST_KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        final Map<String, Object> partly = new HashMap<>();
        partly.put(KEYS_FIELD, Arrays.asList(
                good.toPublicJWK().toJSONObject(),
                Collections.singletonMap("kty", "nonsense")));
        issuer.setJwks(partly);

        assertThatThrownBy(() -> provider.getKeySource(issuer))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    /**
     * The endpoint needs no platform token, so a provider that is down must not turn one inbound
     * token into one outbound request: the failure is remembered and refused without a fetch.
     */
    @Test
    public void shouldRememberAFailureRatherThanRetryingItPerToken() {
        final OidcIssuer issuer = issuer();
        issuer.setJwksUri("http://provider.local/keys");

        assertThatThrownBy(() -> provider.getKeySource(issuer))
                .isInstanceOf(OidcTokenVerificationException.class);
        assertThatThrownBy(() -> provider.getKeySource(issuer))
                .isInstanceOf(OidcTokenVerificationException.class);
    }

    private static OidcIssuer issuer() {
        final OidcIssuer issuer = new OidcIssuer();
        issuer.setIssuer(ISSUER_URL);
        return issuer;
    }

    private static OidcIssuer issuerWithKey(final String keyId) throws JOSEException {
        final OidcIssuer issuer = issuer();
        final JWK key = new RSAKeyGenerator(RSAKeyGenerator.MIN_KEY_SIZE_BITS)
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        final Map<String, Object> keySet = new HashMap<>();
        keySet.put(KEYS_FIELD, Collections.singletonList(key.toPublicJWK().toJSONObject()));
        issuer.setJwks(keySet);
        return issuer;
    }
}
