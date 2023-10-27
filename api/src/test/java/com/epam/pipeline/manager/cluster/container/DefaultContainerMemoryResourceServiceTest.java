/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
 *  limitations under the License.
 */

package com.epam.pipeline.manager.cluster.container;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.node.NodeResources;
import com.epam.pipeline.manager.cluster.node.NodeResourcesService;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import io.fabric8.kubernetes.api.model.Quantity;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class DefaultContainerMemoryResourceServiceTest {

    private static final PipelineRun RUN = PipelineCreatorUtils.getPipelineRunWithInstance(CommonCreatorConstants.ID,
            CommonCreatorConstants.TEST_STRING, CommonCreatorConstants.ID);
    private static final PipelineRun RUN_WITHOUT_INSTANCE = PipelineCreatorUtils.getPipelineRun();

    private final NodeResourcesService nodeResourcesService = mock(NodeResourcesService.class);
    private final ContainerMemoryResourceService service = new DefaultContainerMemoryResourceService(
            nodeResourcesService);

    @Test
    public void buildShouldReturnEmptyResourcesIfRunIsMissing() {
        final ContainerResources actual = service.buildResourcesForRun(null);

        assertThat(actual, is(ContainerResources.empty()));
    }

    @Test
    public void buildShouldReturnEmptyResourcesIfRunInstanceIsMissing() {
        final ContainerResources actual = service.buildResourcesForRun(RUN_WITHOUT_INSTANCE);

        assertThat(actual, is(ContainerResources.empty()));
    }

    @Test
    public void buildShouldReturnEmptyResourcesIfNodeResourcesAreMissing() {
        doReturn(null).when(nodeResourcesService).build(any());

        final ContainerResources actual = service.buildResourcesForRun(RUN);

        assertThat(actual, is(ContainerResources.empty()));
    }

    @Test
    public void buildShouldReturnEmptyResourcesIfContainerResourcesAreMissing() {
        doReturn(NodeResources.builder().build()).when(nodeResourcesService).build(any());

        final ContainerResources actual = service.buildResourcesForRun(RUN);

        assertThat(actual, is(ContainerResources.empty()));
    }

    @Test
    public void buildShouldReturnResources() {
        final ContainerResources expected = ContainerResources.builder()
                .requests(Collections.singletonMap(CommonCreatorConstants.TEST_STRING,
                        new Quantity(CommonCreatorConstants.TEST_STRING)))
                .limits(Collections.singletonMap(CommonCreatorConstants.TEST_STRING,
                        new Quantity(CommonCreatorConstants.TEST_STRING)))
                .build();
        doReturn(NodeResources.builder().containerResources(expected).build()).when(nodeResourcesService).build(any());

        final ContainerResources actual = service.buildResourcesForRun(RUN);

        assertThat(actual, is(expected));
    }
}
