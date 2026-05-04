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

public final class JwtUtils {

    private static final String PEM_PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String RSA = "RSA";

    private JwtUtils() {
    }

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
