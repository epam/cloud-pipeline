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
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.manager.cluster.InstanceTypeCRUDService;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@SuppressWarnings("checkstyle:MagicNumber")
public class DefaultNodeResourcesServiceTest {

    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final InstanceTypeCRUDService instanceTypeCRUDService = mock(InstanceTypeCRUDService.class);
    private final NodeResourcesService service = new DefaultNodeResourcesService(preferenceManager,
            instanceTypeCRUDService);

    @Test
    public void buildShouldReturnEmptyResourcesIfInstanceIsMissing() {
        final NodeResources actual = service.build(null);

        assertThat(actual, is(NodeResources.empty()));
    }

    @Test
    public void buildShouldReturnEmptyResourcesIfThereAreNoInstanceTypes() {
        doReturn(Optional.empty()).when(instanceTypeCRUDService).find(any(), any(Long.class), any());

        final NodeResources actual = service.build(getInstance());

        assertThat(actual, is(NodeResources.empty()));
    }

    @Test
    public void buildShouldReturnEmptyResourcesIfInstanceTypeHasNotBeenFound() {
        doReturn(Optional.empty()).when(instanceTypeCRUDService).find(any(), any(Long.class), any());

        final NodeResources actual = service.build(getInstance());

        assertThat(actual, is(NodeResources.empty()));
    }

    @Test
    public void buildShouldReturnResourcesIfInstanceTypeHasBeenFound() {
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

        doReturn(Optional.of(getInstanceType(CommonCreatorConstants.TEST_NAME, 2, 8)))
                .when(instanceTypeCRUDService).find(any(), any(Long.class), any());

        final NodeResources actual = service.build(getInstance());

        assertNotNull(actual);
        assertThat(actual, is(not(NodeResources.empty())));
    }

    private <T> void set(final AbstractSystemPreference<T> preference) {
        set(preference, preference.getDefaultValue());
    }

    private <T> void set(final AbstractSystemPreference<T> preference, final T value) {
        doReturn(value).when(preferenceManager).getPreference(preference);
    }

    public static RunInstance getInstance() {
        return getInstance(CommonCreatorConstants.TEST_NAME);
    }

    public static RunInstance getInstance(final String name) {
        final RunInstance instance = new RunInstance();
        instance.setNodeType(name);
        instance.setCloudRegionId(CommonCreatorConstants.ID);
        instance.setSpot(false);
        return instance;
    }

    public static InstanceType getInstanceType(final int cpu, final float memGiB) {
        return getInstanceType(CommonCreatorConstants.TEST_NAME, cpu, memGiB);
    }

    private static InstanceType getInstanceType(final String name, final int cpu, final float memGiB) {
        final InstanceType type = new InstanceType();
        type.setName(name);
        type.setVCPU(cpu);
        type.setMemory(memGiB);
        return type;
    }
}
