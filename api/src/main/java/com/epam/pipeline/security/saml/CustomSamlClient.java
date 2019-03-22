/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.coveo.saml.SamlException;
import com.coveo.saml.SamlResponse;
import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import org.joda.time.DateTime;
import org.opensaml.common.SAMLException;
import org.opensaml.common.binding.decoding.BasicURLComparator;
import org.opensaml.common.binding.decoding.URIComparator;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.AuthnStatement;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.validator.ResponseSchemaValidator;
import org.opensaml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml2.metadata.SingleSignOnService;
import org.opensaml.saml2.metadata.impl.EndpointImpl;
import org.opensaml.saml2.metadata.provider.DOMMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.encryption.DecryptionException;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.credential.UsageType;
import org.opensaml.xml.security.keyinfo.KeyInfoHelper;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.signature.X509Data;
import org.opensaml.xml.util.Base64;
import org.opensaml.xml.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.saml.context.SAMLMessageContext;
import org.springframework.security.saml.websso.WebSSOProfileConsumerImpl;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.security.saml.util.SAMLUtil.isDateTimeSkewValid;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class CustomSamlClient extends WebSSOProfileConsumerImpl {
    private static final Logger logger = LoggerFactory.getLogger(CustomSamlClient.class);
    public static final String SSO_ENDPOINT = "/saml/SSO";

    public enum SamlIdpBinding {
        POST,
        Redirect;
    }

    private int responseSkew;
    private String relyingPartyIdentifier;
    private String identityProviderUrl;
    private String responseIssuer;
    private List<Credential> credentials;
    protected URIComparator uriComparator = new BasicURLComparator();

    /**
     * Returns the url where SAML requests should be posted.
     *
     * @return the url where SAML requests should be posted.
     */
    public String getIdentityProviderUrl() {
        return identityProviderUrl;
    }

    /**
     * Constructs an SAML client using explicit parameters.
     *
     * @param relyingPartyIdentifier      the identifier of the relying party.
     * @param identityProviderUrl         the url where the SAML request will be submitted.
     * @param responseIssuer              the expected issuer ID for SAML responses.
     * @param certificates                the list of base-64 encoded certificates to use to validate
     *                                    responses.
     * @param responseSkew
     * @throws SamlException thrown if any error occur while loading the provider information.
     */
    public CustomSamlClient(
        String relyingPartyIdentifier,
        String identityProviderUrl,
        String responseIssuer,
        List<X509Certificate> certificates,
        int responseSkew) throws SAMLException {
        this.responseSkew = responseSkew;

        if (relyingPartyIdentifier == null) {
            throw new IllegalArgumentException("relyingPartyIdentifier");
        }
        if (identityProviderUrl == null) {
            throw new IllegalArgumentException("identityProviderUrl");
        }
        if (responseIssuer == null) {
            throw new IllegalArgumentException("responseIssuer");
        }
        if (certificates == null || certificates.isEmpty()) {
            throw new IllegalArgumentException("certificates");
        }

        this.relyingPartyIdentifier = relyingPartyIdentifier;
        this.identityProviderUrl = identityProviderUrl;
        this.responseIssuer = responseIssuer;
        credentials = certificates.stream().map(CustomSamlClient::getCredential).collect(Collectors.toList());
    }

    /**
     * Decodes and validates an SAML response returned by an identity provider.
     *
     * @param encodedResponse the encoded response returned by the identity provider.
     * @return An {@link SamlResponse} object containing information decoded from the SAML response.
     * @throws SamlException if the signature is invalid, or if any other error occurs.
     */
    public SamlResponse decodeAndValidateSamlResponse(String encodedResponse) throws SAMLException {
        Response response = decodeSamlResponse(encodedResponse);

        return validate(response);
    }

    /**
     * Decode SAMLResponse with no validation
     * @param encodedResponse the encoded response returned by the identity provider.
     * @return An {@link Response} object containing information decoded from the SAML response.
     * @throws SAMLException
     */
    public static Response decodeSamlResponse(String encodedResponse) throws SAMLException {
        String decodedResponse;
        try {
            decodedResponse = new String(Base64.decode(encodedResponse), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new SAMLException("Cannot decode base64 encoded response", ex);
        }

        logger.trace("Validating SAML response: " + decodedResponse);

        try {
            DOMParser parser = createDOMParser();
            parser.parse(new InputSource(new StringReader(decodedResponse)));
            return (Response) Configuration.getUnmarshallerFactory()
                        .getUnmarshaller(parser.getDocument().getDocumentElement())
                        .unmarshall(parser.getDocument().getDocumentElement());
        } catch (IOException | SAXException | UnmarshallingException ex) {
            throw new SAMLException("Cannot decode xml encoded response", ex);
        }
    }

    /**
     * Validates SAML response
     * @param response a response to validate
     * @return An {@link SamlResponse} object containing information decoded from the SAML response.
     * @throws SamlException if the signature is invalid, or if any other error occurs.
     */
    public SamlResponse validate(Response response) throws SAMLException {
        validateResponse(response);
        validateSignature(response);
        validateIssueTime(response);
        validateAssertion(response);
        validateDestination(response);

        Assertion assertion = response.getAssertions().get(0);
        return new SamlResponse(assertion);
    }

    private void validateIssueTime(Response response) throws SAMLException {
        DateTime time = response.getIssueInstant();
        if (!isDateTimeSkewValid(responseSkew, time)) {
            throw new SAMLException("Response issue time is either too old or with date in the future, skew "
                                    + responseSkew + ", time " + time);
        }
    }

    private void validateDestination(Response response) throws SAMLException {
        String destination = response.getDestination();
        if (destination != null && !uriComparator.compare(destination, relyingPartyIdentifier + SSO_ENDPOINT)) {
            throw new SAMLException("Intended destination " + destination
                                    + " doesn't match any of the endpoint URLs on endpoint ");
        }
    }

    /**
     * Constructs an SAML client using XML metadata obtained from the identity provider. <p> When
     * using Okta as an identity provider, it is possible to pass null to relyingPartyIdentifier and
     * assertionConsumerServiceUrl; they will be inferred from the metadata provider XML.
     *
     * @param relyingPartyIdentifier      the identifier for the relying party.
     * @param metadata                    the XML metadata obtained from the identity provider.
     * @return The created {@link CustomSamlClient}.
     * @throws SamlException thrown if any error occur while loading the metadata information.
     */
    public static CustomSamlClient fromMetadata(
        String relyingPartyIdentifier, Reader metadata, int responseSkew)
        throws SAMLException {
        return fromMetadata(
            relyingPartyIdentifier, metadata, CustomSamlClient.SamlIdpBinding.POST, responseSkew);
    }

    /**
     * Constructs an SAML client using XML metadata obtained from the identity provider. <p> When
     * using Okta as an identity provider, it is possible to pass null to relyingPartyIdentifier and
     * assertionConsumerServiceUrl; they will be inferred from the metadata provider XML.
     *
     * @param relyingPartyIdentifier      the identifier for the relying party.
     * @param metadata                    the XML metadata obtained from the identity provider.
     * @param samlBinding                 the HTTP method to use for binding to the IdP.
     * @return The created {@link CustomSamlClient}.
     * @throws SamlException thrown if any error occur while loading the metadata information.
     */
    public static CustomSamlClient fromMetadata(
        String relyingPartyIdentifier,
        Reader metadata,
        CustomSamlClient.SamlIdpBinding samlBinding, int responseSkew)
        throws SAMLException {

        MetadataProvider metadataProvider = createMetadataProvider(metadata);
        EntityDescriptor entityDescriptor = getEntityDescriptor(metadataProvider);

        IDPSSODescriptor idpSsoDescriptor = getIDPSSODescriptor(entityDescriptor);
        SingleSignOnService idpBinding = getIdpBinding(idpSsoDescriptor, samlBinding);
        List<X509Certificate> x509Certificates = getCertificates(idpSsoDescriptor);
        boolean isOkta = entityDescriptor.getEntityID().contains(".okta.com");

        if (relyingPartyIdentifier == null) {
            // Okta's own toolkit uses the entity ID as a relying party identifier, so if we
            // detect that the IDP is Okta let's tolerate a null value for this parameter.
            if (isOkta) {
                relyingPartyIdentifier = entityDescriptor.getEntityID();
            } else {
                throw new IllegalArgumentException("relyingPartyIdentifier");
            }
        }

        String identityProviderUrl = idpBinding.getLocation();
        String responseIssuer = entityDescriptor.getEntityID();

        return new CustomSamlClient(
            relyingPartyIdentifier,
            identityProviderUrl,
            responseIssuer,
            x509Certificates,
            responseSkew);
    }

    private void validateResponse(Response response) throws SAMLException {
        try {
            new ResponseSchemaValidator().validate(response);
        } catch (ValidationException ex) {
            throw new SAMLException("The response schema validation failed", ex);
        }

        if (!response.getIssuer().getValue().equals(responseIssuer)) {
            throw new SAMLException("The response issuer didn't match the expected value");
        }

        String statusCode = response.getStatus().getStatusCode().getValue();

        if (!statusCode.equals("urn:oasis:names:tc:SAML:2.0:status:Success")) {
            throw new SAMLException("Invalid status code: " + statusCode);
        }
    }

    private void validateAssertion(Response response) throws SAMLException {
        if (response.getAssertions().size() != 1) {
            throw new SAMLException("The response doesn't contain exactly 1 assertion");
        }

        Assertion assertion = response.getAssertions().get(0);

        // Verify storage time skew
        if (!isDateTimeSkewValid(getResponseSkew(), getMaxAssertionTime(), assertion.getIssueInstant())) {
            throw new SAMLException(
                "Assertion is too old to be used, value can be customized by setting maxAssertionTime value "
                + assertion.getIssueInstant());
        }

        if (!assertion.getIssuer().getValue().equals(responseIssuer)) {
            throw new SAMLException("The assertion issuer didn't match the expected value");
        }

        if (assertion.getSubject().getNameID() == null) {
            throw new SAMLException(
                "The NameID value is missing from the SAML response; this is likely an IDP configuration issue");
        }

        SAMLMessageContext context = new SAMLMessageContext();
        context.setLocalEntityId(relyingPartyIdentifier);
        context.setLocalEntityEndpoint(new EndpointImpl(null, "", "") {
            @Override
            public String getLocation() {
                return relyingPartyIdentifier + SSO_ENDPOINT;
            }
        });

        try {
            verifySubject(assertion.getSubject(), null, context);
        } catch (DecryptionException e) {
            throw new SAMLException(e);
        }

        if (assertion.getAuthnStatements().size() > 0) {
            verifyAssertionConditions(assertion.getConditions(), context, true);
            for (AuthnStatement statement : assertion.getAuthnStatements()) {
                verifyAuthenticationStatement(statement, null, context);
            }
        } else {
            verifyAssertionConditions(assertion.getConditions(), context, false);
        }
    }

    private void validateSignature(Response response) throws SAMLException {
        Signature responseSignature = response.getSignature();
        Signature assertionSignature = response.getAssertions().get(0).getSignature();

        if (responseSignature == null && assertionSignature == null) {
            throw new SAMLException("No signature is present in either response or assertion");
        }

        if (responseSignature != null && !validate(responseSignature)) {
            throw new SAMLException("The response signature is invalid");
        }

        if (assertionSignature != null && !validate(assertionSignature)) {
            throw new SAMLException("The assertion signature is invalid");
        }
    }

    private boolean validate(Signature signature) {
        if (signature == null) {
            return false;
        }

        // It's fine if any of the credentials match the signature
        return credentials
            .stream()
            .anyMatch(
                c -> {
                    try {
                        SignatureValidator signatureValidator = new SignatureValidator(c);
                        signatureValidator.validate(signature);
                        return true;
                    } catch (ValidationException ex) {
                        return false;
                    }
                });
    }

    private static DOMParser createDOMParser() throws SAMLException {
        DOMParser parser =
            new DOMParser() {
                {
                    try {
                        setFeature(INCLUDE_COMMENTS_FEATURE, false);
                    } catch (Exception ex) {
                        throw new SAMLException(
                            "Cannot disable comments parsing to mitigate https://www.kb.cert.org/vuls/id/475445",
                            ex);
                    }
                }
            };

        return parser;
    }

    private static MetadataProvider createMetadataProvider(Reader metadata) throws SAMLException {
        try {
            DOMParser parser = createDOMParser();
            parser.parse(new InputSource(metadata));
            DOMMetadataProvider provider =
                new DOMMetadataProvider(parser.getDocument().getDocumentElement());
            provider.initialize();
            return provider;
        } catch (IOException | SAXException | MetadataProviderException ex) {
            throw new SAMLException("Cannot load identity provider metadata", ex);
        }
    }

    private static EntityDescriptor getEntityDescriptor(MetadataProvider metadataProvider)
        throws SAMLException {
        EntityDescriptor descriptor;

        try {
            descriptor = (EntityDescriptor) metadataProvider.getMetadata();
        } catch (MetadataProviderException ex) {
            throw new SAMLException("Cannot retrieve the entity descriptor", ex);
        }

        if (descriptor == null) {
            throw new SAMLException("Cannot retrieve the entity descriptor");
        }

        return descriptor;
    }

    private static IDPSSODescriptor getIDPSSODescriptor(EntityDescriptor entityDescriptor)
        throws SAMLException {
        IDPSSODescriptor idpssoDescriptor =
            entityDescriptor.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol");
        if (idpssoDescriptor == null) {
            throw new SAMLException("Cannot retrieve IDP SSO descriptor");
        }

        return idpssoDescriptor;
    }

    private static SingleSignOnService getIdpBinding(
        IDPSSODescriptor idpSsoDescriptor, CustomSamlClient.SamlIdpBinding samlBinding) throws SAMLException {
        return idpSsoDescriptor
            .getSingleSignOnServices()
            .stream()
            .filter(
                x
                    -> x.getBinding()
                    .equals("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-" + samlBinding.toString()))
            .findAny()
            .orElseThrow(() -> new SAMLException("Cannot find HTTP-POST SSO binding in metadata"));
    }

    private static List<X509Certificate> getCertificates(IDPSSODescriptor idpSsoDescriptor)
        throws SAMLException {

        List<X509Certificate> certificates;

        try {
            certificates =
                idpSsoDescriptor
                    .getKeyDescriptors()
                    .stream()
                    .filter(x -> x.getUse() == UsageType.SIGNING)
                    .flatMap(CustomSamlClient::getDatasWithCertificates)
                    .map(CustomSamlClient::getFirstCertificate)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new SAMLException("Exception in getCertificates", e);
        }

        return certificates;
    }

    private static Stream<X509Data> getDatasWithCertificates(KeyDescriptor descriptor) {
        return descriptor
            .getKeyInfo()
            .getX509Datas()
            .stream()
            .filter(d -> d.getX509Certificates().size() > 0);
    }

    private static X509Certificate getFirstCertificate(X509Data data) {
        try {
            org.opensaml.xml.signature.X509Certificate cert =
                data.getX509Certificates().stream().findFirst().orElse(null);
            if (cert != null) {
                return KeyInfoHelper.getCertificate(cert);
            }
        } catch (CertificateException e) {
            logger.error("Exception in getFirstCertificate", e);
        }

        return null;
    }

    private static Credential getCredential(X509Certificate certificate) {
        BasicX509Credential credential = new BasicX509Credential();
        credential.setEntityCertificate(certificate);
        credential.setPublicKey(certificate.getPublicKey());
        credential.setCRLs(Collections.emptyList());
        return credential;
    }
}
