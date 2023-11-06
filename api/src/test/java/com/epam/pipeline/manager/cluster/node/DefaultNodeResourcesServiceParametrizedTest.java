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

package com.epam.pipeline.manager.cluster.node;

import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.manager.cluster.container.ContainerResources;
import com.epam.pipeline.manager.cluster.InstanceTypeCRUDService;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.RequiredArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:MagicNumber")
public class DefaultNodeResourcesServiceParametrizedTest {

    private static final int MIB_IN_GIB = 1024;
    private static final String MEMORY = "memory";
    private static final String MIB = "Mi";
    private static final String GIB = "Gi";

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final InstanceTypeCRUDService instanceTypeCRUDService = mock(InstanceTypeCRUDService.class);
    private final NodeResourcesService service = new DefaultNodeResourcesService(preferenceManager,
            instanceTypeCRUDService);

    private final String name;
    private final InstanceType type;
    private final NodeResources expected;

    @SuppressWarnings("checkstyle:MethodLength")
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                "t3.nano",
                DefaultNodeResourcesServiceTest.getInstanceType(2, 0.5f),
                NodeResources.builder()
                        .kubeMem(256 + MIB)
                        .systemMem(256 + MIB)
                        .extraMem(512 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(null)
                                .build())
                        .build()
                },
                {
                "t3.micro",
                DefaultNodeResourcesServiceTest.getInstanceType(2, 1),
                NodeResources.builder()
                        .kubeMem(256 + MIB)
                        .systemMem(256 + MIB)
                        .extraMem(512 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(null)
                                .build())
                        .build()
                },
                {
                "t3.small",
                DefaultNodeResourcesServiceTest.getInstanceType(2, (float) 2),
                NodeResources.builder()
                        .kubeMem(256 + MIB)
                        .systemMem(256 + MIB)
                        .extraMem(512 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(MIB_IN_GIB + MIB)))
                                .build())
                        .build()
                },
                {
                "t3.medium",
                DefaultNodeResourcesServiceTest.getInstanceType(2, 4),
                NodeResources.builder()
                        .kubeMem(256 + MIB)
                        .systemMem(256 + MIB)
                        .extraMem(512 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(3 * MIB_IN_GIB + MIB)))
                                .build())
                        .build()
                },
                {
                "m5.large",
                DefaultNodeResourcesServiceTest.getInstanceType(2, 8),
                NodeResources.builder()
                        .kubeMem(256 + MIB)
                        .systemMem(256 + MIB)
                        .extraMem(512 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(7 * MIB_IN_GIB + MIB)))
                                .build())
                        .build()
                },
                {
                "m5.xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(4, 16),
                NodeResources.builder()
                        .kubeMem(410 + MIB)
                        .systemMem(410 + MIB)
                        .extraMem(820 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(14744 + MIB)))
                                .build())
                        .build()
                },
                {
                "m5.2xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(8, 32),
                NodeResources.builder()
                        .kubeMem(820 + MIB)
                        .systemMem(820 + MIB)
                        .extraMem(1639 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(29489 + MIB)))
                                .build())
                        .build()
                },
                {
                "m5.4xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(16, 64),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(3277 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(60211 + MIB)))
                                .build())
                        .build()
                },
                {
                "m5.8xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(32, 128),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(6554 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(122470 + MIB)))
                                .build())
                        .build()
                },
                {
                "p3.8xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(32, 244),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(12493 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(235315 + MIB)))
                                .build())
                        .build()
                },
                {
                "r6i.8xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(32, 256),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(13108 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(246988 + MIB)))
                                .build())
                        .build()
                },
                {
                "r5.12xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(48, 384),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(19661 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(371507 + MIB)))
                                .build())
                        .build()
                },
                {
                "r5.24xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(96, 768),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(39322 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(745062 + MIB)))
                                .build())
                        .build()
                },
                {
                "r6i.32xlarge",
                DefaultNodeResourcesServiceTest.getInstanceType(128, 1024),
                NodeResources.builder()
                        .kubeMem(1024 + MIB)
                        .systemMem(1024 + MIB)
                        .extraMem(52429 + MIB)
                        .containerResources(ContainerResources.builder()
                                .requests(Collections.singletonMap(MEMORY, new Quantity(1 + GIB)))
                                .limits(Collections.singletonMap(MEMORY, new Quantity(994099 + MIB)))
                                .build())
                        .build()
                },
        });
    }

    @Test
    public void buildShouldReturnCorrectResources() {
        set(SystemPreferences.LAUNCH_CONTAINER_MEMORY_RESOURCE_REQUEST);
        set(SystemPreferences.CLUSTER_NODE_KUBE_MEM_RATIO);
        set(SystemPreferences.CLUSTER_NODE_KUBE_MEM_MIN_MIB);
        set(SystemPreferences.CLUSTER_NODE_KUBE_MEM_MAX_MIB);
        set(SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_RATIO);
        set(SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MIN_MIB);
        set(SystemPreferences.CLUSTER_NODE_SYSTEM_MEM_MAX_MIB);
        set(SystemPreferences.CLUSTER_NODE_EXTRA_MEM_RATIO);
        set(SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MIN_MIB);
        set(SystemPreferences.CLUSTER_NODE_EXTRA_MEM_MAX_MIB);

        doReturn(Optional.of(type)).when(instanceTypeCRUDService).find(any(), any(Long.class), any());

        final NodeResources actual = service.build(DefaultNodeResourcesServiceTest.getInstance());

        assertThat(actual, is(expected));
    }

    private <T> void set(final AbstractSystemPreference<T> preference) {
        set(preference, preference.getDefaultValue());
    }

    private <T> void set(final AbstractSystemPreference<T> preference, final T value) {
        doReturn(value).when(preferenceManager).getPreference(preference);
    }
}
