/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.sync.service.impl;

import com.epam.pipeline.dts.security.model.JwtTokenClaims;
import com.epam.pipeline.dts.security.service.JwtTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiTokenService {

    @Autowired
    private JwtTokenVerifier tokenVerifier;

    @Value("${dts.api.token.path}")
    private String tokenPath;
    @Value("${dts.api.token.exp.days}")
    private String days;
    @Value("${dts.api.token}")
    private String apiToken;
    @Value("${dts.api.url}")
    private String api;

    public String getToken() {
        final String jwtRawToken = readTokenFromFile();
        return StringUtils.isBlank(jwtRawToken) ? apiToken : jwtRawToken;
    }

    public boolean isExpired() {
        final String jwtRawToken = getToken();
        final JwtTokenClaims claims = tokenVerifier.readClaims(jwtRawToken);
        return LocalDateTime.now().plusDays(Long.parseLong(days)).isBefore(claims.getExpiresAt());
    }

    public void updateToken(final String jwtRawToken) {
        if (StringUtils.isNotBlank(tokenPath)) {
            final File file = new File(tokenPath);
            try {
                final String jsonString = new JSONObject()
                        .put("access_key", jwtRawToken)
                        .put("api", api)
                        .put("tz", "local")
                        .put("proxy", "")
                        .put("proxy_ntlm", false)
                        .put("proxy_ntlm_pass", null)
                        .put("codec", null)
                        .put("proxy_ntlm_domain", null)
                        .put("proxy_ntlm_user", null)
                        .toString();
                FileUtils.writeStringToFile(file, jsonString, "UTF-8");
            } catch (IOException | JSONException e) {
                log.debug("Can't write JWT token to file {}", file.getName());
            }
        } else {
            log.debug("JWT token path must be specified.");
        }
    }

    private String readTokenFromFile() {
        if (StringUtils.isNotBlank(tokenPath)) {
            final File file = new File(tokenPath);
            if (file.exists()) {
                try {
                    final String jwtRawTokenJson = FileUtils.readFileToString(file, "UTF-8");
                    final JSONObject obj = new JSONObject(jwtRawTokenJson);
                    return obj.getString("access_key");
                } catch (IOException | JSONException e) {
                    log.debug("Can't read JWT token from file {}", file.getName());
                }
            }
        } else {
            log.debug("JWT token path must be specified.");
        }
        return null;
    }
}
