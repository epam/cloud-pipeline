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
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.cluster.PodCreatorUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class KubernetesMapperTest {

    private final KubernetesMapper mapper = Mappers.getMapper(KubernetesMapper.class);

    @Test
    public void shouldMapEvents() {
        final Event kubeEvent = event();
        final EventEntity eventEntity = mapper.mapEvent(kubeEvent);

        assertThat(eventEntity).isEqualTo(eventEntity());
    }

    @Test
    public void shouldMapCorePod() {
        final Pod kubePod = pod(NAME1);
        final CorePodInstance corePod = mapper.mapCorePod(kubePod);

        assertThat(corePod).isNotNull();
        assertEquals(corePod.getName(), NAME1);
        assertEquals(corePod.getNamespace(), DEFAULT);
        assertEquals(corePod.getPhase(), PHASE);

        final PodInstanceStatus status = corePod.getStatus();
        assertThat(status).isNotNull();
        assertEquals(status.getTimestamp(), TIMESTAMP);

        final List<ContainerInstance> containers = corePod.getContainers();
        assertThat(containers).hasSize(2);
        final Map<String, ContainerInstance> byName = containers.stream()
                .collect(Collectors.toMap(ContainerInstance::getName, Function.identity()));
        assertContainer(byName.get(NAME1), NAME1);
        assertContainer(byName.get(NAME2), NAME2);
    }

    private static void assertContainer(final ContainerInstance container, final String name) {
        assertEquals(container.getName(), name);
        assertEquals(container.getRestartCount(), RESTARTS_COUNT);

        final ContainerInstanceStatus status = container.getStatus();
        assertThat(status).isNotNull();
        assertEquals(status.getStatus(), "Running");

        final ContainerInstanceStatus lastRestart = container.getLastRestartStatus();
        assertThat(lastRestart).isNotNull();
        assertEquals(lastRestart.getStatus(), "Terminated");
    }
}
