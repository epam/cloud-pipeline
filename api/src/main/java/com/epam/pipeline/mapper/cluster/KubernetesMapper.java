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

package com.epam.pipeline.mapper.cluster;

import com.epam.pipeline.entity.cluster.ContainerInstance;
import com.epam.pipeline.entity.cluster.ContainerInstanceStatus;
import com.epam.pipeline.entity.cluster.CorePodInstance;
import com.epam.pipeline.entity.cluster.EventEntity;
import com.epam.pipeline.entity.cluster.PodInstanceStatus;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface KubernetesMapper {

    EventEntity mapEvent(Event event);

    default CorePodInstance mapCorePod(final Pod kubePod) {
        final CorePodInstance pod = new CorePodInstance(kubePod);
        final PodStatus podStatus = kubePod.getStatus();
        if (Objects.nonNull(podStatus)) {
            pod.setStatus(new PodInstanceStatus(podStatus));
            pod.setContainers(mapContainerInstances(podStatus, pod.getContainers()));
        }
        return pod;
    }

    static ContainerInstance mapContainerInstance(final ContainerStatus containerStatus,
                                                  final ContainerInstance containerInstance) {
        containerInstance.setRestartCount(containerStatus.getRestartCount());
        final ContainerState lastState = containerStatus.getLastState();
        if (Objects.nonNull(lastState)) {
            containerInstance.setLastRestartStatus(new ContainerInstanceStatus(lastState));
        }
        return containerInstance;
    }

    static List<ContainerInstance> mapContainerInstances(final PodStatus podStatus,
                                                         final List<ContainerInstance> containers) {
        if (CollectionUtils.isEmpty(containers)) {
            return null;
        }
        final Map<String, ContainerInstance> containersByName = containers.stream()
                .collect(Collectors.toMap(ContainerInstance::getName, Function.identity()));
        return podStatus.getContainerStatuses().stream()
                .map(containerStatus -> mapContainerInstance(containerStatus,
                        containersByName.get(containerStatus.getName())))
                .collect(Collectors.toList());
    }
}
