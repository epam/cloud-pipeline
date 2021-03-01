/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.KubernetesService;
import com.epam.pipeline.entity.pipeline.KubernetesServicePort;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils.getPipelineRun;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class PipelineRunKubernetesManagerTest {
    private static final String RUN_ID_LABEL_NAME = "run-id";
    private static final String SERVICE_NAME = "name";
    private static final String DEFAULT_NAMESPACE = "default";
    private static final Integer PORT1 = 8080;
    private static final Integer PORT2 = 1234;

    private final PipelineRun run = getPipelineRun(ID);

    private final PipelineRunCRUDService pipelineRunCRUDService = mock(PipelineRunCRUDService.class);
    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PipelineRunKubernetesManager pipelineRunKubernetesManager = new PipelineRunKubernetesManager(
            pipelineRunCRUDService, kubernetesManager, messageHelper, RUN_ID_LABEL_NAME, DEFAULT_NAMESPACE);

    @Test
    public void shouldCreateKubernetesService() {
        doReturn(run).when(pipelineRunCRUDService).loadRunById(anyLong());
        doReturn(new Pod()).when(kubernetesManager).findPodById(anyString());
        doReturn(service()).when(kubernetesManager).createService(anyString(), any(), any());

        final KubernetesService result = pipelineRunKubernetesManager
                .createKubernetesService(SERVICE_NAME, ID, buildKubernetesPorts());
        assertThat(result)
                .hasFieldOrPropertyWithValue("name", SERVICE_NAME)
                .hasFieldOrProperty("ports");
        assertThat(result.getPorts().stream().map(KubernetesServicePort::getPort).collect(Collectors.toSet()))
                .hasSize(2)
                .contains(PORT1, PORT2);

        final ArgumentCaptor<List<ServicePort>> portsCaptor = listCaptor();
        final ArgumentCaptor<Map<String, String>> mapCaptor = mapCaptor();
        verify(kubernetesManager).createService(any(), mapCaptor.capture(), portsCaptor.capture());
        final Map<String, String> resultLabels = mapCaptor.getValue();
        assertThat(resultLabels)
                .hasSize(1)
                .containsKey(RUN_ID_LABEL_NAME)
                .containsValue(String.valueOf(ID));
        final List<ServicePort> resultPorts = portsCaptor.getValue();
        assertThat(resultPorts.stream().map(ServicePort::getPort).collect(Collectors.toSet()))
                .hasSize(2)
                .contains(PORT1, PORT2);
        verify(pipelineRunCRUDService).updateKubernetesService(run, true);
    }

    @Test
    public void shouldFailCreationIfPodNotExists() {
        doReturn(run).when(pipelineRunCRUDService).loadRunById(anyLong());

        assertThrows(IllegalArgumentException.class, () -> pipelineRunKubernetesManager
                .createKubernetesService(SERVICE_NAME, ID, buildKubernetesPorts()));
    }

    private List<KubernetesServicePort> buildKubernetesPorts() {
        return Arrays.asList(KubernetesServicePort.builder().port(PORT1).build(),
                KubernetesServicePort.builder().port(PORT2).build());
    }

    private ArgumentCaptor listCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private ArgumentCaptor mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    private Service service() {
        final Service service = new Service();
        final ObjectMeta metadata = new ObjectMeta();
        metadata.setName(SERVICE_NAME);
        service.setMetadata(metadata);
        final ServiceSpec spec = new ServiceSpec();
        spec.setPorts(Arrays.asList(servicePort(PORT1), servicePort(PORT2)));
        service.setSpec(spec);
        return service;
    }

    private ServicePort servicePort(final Integer port) {
        final ServicePort servicePort = new ServicePort();
        servicePort.setPort(port);
        return servicePort;
    }
}
