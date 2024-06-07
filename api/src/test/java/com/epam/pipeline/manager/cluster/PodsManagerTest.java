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

import com.epam.pipeline.entity.cluster.CorePodInstance;
import com.epam.pipeline.entity.cluster.EventEntity;
import com.epam.pipeline.entity.cluster.PodDescription;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.mapper.cluster.KubernetesMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.cluster.PodCreatorUtils.*;
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
        doReturn(new CorePodInstance(pod1)).when(mapper).mapCorePod(pod1);
        doReturn(new CorePodInstance(pod2)).when(mapper).mapCorePod(pod2);

        final List<CorePodInstance> corePods = manager.getCorePods();
        assertThat(corePods).hasSize(2);
        final Map<String, CorePodInstance> byParentName = corePods.stream()
                .collect(Collectors.toMap(CorePodInstance::getParentName, Function.identity()));
        assertCorePod(byParentName.get(COMPONENT1), COMPONENT1);
        assertCorePod(byParentName.get(COMPONENT2), COMPONENT2);
    }

    @Test
    public void shouldLoadShortPodDescription() {
        doReturn(kubernetesClient).when(kubernetesManager).getKubernetesClient();
        doReturn(Collections.singletonList(event())).when(kubernetesManager)
                .getPodEvents(kubernetesClient, NAME1);
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
                .getPodEvents(kubernetesClient, NAME1);
        final EventEntity event = new EventEntity();
        doReturn(event).when(mapper).mapEvent(any());
        doReturn(pod(NAME1)).when(kubernetesManager).findPodById(kubernetesClient, NAME1);

        final PodDescription podDescription = manager.describePod(NAME1, true);
        assertThat(podDescription.getEvents()).hasSize(1);
        assertThat(podDescription.getDescription()).isNotBlank();
    }

    private static void assertCorePod(final CorePodInstance pod, final String name) {
        assertEquals(pod.getParentName(), name);
        assertEquals(pod.getParentType(), TYPE);
    }
}
