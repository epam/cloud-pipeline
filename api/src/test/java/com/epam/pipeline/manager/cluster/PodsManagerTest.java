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

import com.epam.pipeline.entity.cluster.ContainerInstance;
import com.epam.pipeline.entity.cluster.ContainerInstanceStatus;
import com.epam.pipeline.entity.cluster.EventEntity;
import com.epam.pipeline.entity.cluster.PodDescription;
import com.epam.pipeline.entity.cluster.PodInstance;
import com.epam.pipeline.entity.cluster.PodInstanceStatus;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.cluster.KubernetesMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.cluster.KubernetesCreatorUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PodsManagerTest {

    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final KubernetesMapper mapper = mock(KubernetesMapper.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final KubernetesClient kubernetesClient = mock(KubernetesClient.class);
    private final PodsManager manager = new PodsManager(kubernetesManager, mapper, preferenceManager);

    @Before
    public void setUp() {
        doReturn(CORE_LABEL).when(preferenceManager).getPreference(SystemPreferences.CLUSTER_KUBE_CORE_COMPONENT_LABEL);
    }

    @Test
    public void shouldLoadCoreComponents() {
        final Pod pod1 = pod(COMPONENT1);
        final Pod pod2 = pod(COMPONENT2);
        doReturn(Arrays.asList(pod1, pod2)).when(kubernetesManager).getPodsByLabel(CORE_LABEL);

        final List<PodInstance> corePods = manager.getCorePods();
        assertThat(corePods).hasSize(2);
        final Map<String, PodInstance> byParentName = corePods.stream()
                .collect(Collectors.toMap(PodInstance::getParentName, Function.identity()));
        assertCorePod(byParentName.get(COMPONENT1), COMPONENT1);
        assertCorePod(byParentName.get(COMPONENT2), COMPONENT2);
    }

    @Test
    public void shouldLoadShortPodDescription() {
        doReturn(kubernetesClient).when(kubernetesManager).getKubernetesClient();
        doReturn(Collections.singletonList(event())).when(kubernetesManager)
                .getEvents(kubernetesClient, NAME1);
        final EventEntity event = new EventEntity();
        doReturn(event).when(mapper).mapEvent(any());

        final PodDescription podDescription = manager.describePod(NAME1, false);
        assertThat(podDescription.getEvents()).hasSize(1);
        assertThat(podDescription.getDescription()).isNull();
        verify(kubernetesManager, never()).findPodById(any(), any());
    }

    @Test
    public void shouldLoadLongPodDescription() {
        doReturn(kubernetesClient).when(kubernetesManager).getKubernetesClient();
        doReturn(Collections.singletonList(event())).when(kubernetesManager)
                .getEvents(kubernetesClient, NAME1);
        final EventEntity event = new EventEntity();
        doReturn(event).when(mapper).mapEvent(any());
        doReturn(pod(NAME1)).when(kubernetesManager).findPodById(kubernetesClient, NAME1);

        final PodDescription podDescription = manager.describePod(NAME1, true);
        assertThat(podDescription.getEvents()).hasSize(1);
        assertThat(podDescription.getDescription()).isNotBlank();
    }

    private static void assertCorePod(final PodInstance pod, final String name) {
        assertEquals(pod.getParentName(), name);
        assertEquals(pod.getParentType(), TYPE);
        assertThat(pod).isNotNull();
        assertEquals(pod.getName(), name);
        assertEquals(pod.getNamespace(), DEFAULT);
        assertEquals(pod.getPhase(), PHASE);

        final PodInstanceStatus status = pod.getStatus();
        assertThat(status).isNotNull();
        assertEquals(status.getTimestamp(), TIMESTAMP);

        final List<ContainerInstance> containers = pod.getContainers();
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
