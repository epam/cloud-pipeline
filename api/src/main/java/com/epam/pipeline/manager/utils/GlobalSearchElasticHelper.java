/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.utils;

import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.elasticsearch.ELKVersionedRestHighLevelClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

@Service
@Slf4j
public class GlobalSearchElasticHelper {

    private final PreferenceManager preferenceManager;
    private final String elasticsearchAuth;

    public GlobalSearchElasticHelper(final PreferenceManager preferenceManager,
                                     @Value("${elasticsearch.client.auth:#{null}}")
                                     final String elasticsearchAuth) {
        this.preferenceManager = preferenceManager;
        this.elasticsearchAuth = elasticsearchAuth;
    }

    public ELKVersionedRestHighLevelClient buildClient() {
        return new ELKVersionedRestHighLevelClient(buildLowLevelClientBuilder());
    }

    public ELKVersionedRestHighLevelClient buildBillingClient() {
        final Integer socketTimeout = preferenceManager.getPreference(
                SystemPreferences.SEARCH_ELASTIC_BILLING_SOCKET_TIMEOUT);
        final Integer retryTimeout = preferenceManager.getPreference(
                SystemPreferences.SEARCH_ELASTIC_BILLING_RETRY_TIMEOUT);
        return new ELKVersionedRestHighLevelClient(buildLowLevelClientBuilder(socketTimeout, retryTimeout));
    }

    public RestClientBuilder buildLowLevelClientBuilder() {
        final Integer socketTimeout = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SOCKET_TIMEOUT);
        return buildLowLevelClientBuilder(socketTimeout, null);
    }

    public RestClientBuilder buildLowLevelClientBuilder(final Integer socketTimeout, final Integer maxRetryTimeout) {
        final String host = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_HOST);
        final Integer port = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_PORT);
        final String schema = preferenceManager.getPreference(SystemPreferences.SEARCH_ELASTIC_SCHEME);

        Assert.isTrue(Objects.nonNull(host) && Objects.nonNull(port) && Objects.nonNull(schema),
                "One or more of the following parameters is not configured: "
                        + SystemPreferences.SEARCH_ELASTIC_HOST.getKey() + ", "
                        + SystemPreferences.SEARCH_ELASTIC_PORT.getKey() + ", "
                        + SystemPreferences.SEARCH_ELASTIC_SCHEME.getKey()
        );
        final RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, schema))
                .setRequestConfigCallback(requestConfigBuilder ->
                        requestConfigBuilder.setSocketTimeout(socketTimeout));
        if (Objects.nonNull(maxRetryTimeout)) {
            builder.setMaxRetryTimeoutMillis(maxRetryTimeout);
        }
        builder.setDefaultHeaders(getAuthHeaders());
        return builder;
    }

    private Header[] getAuthHeaders() {
        if (!StringUtils.isEmpty(elasticsearchAuth)) {
            final String encodedAuth = Base64.getEncoder()
                    .encodeToString(elasticsearchAuth.getBytes(StandardCharsets.UTF_8));
            return new Header[] {new BasicHeader("Authorization", String.format("Basic %s", encodedAuth))};
        }
        return new Header[0];
    }

}
