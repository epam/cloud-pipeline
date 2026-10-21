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

package com.epam.pipeline.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import joptsimple.internal.Strings;
import org.bouncycastle.asn1.ASN1Sequence;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Utility class for JWT generation.
 * <p>
 * This class provides functionality to generate RSA-256 signed JWT tokens using private keys
 * in either PKCS#8 or PKCS#1 PEM format. It supports reading private keys from the file system
 * and creating JWT tokens with configurable issuer and time-to-live settings.
 * </p>
 *
 * <h2>Supported Key Formats:</h2>
 * <ul>
 *   <li>PKCS#8 format: {@code -----BEGIN PRIVATE KEY-----}</li>
 *   <li>PKCS#1 format: {@code -----BEGIN RSA PRIVATE KEY-----}</li>
 * </ul>
 */
public final class JwtUtils {

    private static final String PEM_PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String RSA = "RSA";

    private JwtUtils() {
    }

    /**
     * Generates a JWT token signed with RSA-256 algorithm using a private key from a PEM file.
     * <p>
     * This method creates a JWT with the following claims:
     * </p>
     * <ul>
     *   <li><strong>iss</strong> (Issuer): The entity that issued the token</li>
     *   <li><strong>iat</strong> (Issued At): The timestamp when the token was created</li>
     *   <li><strong>exp</strong> (Expiration Time): The timestamp when the token expires</li>
     * </ul>
     * <p>
     * The private key file must be in PEM format and can be either PKCS#8 or PKCS#1 encoded.
     * The method automatically detects the format based on the PEM header.
     * </p>
     *
     * @param privateKeyPath the file system path to the RSA private key in PEM format.
     *                       Must not be null or empty, and the file must exist.
     * @param issuer         the issuer claim (iss) to include in the JWT.
     *                       Typically identifies the principal that issued the token.
     * @param ttlSeconds     the time-to-live in seconds. The token will expire
     *                       this many seconds after the current time.
     *                       Must be a positive value.
     * @return a signed JWT token as a compact, URL-safe string in the format:
     * {@code header.payload.signature}
     * @throws IOException              if the private key file cannot be read or does not exist
     * @throws NoSuchAlgorithmException if the RSA algorithm is not available in the JVM
     * @throws InvalidKeySpecException  if the private key format is invalid or cannot be parsed
     * @throws IllegalArgumentException if privateKeyPath is null/empty or the file doesn't exist
     */
    public static String generateRsa256Jwt(final String privateKeyPath,
                                           final String issuer,
                                           final long ttlSeconds)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        final String pem = readPrivateKeyPem(privateKeyPath);
        final RSAPrivateKey rsa = parseRsaPrivateKey(pem);

        final long nowSeconds = Instant.now().getEpochSecond();
        final Date issuedAt = Date.from(Instant.ofEpochSecond(nowSeconds));
        final Date expiresAt = Date.from(Instant.ofEpochSecond(nowSeconds + ttlSeconds));

        return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(Algorithm.RSA256(rsa));
    }

    private static String readPrivateKeyPem(final String pathToPem) throws IOException {
        Assert.isTrue(!Strings.isNullOrEmpty(pathToPem),
                "Private key file path must be provided to generate JWT.");
        final File file = new File(pathToPem);
        Assert.isTrue(file.isFile() && file.exists(),
                "Private key file must be provided to generate JWT.");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static byte[] normalizeKey(final String rawKey, final String header, final String footer) {
        final String normalized = rawKey.replace(header, Strings.EMPTY)
                .replace(footer, Strings.EMPTY)
                .replaceAll("\\s", Strings.EMPTY);
        return Base64.getDecoder().decode(normalized);
    }

    private static KeySpec getPKCS8KeySpec(final String rawKey) {
        final byte[] normalized = normalizeKey(rawKey, PEM_PKCS8_HEADER, PEM_PKCS8_FOOTER);
        return new PKCS8EncodedKeySpec(normalized);
    }

    private static KeySpec getPKCS1KeySpec(final String rawKey) {
        final byte[] normalized = normalizeKey(rawKey, PEM_PKCS1_HEADER, PEM_PKCS1_FOOTER);

        final ASN1Sequence asn1Sequence = ASN1Sequence.getInstance(normalized);
        final org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaPrivateKey =
                org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(asn1Sequence);

        return new RSAPrivateCrtKeySpec(
                rsaPrivateKey.getModulus(),
                rsaPrivateKey.getPublicExponent(),
                rsaPrivateKey.getPrivateExponent(),
                rsaPrivateKey.getPrime1(),
                rsaPrivateKey.getPrime2(),
                rsaPrivateKey.getExponent1(),
                rsaPrivateKey.getExponent2(),
                rsaPrivateKey.getCoefficient()
        );
    }

    private static RSAPrivateKey parseRsaPrivateKey(final String pem)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        final KeySpec spec = pem.startsWith(PEM_PKCS8_HEADER) ? getPKCS8KeySpec(pem) : getPKCS1KeySpec(pem);
        final KeyFactory keyFactory = KeyFactory.getInstance(RSA);
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }
}
