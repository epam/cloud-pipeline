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

package com.epam.pipeline.test.creator.cluster;

import com.epam.pipeline.entity.cluster.EventEntity;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PodCreatorUtils {
    public static final String CORE_LABEL = "core";
    public static final String COMPONENT1 = "cp-component-1";
    public static final String COMPONENT2 = "cp-component-2";
    public static final String TEST_UUID = UUID.randomUUID().toString();
    public static final String DEFAULT = "default";
    public static final String NAME1 = "name-1";
    public static final String NAME2 = "name-2";
    public static final String PHASE = "Running";
    public static final Integer RESTARTS_COUNT = 1;
    public static final String TIMESTAMP = "2023-03-09T14:47:06Z";
    public static final String TYPE = "Deployment";
    public static final String TEST = "test";

    private PodCreatorUtils() {
        // no-op
    }

    public static ContainerState runningState() {
        final ContainerState state = new ContainerState();
        state.setRunning(new ContainerStateRunning());
        return state;
    }

    public static ContainerState terminatedState() {
        final ContainerState state = new ContainerState();
        state.setTerminated(new ContainerStateTerminated());
        return state;
    }

    public static ContainerStatus containerStatus(final String name) {
        final ContainerStatus containerStatus = new ContainerStatus();
        containerStatus.setName(name);
        containerStatus.setState(runningState());
        containerStatus.setRestartCount(RESTARTS_COUNT);
        containerStatus.setLastState(terminatedState());
        return containerStatus;
    }

    public static PodStatus podStatus(final List<ContainerStatus> containerStatuses) {
        final PodStatus podStatus = new PodStatus();
        podStatus.setPhase(PHASE);
        podStatus.setContainerStatuses(containerStatuses);
        podStatus.setStartTime(TIMESTAMP);
        return podStatus;
    }

    public static ObjectMeta objectMeta(final String name) {
        final Map<String, String> labels = new HashMap<>();
        labels.put("cloud-pipeline/" + name, "true");
        labels.put(CORE_LABEL, TYPE);

        final ObjectMeta objectMeta = new ObjectMeta();
        objectMeta.setLabels(labels);
        objectMeta.setUid(TEST_UUID);
        objectMeta.setName(name);
        objectMeta.setNamespace(DEFAULT);
        return objectMeta;
    }

    public static Container container(final String name) {
        final Container container = new Container();
        container.setName(name);
        return container;
    }

    public static Event event() {
        final Event event = new Event();
        event.setKind("Event");
        event.setMessage(TEST);
        event.setReason(TEST);
        event.setType(TEST);
        event.setAction(TEST);
        event.setMetadata(objectMeta(TEST));
        return event;
    }

    public static EventEntity eventEntity() {
        return EventEntity.builder()
                .reason(TEST)
                .message(TEST)
                .type(TEST)
                .build();
    }

    public static Pod pod(final String name) {
        final List<ContainerStatus> containerStatuses = Arrays.asList(containerStatus(NAME1), containerStatus(NAME2));

        final PodSpec podSpec = new PodSpec();
        podSpec.setContainers(Arrays.asList(container(NAME1), container(NAME2)));

        final Pod pod = new Pod();
        pod.setMetadata(objectMeta(name));
        pod.setStatus(podStatus(containerStatuses));
        pod.setSpec(podSpec);
        return pod;
    }
}
