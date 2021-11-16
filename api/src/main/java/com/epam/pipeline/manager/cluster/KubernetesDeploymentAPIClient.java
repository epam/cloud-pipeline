/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Service
public class KubernetesDeploymentAPIClient {

    private final KubernetesDeploymentAPI kubernetesDeploymentAPI;

    public KubernetesDeploymentAPIClient(final @Value("${kube.deployment.api.url.prefix:apis/extensions/v1beta1}")
                                             String deploymentOpsUrPrefix) {
        this.kubernetesDeploymentAPI = buildRetrofitClient(deploymentOpsUrPrefix);
    }

    public Deployment updateDeployment(final String namespace, final String name) {
        return executeRequest(kubernetesDeploymentAPI.updateDeployment(namespace, name, getUpdateTriggeringPatch()));
    }

    private Map<String, Object> getUpdateTriggeringPatch() {
        return Collections.singletonMap(
            "spec",
            Collections.singletonMap(
                "template",
                Collections.singletonMap(
                    "metadata",
                    Collections.singletonMap(
                        "labels",
                        Collections.singletonMap(
                            "cp-updated",
                            LocalDateTime.now().format(KubernetesConstants.KUBE_LABEL_DATE_FORMATTER))))));
    }

    private <T> T executeRequest(final Call<T> request) {
        try {
            Response<T> response = request.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new IllegalStateException("Error in response from kube deployment API received: "
                                                + extractErrorMessage(response));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Request to kube deployment API failed: " + e.getMessage());
        }
    }

    private static String extractErrorMessage(final Response<?> response) throws IOException {
        return response.errorBody() == null ? "" : response.errorBody().string();
    }

    private KubernetesDeploymentAPI buildRetrofitClient(final String deploymentGroupUrlPrefix) {
        final Config kubeConfig = new Config();
        final OkHttpClient client = HttpClientUtils.createHttpClient(kubeConfig);
        final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(buildBaseUrlForDeploymentOperations(kubeConfig, deploymentGroupUrlPrefix))
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .client(client)
            .build();
        return retrofit.create(KubernetesDeploymentAPI.class);
    }

    private String buildBaseUrlForDeploymentOperations(final Config kubeConfig, final String deploymentGroupUrlPrefix) {
        return ProviderUtils.withTrailingDelimiter(kubeConfig.getMasterUrl())
               + ProviderUtils.withTrailingDelimiter(ProviderUtils.withoutLeadingDelimiter(deploymentGroupUrlPrefix));
    }
}
