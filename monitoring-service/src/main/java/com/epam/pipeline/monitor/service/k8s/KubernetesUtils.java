/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.monitor.service.k8s;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.collections4.ListUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class KubernetesUtils {
    private static final int CONNECTION_TIMEOUT_MS = 2 * 1000;
    private static final String UNRESOLVED_POD = "unresolved pod";
    private static final String UNRESOLVED_NODE = "unresolved node";

    public static KubernetesClient getKubernetesClient() {
        return new DefaultKubernetesClient(new ConfigBuilder()
                .withConnectionTimeout(CONNECTION_TIMEOUT_MS)
                .build());
    }

    public static String getPodName(final Pod pod) {
        return Optional.ofNullable(pod)
                .map(Pod::getMetadata)
                .map(ObjectMeta::getName)
                .orElse(UNRESOLVED_POD);
    }

    public static String getNodeName(final Pod pod) {
        return Optional.ofNullable(pod)
                .map(Pod::getSpec)
                .map(PodSpec::getNodeName)
                .orElse(UNRESOLVED_NODE);
    }

    public static Optional<String> getPodIp(final Pod pod) {
        return Optional.ofNullable(pod)
                .map(Pod::getStatus)
                .map(PodStatus::getPodIP);
    }

    public static List<Node> findNodesByLabel(final KubernetesClient client, final String key,
                                              final Set<String> values) {
        return ListUtils.emptyIfNull(client.nodes()
                .withLabelIn(key, values.toArray(new String[0]))
                .list()
                .getItems());
    }

    public static List<Pod> findPodsByLabel(final KubernetesClient client, final String key, final String value,
                                            final String namespace) {
        return ListUtils.emptyIfNull(client.pods()
                .inNamespace(namespace)
                .withLabel(key, value)
                .list()
                .getItems());
    }

    private KubernetesUtils() {
        // no-op
    }
}
