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

package com.epam.pipeline.manager.google;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.google.RefreshToken;
import com.epam.pipeline.exception.GoogleAccessException;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.JsonService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class GoogleCredentialsManager implements CredentialsManager {

    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final String ACCESS_TYPE = "offline";

    private MessageHelper messageHelper;
    private PreferenceManager preferenceManager;
    private JsonService jsonService;

    @Override
    public AccessToken getAccessToken(String refreshToken) {
        try {
            GoogleWebClientCredentials webClientCredentials = getWebClientCredentials();
            return UserCredentials.newBuilder()
                    .setClientId(webClientCredentials.getClientId())
                    .setClientSecret(webClientCredentials.getClientSecret())
                    .setRefreshToken(refreshToken)
                    .build()
                    .refreshAccessToken();
        } catch (IOException e) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_CREDENTIALS, e.getMessage()));
            throw new GoogleAccessException(e.getMessage(), e);
        }
    }

    @Override
    public AccessToken getDefaultToken() {
        try {
            return getDefaultCredentials().refreshAccessToken();
        } catch (IOException e) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_CREDENTIALS, e.getMessage()));
            throw new GoogleAccessException(e.getMessage(), e);
        }
    }

    @Override
    public FirecloudCredentials getFirecloudCredentials(String refreshToken) {
        return StringUtils.hasText(refreshToken) ?
                getWebClientCredentials().withRefreshToken(refreshToken) :
                new FirecloudDefaultCredentials(getDefaultCredentials());
    }

    @Override
    public UserCredentials getDefaultCredentials() {
        try {
            return (UserCredentials)UserCredentials.getApplicationDefault();
        } catch (IOException e) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_CREDENTIALS, e.getMessage()));
            throw new GoogleAccessException(e.getMessage(), e);
        }
    }

    @Override
    public RefreshToken issueTokenFromAuthCode(String authorizationCode) {
        List<String> scopes = preferenceManager.getPreference(SystemPreferences.FIRECLOUD_SCOPES);
        Assert.notNull(scopes, messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_SCOPES_MISSING));
        String redirectUrl = preferenceManager.getPreference(SystemPreferences.GOOGLE_REDIRECT_URL);
        Assert.notNull(redirectUrl, messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_REDIRECT_URL_MISSING));

        GoogleWebClientCredentials webClientCredentials = getWebClientCredentials();

        GoogleAuthorizationCodeFlow authorizationFlow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT,
                new JacksonFactory(),
                webClientCredentials.getClientId(),
                webClientCredentials.getClientSecret(),
                scopes)
                .setAccessType(ACCESS_TYPE)
                .build();

        try {
            GoogleTokenResponse tokenResponse = authorizationFlow.newTokenRequest(authorizationCode)
                    .setRedirectUri(redirectUrl)
                    .execute();
            return new RefreshToken(tokenResponse.getRefreshToken());
        } catch (IOException e) {
            log.error(messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_CREDENTIALS, e.getMessage()));
            throw new GoogleAccessException(e.getMessage(), e);
        }
    }

    private GoogleWebClientCredentials getWebClientCredentials() {
        String secretJson = preferenceManager.getPreference(SystemPreferences.GOOGLE_CLIENT_SETTINGS);
        Assert.notNull(secretJson, messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_SECRET_MISSING));
        GoogleWebClientCredentials.CredentialsWrapper wrapper = jsonService.parseJsonFromFile(secretJson,
                new TypeReference<GoogleWebClientCredentials.CredentialsWrapper>() {});
        Assert.notNull(wrapper, messageHelper.getMessage(MessageConstants.ERROR_GOOGLE_INVALID_SECRET_JSON));
        Assert.notNull(wrapper.getWebCredentials(), messageHelper.getMessage(
                MessageConstants.ERROR_GOOGLE_INVALID_SECRET_JSON));
        return wrapper.getWebCredentials();
    }
}
