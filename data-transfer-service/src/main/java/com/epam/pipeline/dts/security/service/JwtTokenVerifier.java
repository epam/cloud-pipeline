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

package com.epam.pipeline.dts.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.epam.pipeline.dts.security.exception.JwtInitializationException;
import com.epam.pipeline.dts.security.exception.TokenVerificationException;
import com.epam.pipeline.dts.security.model.JwtTokenClaims;
import org.apache.commons.lang3.StringUtils;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;

import static com.epam.pipeline.dts.security.model.JwtTokenClaims.CLAIM_GROUPS;
import static com.epam.pipeline.dts.security.model.JwtTokenClaims.CLAIM_ORG_UNIT_ID;
import static com.epam.pipeline.dts.security.model.JwtTokenClaims.CLAIM_ROLES;
import static com.epam.pipeline.dts.security.model.JwtTokenClaims.CLAIM_USER_ID;

public class JwtTokenVerifier {
    private RSAPublicKey publicKey;

    public JwtTokenVerifier(String publicKey) {
        try {
            this.publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new JwtInitializationException(e);
        }
    }

    public JwtTokenClaims readClaims(String jwtToken) {
        DecodedJWT decodedToken;

        try {
            decodedToken = JWT.require(Algorithm.RSA512(publicKey, null))
                    .build()
                    .verify(jwtToken);

        } catch (JWTVerificationException jve) {
            throw new TokenVerificationException(jve);
        }

        JwtTokenClaims tokenClaims = JwtTokenClaims.builder()
                .jwtTokenId(decodedToken.getId())
                .userName(decodedToken.getSubject())
                .userId(decodedToken.getClaim(CLAIM_USER_ID).asString())
                .orgUnitId(decodedToken.getClaim(CLAIM_ORG_UNIT_ID).asString())
                .roles(Arrays.asList(decodedToken.getClaim(CLAIM_ROLES).asArray(String.class)))
                .groups(Arrays.asList(decodedToken.getClaim(CLAIM_GROUPS).asArray(String.class)))
                .issuedAt(decodedToken.getIssuedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .expiresAt(decodedToken.getExpiresAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();

        return validateClaims(tokenClaims);
    }

    private JwtTokenClaims validateClaims(JwtTokenClaims tokenClaims) {
        if (StringUtils.isEmpty(tokenClaims.getJwtTokenId())) {
            throw new TokenVerificationException("Invalid token: token ID is empty");
        }

        if (StringUtils.isEmpty(tokenClaims.getUserId())) {
            throw new TokenVerificationException("Invalid token: user ID is empty");
        }

        if (StringUtils.isEmpty(tokenClaims.getUserName())) {
            throw new TokenVerificationException("Invalid token: user name is empty");
        }
        return tokenClaims;
    }
}
