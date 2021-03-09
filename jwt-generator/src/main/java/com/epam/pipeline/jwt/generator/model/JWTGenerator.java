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

package com.epam.pipeline.jwt.generator.model;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static com.epam.pipeline.jwt.generator.model.JwtTokenClaims.CLAIM_GROUPS;
import static com.epam.pipeline.jwt.generator.model.JwtTokenClaims.CLAIM_ORG_UNIT_ID;
import static com.epam.pipeline.jwt.generator.model.JwtTokenClaims.CLAIM_ROLES;
import static com.epam.pipeline.jwt.generator.model.JwtTokenClaims.CLAIM_USER_ID;

public class JWTGenerator {
    private static final String PEM_PRIVATE_START = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";

    private RSAPrivateKey privateKey;

    public JWTGenerator(String privateKeyFile) {
        try {
            String privateKeyPem = new String(Files.readAllBytes(Paths.get(privateKeyFile)));
            privateKeyPem = privateKeyPem.replace(PEM_PRIVATE_START, "").replace(PEM_PRIVATE_END, "");
            privateKeyPem = privateKeyPem.replaceAll("\\s", "");
            byte[] pkcs8EncodedKey = Base64.getDecoder().decode(privateKeyPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8EncodedKey);
            this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String createToken(User user, long expiration) {
        Date expiresAt = toDate(LocalDateTime.now().plusSeconds(expiration));
        if (isContainsProblemY2038(expiresAt.toInstant().getEpochSecond())) {
            throw new IllegalArgumentException("Expiration date configured too far. Please configure expiration date before 19.01.2038");
        }
        JWTCreator.Builder tokenBuilder = buildToken(user.toClaims());
        tokenBuilder.withExpiresAt(expiresAt);
        return tokenBuilder.sign(Algorithm.RSA512(privateKey));
    }

    private boolean isContainsProblemY2038(long expiration) {
        return expiration > Integer.MAX_VALUE;
    }

    private JWTCreator.Builder buildToken(JwtTokenClaims claims) {
        JWTCreator.Builder tokenBuilder = JWT.create();
        tokenBuilder.withHeader(Collections.singletonMap("typ", "JWT"));
        tokenBuilder
                .withIssuedAt(new Date())
                .withJWTId(StringUtils.isEmpty(claims.getJwtTokenId()) ?
                        UUID.randomUUID().toString() : claims.getJwtTokenId())
                .withSubject(claims.getUserName())
                .withClaim(CLAIM_USER_ID, claims.getUserId())
                .withClaim(CLAIM_ORG_UNIT_ID, claims.getOrgUnitId())
                .withArrayClaim(CLAIM_GROUPS, claims.getGroups().toArray(new String[claims.getRoles().size()]))
                .withArrayClaim(CLAIM_ROLES, claims.getRoles().toArray(new String[claims.getRoles().size()]));
        return tokenBuilder;
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
