/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.kube;

import io.fabric8.kubernetes.api.model.networking.NetworkPolicy;
import org.springframework.stereotype.Service;

@Service
public class KubernetesAPIClient {

    private final KubernetesAPI api;

    public KubernetesAPIClient() {
        this.api = buildRetrofitClient();
    }

    public NetworkPolicy getNetworkPolicy(final String namespace, final String name) {
        return KubernetesAPIUtils.executeRequest(api.getNetworkPolicy(namespace, name));
    }

    public NetworkPolicy updateNetworkPolicy(final String namespace, final String name, final NetworkPolicy policy) {
        return KubernetesAPIUtils.executeRequest(api.updateNetworkPolicy(namespace, name, policy));
    }

    private KubernetesAPI buildRetrofitClient() {
        return KubernetesAPIUtils.buildRetrofit()
                .create(KubernetesAPI.class);
    }
}
