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

package com.epam.pipeline.security.saml;

import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Component
public class CustomSamlRelyingPartyRegistrationBuilder {

    private final String federationMetadataFile;
    private final String registrationId;
    private final String endpointId;
    private final String acsEndpoint;
    private final String signingKey;
    private final String keyAlias;
    private final String keyStore;
    private final String keyStorePassword;

    public CustomSamlRelyingPartyRegistrationBuilder(
            @Value("${server.ssl.metadata}") final String federationMetadataFile,
            @Value("${saml.sso.registration-id:SSO}") final String registrationId,
            @Value("${server.ssl.endpoint.id}") final String endpointId,
            @Value("${saml.sso.acs.endpoint:/saml/SSO}") final String acsEndpoint,
            @Value("${saml.sign.key}") final String signingKey,
            @Value("${server.ssl.keyAlias}") final String keyAlias,
            @Value("${server.ssl.key-store}") final String keyStore,
            @Value("${server.ssl.key-store-password}") final String keyStorePassword) {
        this.federationMetadataFile = federationMetadataFile;
        this.registrationId = registrationId;
        this.endpointId = endpointId;
        this.acsEndpoint = acsEndpoint;
        this.signingKey = signingKey;
        this.keyAlias = keyAlias;
        this.keyStore = keyStore;
        this.keyStorePassword = keyStorePassword;
    }

    public RelyingPartyRegistration build() {
        return RelyingPartyRegistrations.fromMetadataLocation(federationMetadataFile)
                .assertingPartyDetails((party) -> party
                        .singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT)
                        .signingAlgorithms((sign) -> sign.add(SignatureConstants.ALGO_ID_SIGNATURE_RSA)))
                .registrationId(registrationId) // TODO: probably not the best option (it is just a name)
                .entityId(endpointId)
                .assertionConsumerServiceLocation(buildAcsUrl())
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST) // TODO: or redirect?
                .signingX509Credentials((c) -> {
                    c.add(getSigningX509Credentials(signingKey, Saml2X509Credential.Saml2X509CredentialType.SIGNING));
                    c.add(getSigningX509Credentials(keyAlias, Saml2X509Credential.Saml2X509CredentialType.SIGNING,
                            Saml2X509Credential.Saml2X509CredentialType.DECRYPTION));
                })
                .authnRequestsSigned(true)
                .build();
    }

    private Saml2X509Credential getSigningX509Credentials(
            final String alias, final Saml2X509Credential.Saml2X509CredentialType... usageType) {
        try {
            final var loader = new FileSystemResourceLoader();
            final var storeFile = loader.getResource(keyStore);
            final var ks = KeyStore.getInstance("JKS");
            ks.load(storeFile.getInputStream(), keyStorePassword.toCharArray());

            final var certificate = (X509Certificate) ks.getCertificate(alias);

            final var privateKeyEntry = (KeyStore.PrivateKeyEntry) ks.getEntry(
                    alias,
                    new KeyStore.PasswordProtection(keyStorePassword.toCharArray())
            );
            final var privateKey = privateKeyEntry.getPrivateKey();

            return new Saml2X509Credential(
                    privateKey,
                    certificate,
                    usageType
            );
        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException |
                 UnrecoverableEntryException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildAcsUrl() {
        return ProviderUtils.withoutTrailingDelimiter(endpointId) + ProviderUtils.withLeadingDelimiter(acsEndpoint);
    }
}
