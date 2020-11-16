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

package com.epam.pipeline.security.jwt;

import static com.epam.pipeline.entity.security.JwtTokenClaims.CLAIM_GROUPS;
import static com.epam.pipeline.entity.security.JwtTokenClaims.CLAIM_ORG_UNIT_ID;
import static com.epam.pipeline.entity.security.JwtTokenClaims.CLAIM_ROLES;
import static com.epam.pipeline.entity.security.JwtTokenClaims.CLAIM_EXTERNAL;
import static com.epam.pipeline.entity.security.JwtTokenClaims.CLAIM_USER_ID;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.epam.pipeline.entity.security.JwtTokenClaims;
import com.epam.pipeline.manager.docker.DockerRegistryClaim;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenGenerator {
    @Autowired
    private PreferenceManager preferenceManager;

    public static final int FINGERPRINT_LENGTH = 30;
    @Value("${jwt.key.private}")
    private String privateKeyString;

    @Value("${jwt.key.public}")
    private String publicKeyString;

    private RSAPrivateKey privateKey;

    private RSAPublicKey publicKey;

    @PostConstruct
    public void initKey() {
        try {
            this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString)));
            this.publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyString)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new JwtInitializationException(e);
        }
    }

    public String encodeToken(JwtTokenClaims claims, Long expirationSeconds) {
        long jwtExpirationSeconds = preferenceManager.getPreference(SystemPreferences.LAUNCH_JWT_TOKEN_EXPIRATION);
        Long expiration = expirationSeconds == null ? jwtExpirationSeconds : expirationSeconds;
        Date expiresAt = toDate(LocalDateTime.now().plusSeconds(expiration));
        if (isContainsProblemY2038(expiresAt.toInstant().getEpochSecond())) {
            throw new IllegalArgumentException("Expiration date configured too far. Please configure expiration date before 19.01.2038");
        }
        JWTCreator.Builder tokenBuilder = buildToken(claims);
        tokenBuilder.withExpiresAt(expiresAt);
        return tokenBuilder.sign(Algorithm.RSA512(privateKey));
    }

    /**
     * Generates JWT token for Docker registry authentication according to the documentation:
     * https://docs.docker.com/registry/spec/auth/jwt/#getting-a-bearer-token
     * @param claims    authenticated user
     * @param expirationSeconds fro token
     * @param service   docker registry id
     * @param dockerRegistryClaims  requested changes, may be empty for 'login' requests
     * @return valid JWT token
     */
    public String issueDockerToken(JwtTokenClaims claims, Long expirationSeconds, String service,
            List<DockerRegistryClaim> dockerRegistryClaims) {
        long jwtExpirationSeconds = preferenceManager.getPreference(SystemPreferences.LAUNCH_JWT_TOKEN_EXPIRATION);
        Long expiration = expirationSeconds == null ? jwtExpirationSeconds : expirationSeconds;
        Date expiresAt = toDate(LocalDateTime.now().plusSeconds(expiration));
        if (isContainsProblemY2038(expiresAt.toInstant().getEpochSecond())) {
            throw new IllegalArgumentException("Expiration date configured too far. Please configure expiration date before 19.01.2038");
        }
        JwtTokenDockerCreator.Builder tokenBuilder = buildDockerToken(claims, service, dockerRegistryClaims);
        tokenBuilder.withExpiresAt(expiresAt);
        return tokenBuilder.sign(Algorithm.RSA512(privateKey));
    }

    private JwtTokenDockerCreator.Builder buildDockerToken(JwtTokenClaims claims, String service,
            List<DockerRegistryClaim> dockerRegistryClaims) {
        JwtTokenDockerCreator.Builder tokenBuilder = new JwtTokenDockerCreator.Builder();
        Map<String, Object> header = new HashMap<>();
        header.put("typ", "JWT");
        header.put("alg",  publicKey.getAlgorithm());
        header.put("kid", getKeyFingerPrint());
        tokenBuilder.withHeader(header);
        tokenBuilder
                .withIssuedAt(new Date())
                .withJWTId(Strings.isNullOrEmpty(claims.getJwtTokenId()) ?
                        UUID.randomUUID().toString() : claims.getJwtTokenId())
                .withIssuer("Cloud pipeline")
                .withAudience(service)
                .withSubject(claims.getUserName());
        if (CollectionUtils.isNotEmpty(dockerRegistryClaims)) {
            tokenBuilder.withObjectClaim("access", dockerRegistryClaims);
        }
        return tokenBuilder;
    }

    private boolean isContainsProblemY2038(long expiration) {
        return expiration > Integer.MAX_VALUE;
    }

    /*
    * The “kid” field has to be in a libtrust fingerprint compatible format.
    * Such a format can be generated by following steps:
    * Take the DER encoded public key which the JWT token was signed against.
    * Create a SHA256 hash out of it and truncate to 240bits.
    * Split the result into 12 base32 encoded groups with : as delimiter.
    * */

    private String getKeyFingerPrint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyHash = digest.digest(publicKey.getEncoded());
            byte[] truncated = new byte[FINGERPRINT_LENGTH];
            if (keyHash.length > FINGERPRINT_LENGTH) {
                System.arraycopy(keyHash, 0, truncated, 0, FINGERPRINT_LENGTH);
            } else {
                truncated = keyHash;
            }
            String encoded = BaseEncoding.base32().encode(truncated);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < encoded.length(); i++) {
                if (i != 0 && i % 4 == 0) {
                    result.append(':');
                }
                result.append(encoded.charAt(i));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private JWTCreator.Builder buildToken(JwtTokenClaims claims) {
        JWTCreator.Builder tokenBuilder = JWT.create();
        tokenBuilder.withHeader(ImmutableMap.of("typ", "JWT"));
        tokenBuilder
                .withIssuedAt(new Date())
                .withJWTId(Strings.isNullOrEmpty(claims.getJwtTokenId()) ?
                        UUID.randomUUID().toString() : claims.getJwtTokenId())
                .withSubject(claims.getUserName())
                .withClaim(CLAIM_USER_ID, claims.getUserId())
                .withClaim(CLAIM_ORG_UNIT_ID, claims.getOrgUnitId())
                .withArrayClaim(CLAIM_GROUPS, claims.getGroups().toArray(new String[claims.getGroups().size()]))
                .withArrayClaim(CLAIM_ROLES, claims.getRoles().toArray(new String[claims.getRoles().size()]));

        if (claims.isExternal()) {
            tokenBuilder.withClaim(CLAIM_EXTERNAL, claims.isExternal());
        }

        return tokenBuilder;
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }


}
