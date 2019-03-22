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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
public class GoogleWebClientCredentials implements FirecloudCredentials  {

    @JsonProperty(value = "client_id")
    private String clientId;
    @JsonProperty(value = "client_secret")
    private String clientSecret;
    @JsonProperty(value = "project_id")
    private String projectId;
    @JsonProperty(value = "auth_uri")
    private String authUri;
    @JsonProperty(value = "token_uri")
    private String tokenUri;
    @JsonProperty(value = "auth_provider_x509_cert_url")
    private String authProviderCertUrl;
    @JsonProperty(value = "redirect_uris")
    private List<String> redirectUrls;
    @JsonProperty(value = "javascript_origins")
    private List<String> javascriptOrigins;

    // Lombok is not used because of compatibility problems with Jackson
    public GoogleWebClientCredentials(String clientId,
                                      String clientSecret,
                                      String projectId,
                                      String authUri,
                                      String tokenUri,
                                      String authProviderCertUrl,
                                      List<String> redirectUrls,
                                      List<String> javascriptOrigins,
                                      String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.projectId = projectId;
        this.authUri = authUri;
        this.tokenUri = tokenUri;
        this.authProviderCertUrl = authProviderCertUrl;
        this.redirectUrls = Collections.unmodifiableList(redirectUrls);
        this.javascriptOrigins = Collections.unmodifiableList(javascriptOrigins);
        this.refreshToken = refreshToken;
    }

    @JsonIgnore
    private String refreshToken;

    public GoogleWebClientCredentials withRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    @Data
    @NoArgsConstructor
    public static class CredentialsWrapper {

        @JsonProperty(value = "web")
        private GoogleWebClientCredentials webCredentials;
    }
}
