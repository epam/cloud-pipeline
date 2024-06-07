/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.CorePodInstance;
import com.epam.pipeline.entity.cluster.EventEntity;
import com.epam.pipeline.entity.cluster.PodDescription;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.cluster.KubernetesMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PodsManager {

    private final KubernetesManager kubernetesManager;
    private final KubernetesMapper mapper;
    private final PreferenceManager preferenceManager;

    public List<CorePodInstance> getCorePods() {
        final String labelName = preferenceManager.getPreference(SystemPreferences.CLUSTER_KUBE_CORE_COMPONENT_LABEL);
        return kubernetesManager.getPodsByLabel(labelName).stream()
                .map(pod -> buildCorePod(pod, labelName))
                .collect(Collectors.toList());
    }

    public PodDescription describePod(final String podId, final boolean detailed) {
        try (KubernetesClient client = kubernetesManager.getKubernetesClient()) {
            final List<EventEntity> events = kubernetesManager.getPodEvents(client, podId).stream()
                    .map(mapper::mapEvent)
                    .collect(Collectors.toList());
            final PodDescription podDescription = PodDescription.builder()
                    .events(events)
                    .build();
            if (!detailed) {
                return podDescription;
            }
            final Pod pod = kubernetesManager.findPodById(client, podId);
            try {
                podDescription.setDescription(new JsonMapper().writeValueAsString(pod));
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
            return podDescription;
        } catch (KubernetesClientException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public String getContainerLogs(final String podId, final String containerId, final Integer limit) {
        final int linesCount = Optional.ofNullable(limit)
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.SYSTEM_LIMIT_LOG_LINES));
        return kubernetesManager.getPodContainerLogs(podId, containerId, linesCount);
    }

    private CorePodInstance buildCorePod(final Pod kubePod, final String coreLabel) {
        final CorePodInstance pod = mapper.mapCorePod(kubePod);
        final ObjectMeta metadata = kubePod.getMetadata();
        if (Objects.nonNull(metadata)) {
            final Map<String, String> labels = MapUtils.emptyIfNull(kubePod.getMetadata().getLabels());
            labels.forEach((labelName, labelValue) -> resolveCorePodParent(pod, labelName, labelValue, coreLabel));
        }
        return pod;
    }

    private void resolveCorePodParent(final CorePodInstance pod, final String labelName, final String labelValue,
                                      final String coreLabel) {
        if (coreLabel.equals(labelName)
                && !KubernetesConstants.TRUE_LABEL_VALUE.equals(labelValue.toLowerCase(Locale.ROOT))) {
            pod.setParentType(labelValue);
        }
        if (labelName.startsWith(KubernetesConstants.CORE_COMPONENT_PREFIX)) {
            pod.setParentName(labelName.replace(KubernetesConstants.CP_LABEL_PREFIX, Strings.EMPTY));
        }
    }
}
