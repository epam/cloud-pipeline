/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.util.KubernetesTestUtils;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class KubernetesManagerContainerIdTest {

    private static final String NAMESPACE = "default";
    private static final String POD_ID = "pipeline-1";
    private static final String CONTAINER_ID = "container-id";
    private static final String TAG_IMAGE = "registry:443/group/tool:latest";
    private static final String DIGEST_IMAGE = "registry:443/group/tool@sha256:digest";
    private static final String ANOTHER_IMAGE = "registry:443/group/another:latest";
    private static final String ANOTHER_NAME = "sidecar";

    private final KubernetesManager kubernetesManager = spy(new KubernetesManager());
    private final KubernetesClient client = mock(KubernetesClient.class);

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(kubernetesManager, "kubeNamespace", NAMESPACE);
        doReturn(client).when(kubernetesManager).getKubernetesClient();
    }

    @Test
    public void shouldFindContainerOfPodPinnedToDigestByName() {
        mockPod(containerStatus(KubernetesConstants.PIPELINE_CONTAINER_NAME, DIGEST_IMAGE));

        assertEquals(CONTAINER_ID, kubernetesManager.getContainerIdFromKubernetesPod(POD_ID, TAG_IMAGE));
    }

    @Test
    public void shouldFindContainerOfPodWithTagByName() {
        mockPod(containerStatus(KubernetesConstants.PIPELINE_CONTAINER_NAME, TAG_IMAGE));

        assertEquals(CONTAINER_ID, kubernetesManager.getContainerIdFromKubernetesPod(POD_ID, TAG_IMAGE));
    }

    @Test
    public void shouldFindContainerWithAnotherNameByImage() {
        mockPod(containerStatus(ANOTHER_NAME, TAG_IMAGE));

        assertEquals(CONTAINER_ID, kubernetesManager.getContainerIdFromKubernetesPod(POD_ID, TAG_IMAGE));
    }

    @Test
    public void shouldNotFindContainerWithAnotherNameAndImage() {
        mockPod(containerStatus(ANOTHER_NAME, ANOTHER_IMAGE));

        assertNull(kubernetesManager.getContainerIdFromKubernetesPod(POD_ID, TAG_IMAGE));
    }

    private ContainerStatus containerStatus(final String name, final String image) {
        return new ContainerStatusBuilder()
                .withName(name)
                .withImage(image)
                .withContainerID("docker://" + CONTAINER_ID)
                .build();
    }

    private void mockPod(final ContainerStatus containerStatus) {
        final Pod pod = new PodBuilder()
                .withNewStatus()
                .withContainerStatuses(containerStatus)
                .endStatus()
                .build();
        final MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> pods =
                new KubernetesTestUtils.MockPods()
                        .mockNamespace(NAMESPACE)
                        .mockWithName(POD_ID)
                        .mockPod(pod)
                        .and()
                        .getMockedEntity();
        doReturn(pods).when(client).pods();
    }
}
