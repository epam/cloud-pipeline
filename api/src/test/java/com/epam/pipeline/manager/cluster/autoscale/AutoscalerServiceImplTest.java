/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster.autoscale;

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.ObjectCreatorUtils;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.NodeDiskManager;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.UnusedPrivateField")
public class AutoscalerServiceImplTest {

    private static final String INSTANCE_TYPE = "m5.large";
    private static final String FALLBACK_TYPE_1 = "c5.large";
    private static final String FALLBACK_TYPE_2 = "r4.large";
    private static final int DEFAULT_INSTANCE_HDD = 30;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private CloudRegionManager cloudRegionManager;

    @Mock
    private PipelineRunManager pipelineRunManager;

    @Mock
    private PipelineRunCRUDService runCRUDService;

    @Mock
    private NodePoolManager nodePoolManager;

    @Mock
    private CloudFacade cloudFacade;

    @Mock
    private NodeDiskManager nodeDiskManager;

    @InjectMocks
    private AutoscalerServiceImpl autoscalerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final AwsRegion defaultRegion = ObjectCreatorUtils.getDefaultAwsRegion();
        defaultRegion.setId(1L);
        when(cloudRegionManager.loadDefaultRegion()).thenReturn(defaultRegion);
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD)).thenReturn(DEFAULT_INSTANCE_HDD);
    }

    @Test
    public void shouldNotSetFallbackInstanceTypesWhenFeatureIsDisabled() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT))
                .thenReturn(-1);

        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setInstanceType(INSTANCE_TYPE);
        configuration.setFallbackInstanceTypes(Arrays.asList(FALLBACK_TYPE_1, FALLBACK_TYPE_2));

        final RunInstance instance = autoscalerService.configurationToInstance(configuration);

        assertThat(instance.getFallbackInstanceTypes()).isNullOrEmpty();
    }

    @Test
    public void shouldSetFallbackInstanceTypesWhenFeatureIsEnabled() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT))
                .thenReturn(5);

        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setInstanceType(INSTANCE_TYPE);
        configuration.setFallbackInstanceTypes(Arrays.asList(FALLBACK_TYPE_1, FALLBACK_TYPE_2));

        final RunInstance instance = autoscalerService.configurationToInstance(configuration);

        assertThat(instance.getFallbackInstanceTypes())
                .containsExactlyInAnyOrder(FALLBACK_TYPE_1, FALLBACK_TYPE_2);
    }

    @Test
    public void shouldSetNullFallbackInstanceTypesWhenNoneProvidedAndFeatureIsEnabled() {
        when(preferenceManager.getPreference(SystemPreferences.CLUSTER_FALLBACK_INSTANCE_TYPES_MAX_COUNT))
                .thenReturn(5);

        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setInstanceType(INSTANCE_TYPE);

        final RunInstance instance = autoscalerService.configurationToInstance(configuration);

        assertThat(instance.getFallbackInstanceTypes()).isNullOrEmpty();
    }
}
