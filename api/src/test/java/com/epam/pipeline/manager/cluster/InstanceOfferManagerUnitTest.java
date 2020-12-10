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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.cluster.InstanceOfferDao;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.cluster.InstanceType;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.contextual.ContextualPreferenceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class InstanceOfferManagerUnitTest {

    private static final Long TOOL_ID = 1L;
    private static final Long REGION_ID = 1L;
    private static final Long ANOTHER_REGION_ID = 2L;
    private static final ContextualPreferenceExternalResource TOOL_RESOURCE =
            new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, TOOL_ID.toString());
    private static final String ALLOWED_INSTANCE_TYPES_PREFERENCE =
            SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES.getKey();
    private static final String ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE =
            SystemPreferences.CLUSTER_ALLOWED_INSTANCE_TYPES_DOCKER.getKey();
    private static final String ALLOWED_PRICE_TYPES_PREFERENCE =
            SystemPreferences.CLUSTER_ALLOWED_PRICE_TYPES.getKey();
    private static final String ALLOWED_MASTER_PRICE_TYPES_PREFERENCES =
            SystemPreferences.CLUSTER_ALLOWED_MASTER_PRICE_TYPES.getKey();
    private static final List<String> INSTANCE_TYPES_PREFERENCES =
            Collections.singletonList(ALLOWED_INSTANCE_TYPES_PREFERENCE);
    private static final List<String> DOCKER_INSTANCE_TYPES_PREFERENCES =
            Arrays.asList(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, ALLOWED_INSTANCE_TYPES_PREFERENCE);
    private static final List<String> PRICE_TYPES_PREFERENCES =
            Collections.singletonList(ALLOWED_PRICE_TYPES_PREFERENCE);
    private static final String M4_LARGE = "m4.large";
    private static final String M5_LARGE = "m5.large";
    private static final String T2_LARGE = "t2.large";
    private static final String S2_LARGE = "s2.large";
    private static final String DV3 = "Dv3";
    private static final String M4_PATTERN = "m4.*";
    private static final String M4_M5_PATTERNS = "m4.*,m5.*";
    private static final String S2_PATTERN = "s2.*";
    private static final String ANY_PATTERN = "*";
    private static final String SPOT = PriceType.SPOT.getLiteral();
    private static final String ON_DEMAND = PriceType.ON_DEMAND.getLiteral();
    private static final String SPOT_AND_ON_DEMAND_TYPES = String.format("%s,%s", PriceType.SPOT, PriceType.ON_DEMAND);
    private static final String TERM_TYPE = "OnDemand";

    private final AbstractCloudRegion defaultRegion = region(REGION_ID);
    private final AbstractCloudRegion anotherRegion = region(ANOTHER_REGION_ID);
    private final InstanceType m4InstanceType = instanceType(M4_LARGE, defaultRegion);
    private final InstanceType m5InstanceType = instanceType(M5_LARGE, defaultRegion);
    private final InstanceType t2InstanceType = instanceType(T2_LARGE, defaultRegion);
    private final InstanceType dv3InstanceType = instanceType(DV3, anotherRegion);

    private final InstanceOfferDao instanceOfferDao = mock(InstanceOfferDao.class);
    private final PipelineVersionManager versionManager = mock(PipelineVersionManager.class);
    private final PipelineRunManager pipelineRunManager = mock(PipelineRunManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final PreferenceManager preferenceManager = mock(PreferenceManager.class);
    private final CloudRegionManager cloudRegionManager = mock(CloudRegionManager.class);
    private final ContextualPreferenceManager contextualPreferenceManager = mock(ContextualPreferenceManager.class);
    private final InstanceOfferManager instanceOfferManager = Mockito.spy(
        new InstanceOfferManager(instanceOfferDao, versionManager, pipelineRunManager, messageHelper, preferenceManager,
                                 cloudRegionManager, contextualPreferenceManager, Collections.emptyList()));

    @Before
    public void setUp() {
        when(cloudRegionManager.loadDefaultRegion()).thenReturn(defaultRegion);
        when(cloudRegionManager.load(defaultRegion.getId())).thenReturn(defaultRegion);
        when(cloudRegionManager.load(anotherRegion.getId())).thenReturn(anotherRegion);
        instanceOfferManager.updateOfferedInstanceTypes(Arrays.asList(m4InstanceType, m5InstanceType, t2InstanceType,
                dv3InstanceType));
    }

    @Test
    public void getAllowedInstanceAndPriceTypesShouldSearchPreferencesIfToolIdIsNull() {
        final List<String> allowedPriceTypes = Arrays.asList(SPOT, ON_DEMAND);
        final List<InstanceType> allInstanceTypes = Arrays.asList(m4InstanceType, m5InstanceType, t2InstanceType);
        final List<InstanceType> allowedInstanceTypes = Arrays.asList(m4InstanceType, m5InstanceType);
        final List<InstanceType> allowedInstanceDockerTypes = Collections.singletonList(m4InstanceType);
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));
        when(contextualPreferenceManager.search(eq(Arrays.asList(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE,
                ALLOWED_INSTANCE_TYPES_PREFERENCE)), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, M4_PATTERN));
        when(contextualPreferenceManager.search(eq(Collections.singletonList(ALLOWED_PRICE_TYPES_PREFERENCE)),
                eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_PRICE_TYPES_PREFERENCE, SPOT_AND_ON_DEMAND_TYPES));
        when(contextualPreferenceManager.search(eq(Collections.singletonList(ALLOWED_MASTER_PRICE_TYPES_PREFERENCES)),
                eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_MASTER_PRICE_TYPES_PREFERENCES, SPOT_AND_ON_DEMAND_TYPES));
        when(instanceOfferManager.getAllInstanceTypes(any(), anyBoolean())).thenReturn(allInstanceTypes);

        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                instanceOfferManager.getAllowedInstanceAndPriceTypes(null, null, false);

        assertThat(allowedInstanceAndPriceTypes.getAllowedInstanceTypes(), is(allowedInstanceTypes));
        assertThat(allowedInstanceAndPriceTypes.getAllowedInstanceDockerTypes(), is(allowedInstanceDockerTypes));
        assertThat(allowedInstanceAndPriceTypes.getAllowedPriceTypes(), is(allowedPriceTypes));
    }

    @Test
    public void getAllowedInstanceAndPriceTypesShouldSearchPreferencesUsingToolAsExternalResourceIfToolIdIsSpecified() {
        final List<String> allowedPriceTypes = Arrays.asList(SPOT, ON_DEMAND);
        final List<InstanceType> allInstanceTypes = Arrays.asList(m4InstanceType, m5InstanceType, t2InstanceType);
        final List<InstanceType> allowedInstanceTypes = Arrays.asList(m4InstanceType, m5InstanceType);
        final List<InstanceType> allowedInstanceDockerTypes = Collections.singletonList(m4InstanceType);
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));
        when(contextualPreferenceManager.search(eq(Arrays.asList(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE,
                ALLOWED_INSTANCE_TYPES_PREFERENCE)), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, M4_PATTERN));
        when(contextualPreferenceManager.search(eq(Collections.singletonList(ALLOWED_PRICE_TYPES_PREFERENCE)),
                eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_PRICE_TYPES_PREFERENCE, SPOT_AND_ON_DEMAND_TYPES));
        when(contextualPreferenceManager.search(eq(Collections.singletonList(ALLOWED_MASTER_PRICE_TYPES_PREFERENCES)),
                eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_MASTER_PRICE_TYPES_PREFERENCES, SPOT_AND_ON_DEMAND_TYPES));
        when(instanceOfferManager.getAllInstanceTypes(any(), anyBoolean())).thenReturn(allInstanceTypes);

        final AllowedInstanceAndPriceTypes allowedInstanceAndPriceTypes =
                instanceOfferManager.getAllowedInstanceAndPriceTypes(TOOL_ID, null, false);

        assertThat(allowedInstanceAndPriceTypes.getAllowedInstanceTypes(), is(allowedInstanceTypes));
        assertThat(allowedInstanceAndPriceTypes.getAllowedInstanceDockerTypes(), is(allowedInstanceDockerTypes));
        assertThat(allowedInstanceAndPriceTypes.getAllowedPriceTypes(), is(allowedPriceTypes));
    }

    @Test
    public void getAllowedInstanceAndPriceTypesShouldLoadInstanceTypesForSingleRegionIfItIsSpecified() {
        when(instanceOfferManager.getAllInstanceTypes(any(), anyBoolean())).thenReturn(Collections.emptyList());
        when(contextualPreferenceManager.search(any(), any()))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        instanceOfferManager.getAllowedInstanceAndPriceTypes(null, REGION_ID, false);

        ArgumentCaptor<Long> argument = ArgumentCaptor.forClass(Long.class);
        verify(instanceOfferManager).getAllInstanceTypes(argument.capture(), anyBoolean());
        final Long regionId = argument.getValue();
        assertThat(regionId, is(REGION_ID));
    }

    @Test
    public void isInstanceAllowedShouldReturnTrueIfInstanceTypeMatchesOneOfTheAllowedPatterns() {
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));

        assertTrue(instanceOfferManager.isInstanceAllowed(M4_LARGE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isInstanceAllowedShouldReturnFalseIfInstanceTypeDoesNotMatchAnyOfTheAllowedPatterns() {
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));

        assertFalse(instanceOfferManager.isInstanceAllowed(T2_LARGE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isInstanceAllowedShouldReturnFalseIfInstanceTypeIsNotOffered() {
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, S2_PATTERN));

        assertFalse(instanceOfferManager.isInstanceAllowed(S2_LARGE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isInstanceAllowedShouldReturnFalseIfInstanceTypeIsNotAllowedInTheSpecifiedRegion() {
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        assertFalse(instanceOfferManager.isInstanceAllowed(DV3, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isInstanceAllowedShouldReturnTrueIfInstanceTypeIsAllowedInTheSpecifiedRegion() {
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        assertTrue(instanceOfferManager.isInstanceAllowed(DV3, ANOTHER_REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isInstanceAllowedShouldCheckInstanceTypesAllowedInDefaultRegionIfNoRegionIsSpecified() {
        when(contextualPreferenceManager.search(eq(INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        assertFalse(instanceOfferManager.isInstanceAllowed(DV3, null, false));
        assertTrue(instanceOfferManager.isInstanceAllowed(M4_LARGE, null, false));
        verify(contextualPreferenceManager, times(2)).search(eq(INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isToolInstanceAllowedShouldReturnTrueIfInstanceTypeMatchesOneOfTheAllowedPatterns() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));

        assertTrue(instanceOfferManager.isToolInstanceAllowed(M4_LARGE, TOOL_RESOURCE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isToolInstanceAllowedShouldReturnFalseIfInstanceTypeDoesNotMatchAnyOfTheAllowedPatterns() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));

        assertFalse(instanceOfferManager.isToolInstanceAllowed(T2_LARGE, TOOL_RESOURCE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isToolInstanceAllowedReturnResultIfResourceIsNull() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, M4_M5_PATTERNS));

        assertTrue(instanceOfferManager.isToolInstanceAllowed(M4_LARGE, null, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(null));
    }

    @Test
    public void isToolInstanceAllowedShouldReturnFalseIfInstanceTypeIsNotOffered() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, S2_PATTERN));

        assertFalse(instanceOfferManager.isToolInstanceAllowed(S2_LARGE, TOOL_RESOURCE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isToolInstanceAllowedShouldReturnFalseIfInstanceTypeIsNotAllowedInTheSpecifiedRegion() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        assertFalse(instanceOfferManager.isToolInstanceAllowed(DV3, TOOL_RESOURCE, REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isToolInstanceAllowedShouldReturnTrueIfInstanceTypeIsAllowedInTheSpecifiedRegion() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        assertTrue(instanceOfferManager.isToolInstanceAllowed(DV3, TOOL_RESOURCE, ANOTHER_REGION_ID, false));
        verify(contextualPreferenceManager).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isToolInstanceAllowedShouldCheckInstanceTypesAllowedInDefaultRegionIfNoRegionIsSpecified() {
        when(contextualPreferenceManager.search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_DOCKER_INSTANCE_TYPES_PREFERENCE, ANY_PATTERN));

        assertFalse(instanceOfferManager.isToolInstanceAllowed(DV3, TOOL_RESOURCE, null, false));
        assertTrue(instanceOfferManager.isToolInstanceAllowed(M4_LARGE, TOOL_RESOURCE, null, false));
        verify(contextualPreferenceManager, times(2)).search(eq(DOCKER_INSTANCE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isPriceTypeAllowedShouldReturnTrueIfGivenPriceTypeEqualsToOneOfTheAllowedPriceTypes() {
        when(contextualPreferenceManager.search(eq(PRICE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_PRICE_TYPES_PREFERENCE, SPOT_AND_ON_DEMAND_TYPES));

        assertTrue(instanceOfferManager.isPriceTypeAllowed(SPOT, TOOL_RESOURCE));
        verify(contextualPreferenceManager).search(eq(PRICE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isPriceTypeAllowedShouldReturnFalseIfGivenPriceTypeDoesNotEqualToAnyOfTheAllowedPriceTypes() {
        when(contextualPreferenceManager.search(eq(PRICE_TYPES_PREFERENCES), eq(TOOL_RESOURCE)))
                .thenReturn(new ContextualPreference(ALLOWED_PRICE_TYPES_PREFERENCE, ON_DEMAND));

        assertFalse(instanceOfferManager.isPriceTypeAllowed(SPOT, TOOL_RESOURCE));
        verify(contextualPreferenceManager).search(eq(PRICE_TYPES_PREFERENCES), eq(TOOL_RESOURCE));
    }

    @Test
    public void isPriceTypeAllowedShouldReturnResultIfResourceIsNull() {
        when(contextualPreferenceManager.search(eq(PRICE_TYPES_PREFERENCES), eq(null)))
                .thenReturn(new ContextualPreference(ALLOWED_PRICE_TYPES_PREFERENCE, SPOT_AND_ON_DEMAND_TYPES));

        assertTrue(instanceOfferManager.isPriceTypeAllowed(SPOT, null));
        verify(contextualPreferenceManager).search(eq(PRICE_TYPES_PREFERENCES), eq(null));
    }

    private InstanceType instanceType(final String name, final AbstractCloudRegion region) {
        final InstanceType instanceType = new InstanceType();
        instanceType.setName(name);
        instanceType.setTermType(TERM_TYPE);
        instanceType.setRegionId(region.getId());
        return instanceType;
    }

    private AwsRegion region(final Long id) {
        final AwsRegion region = new AwsRegion();
        region.setId(id);
        region.setProvider(CloudProvider.AWS);
        return region;
    }
}
