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

import com.epam.pipeline.manager.kube.KubernetesAPIUtils;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Service
public class KubernetesDeploymentAPIClient {

    private final KubernetesDeploymentAPI kubernetesDeploymentAPI;

    public KubernetesDeploymentAPIClient(final @Value("${kube.deployment.api.url.prefix:apis/apps/v1}")
                                             String deploymentOperationsUrlPrefix) {
        this.kubernetesDeploymentAPI = buildRetrofitClient(deploymentOperationsUrlPrefix);
    }

    public Deployment updateDeployment(final String namespace, final String name) {
        return KubernetesAPIUtils.executeRequest(kubernetesDeploymentAPI
                .updateDeployment(namespace, name, getUpdateTriggeringPatch()));
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

    private KubernetesDeploymentAPI buildRetrofitClient(final String deploymentGroupUrlPrefix) {
        return KubernetesAPIUtils.buildRetrofit(deploymentGroupUrlPrefix)
                .create(KubernetesDeploymentAPI.class);
    }
}
