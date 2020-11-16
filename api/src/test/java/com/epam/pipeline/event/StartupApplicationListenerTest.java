/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.event;

import com.epam.pipeline.manager.cluster.KubernetesManager;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.pipeline.PipelineRunDockerOperationManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StartupApplicationListenerTest {

    private final DockerRegistryManager dockerRegistryManager = mock(DockerRegistryManager.class);
    private final CloudRegionManager cloudRegionManager = mock(CloudRegionManager.class);
    private final KubernetesManager kubernetesManager = mock(KubernetesManager.class);
    private final PipelineRunDockerOperationManager pipelineRunDockerOperationManager =
            mock(PipelineRunDockerOperationManager.class);
    private final ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);

    private final StartupApplicationListener listener = new StartupApplicationListener(dockerRegistryManager,
            cloudRegionManager, kubernetesManager, pipelineRunDockerOperationManager, true);

    @Before
    public void setup() {
        when(event.getApplicationContext()).thenReturn(mock(ApplicationContext.class));
    }

    @Test
    public void shouldRerunPauseAndResume() {
        when(kubernetesManager.isMasterHost()).thenReturn(true);

        listener.onApplicationEvent(event);

        verify(dockerRegistryManager).checkDockerSecrets();
        verify(cloudRegionManager).refreshCloudRegionCredKubeSecret();
        verify(pipelineRunDockerOperationManager).rerunPauseAndResume();
    }

    @Test
    public void shouldSkipRerunPauseAndResumeIfNotAMasterHost() {
        when(kubernetesManager.isMasterHost()).thenReturn(false);

        listener.onApplicationEvent(event);

        verify(dockerRegistryManager).checkDockerSecrets();
        verify(cloudRegionManager).refreshCloudRegionCredKubeSecret();
        verify(pipelineRunDockerOperationManager, never()).rerunPauseAndResume();
    }
}
