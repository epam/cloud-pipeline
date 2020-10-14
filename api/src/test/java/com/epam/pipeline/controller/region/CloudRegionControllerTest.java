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

package com.epam.pipeline.controller.region;

import com.epam.pipeline.acl.region.CloudRegionApiService;
import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.controller.vo.region.GCPRegionDTO;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = CloudRegionController.class)
public class CloudRegionControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String TEST_STRING = "TEST";
    private static final String REGION_URL = SERVLET_PATH + "/cloud/region";
    private static final String LOAD_PROVIDERS_URL = REGION_URL + "/provider";
    private static final String LOAD_REGIONS_INFO_URL = REGION_URL + "/info";
    private static final String LOAD_AVAILABLE_REGIONS_URL = REGION_URL + "/available";
    private static final String REGION_ID_URL = REGION_URL + "/%d";
    private final AwsRegion awsRegion = RegionCreatorUtils.getDefaultAwsRegion();
    private final AWSRegionDTO awsRegionDTO = RegionCreatorUtils.getDefaultAwsRegionDTO();
    private final AzureRegion azureRegion = RegionCreatorUtils.getDefaultAzureRegion();
    private final AzureRegionDTO azureRegionDTO = RegionCreatorUtils.getDefaultAzureRegionDTO();
    private final GCPRegion gcpRegion = RegionCreatorUtils.getDefaultGcpRegion();
    private final GCPRegionDTO gcpRegionDTO = RegionCreatorUtils.getDefaultGcpRegionDTO();

    @Autowired
    private CloudRegionApiService mockCloudRegionApiService;

    @Test
    public void shouldFailLoadProvidersForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_PROVIDERS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadProviders() throws Exception {
        final List<CloudProvider> cloudProviders = Collections.singletonList(CloudProvider.AWS);

        Mockito.doReturn(cloudProviders).when(mockCloudRegionApiService).loadProviders();

        final MvcResult mvcResult = performRequest(get(LOAD_PROVIDERS_URL));

        Mockito.verify(mockCloudRegionApiService).loadProviders();

        assertResponse(mvcResult, cloudProviders, RegionCreatorUtils.CLOUD_PROVIDER_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(REGION_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAll() throws Exception {
        final List<AwsRegion> cloudRegions = Collections.singletonList(awsRegion);

        Mockito.doReturn(cloudRegions).when(mockCloudRegionApiService).loadAll();

        final MvcResult mvcResult = performRequest(get(REGION_URL));

        Mockito.verify(mockCloudRegionApiService).loadAll();

        assertResponse(mvcResult, cloudRegions, RegionCreatorUtils.AWS_REGION_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllRegionsInfoForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_REGIONS_INFO_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRegionsInfo() throws Exception {
        final CloudRegionInfo awsRegionInfo = new CloudRegionInfo(awsRegion);
        final List<CloudRegionInfo> cloudRegionInfos = Collections.singletonList(awsRegionInfo);

        Mockito.doReturn(cloudRegionInfos).when(mockCloudRegionApiService).loadAllRegionsInfo();

        final MvcResult mvcResult = performRequest(get(LOAD_REGIONS_INFO_URL));

        Mockito.verify(mockCloudRegionApiService).loadAllRegionsInfo();

        assertResponse(mvcResult, cloudRegionInfos, RegionCreatorUtils.CLOUD_REGION_INFO_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(String.format(REGION_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldLoadAwsRegionById() throws Exception{
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(REGION_ID_URL, ID)));

        Mockito.verify(mockCloudRegionApiService).load(ID);

        assertResponse(mvcResult, awsRegion, RegionCreatorUtils.AWS_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadAzureRegionById() throws Exception{
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(REGION_ID_URL, ID)));

        Mockito.verify(mockCloudRegionApiService).load(ID);

        assertResponse(mvcResult, azureRegion, RegionCreatorUtils.AZURE_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldLoadGcpRegionById() throws Exception{
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = performRequest(get(String.format(REGION_ID_URL, ID)));

        Mockito.verify(mockCloudRegionApiService).load(ID);

        assertResponse(mvcResult, gcpRegion, RegionCreatorUtils.GCP_REGION_TYPE);
    }

    @Test
    public void shouldFailLoadAllAvailableRegionsForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(get(LOAD_AVAILABLE_REGIONS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadAllAvailableRegions() throws Exception {
        final List<String> regions = Collections.singletonList(TEST_STRING);
        Mockito.doReturn(regions).when(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        final MvcResult mvcResult = performRequest(
                get(LOAD_AVAILABLE_REGIONS_URL).param("provider", CloudProvider.AWS.name())
        );

        Mockito.verify(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        assertResponse(mvcResult, regions, RegionCreatorUtils.STRING_LIST_TYPE);
    }

    @Test
    public void shouldFailCreateForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(post(REGION_URL));
    }

    @Test
    @WithMockUser
    public void shouldCreateAwsRegion() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        final String content = getObjectMapper().writeValueAsString(awsRegionDTO);

        final MvcResult mvcResult = performRequest(post(REGION_URL).content(content));

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        assertResponse(mvcResult, awsRegion, RegionCreatorUtils.AWS_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldCreateAzureRegion() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).create(Mockito.refEq(azureRegionDTO));

        final String content = getObjectMapper().writeValueAsString(azureRegionDTO);

        final MvcResult mvcResult = performRequest(post(REGION_URL).content(content));

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(azureRegionDTO));

        assertResponse(mvcResult, azureRegion, RegionCreatorUtils.AZURE_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldCreateGcpRegion() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).create(Mockito.refEq(gcpRegionDTO));

        final String content = getObjectMapper().writeValueAsString(gcpRegionDTO);

        final MvcResult mvcResult = performRequest(post(REGION_URL).content(content));

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(gcpRegionDTO));

        assertResponse(mvcResult, gcpRegion, RegionCreatorUtils.GCP_REGION_TYPE);
    }

    @Test
    public void shouldFailUpdateForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(put(String.format(REGION_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateAwsRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        final String content = getObjectMapper().writeValueAsString(awsRegionDTO);

        final MvcResult mvcResult = performRequest(put(String.format(REGION_ID_URL, ID)).content(content));

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        assertResponse(mvcResult, awsRegion, RegionCreatorUtils.AWS_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateAzureRegionById() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService)
                .update(Mockito.eq(ID), Mockito.refEq(azureRegionDTO));

        final String content = getObjectMapper().writeValueAsString(azureRegionDTO);

        final MvcResult mvcResult = performRequest(put(String.format(REGION_ID_URL, ID)).content(content));

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(azureRegionDTO));

        assertResponse(mvcResult, azureRegion, RegionCreatorUtils.AZURE_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldUpdateGcpRegionById() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(gcpRegionDTO));

        final String content = getObjectMapper().writeValueAsString(gcpRegionDTO);

        final MvcResult mvcResult = performRequest(put(String.format(REGION_ID_URL, ID)).content(content));

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(gcpRegionDTO));

        assertResponse(mvcResult, gcpRegion, RegionCreatorUtils.GCP_REGION_TYPE);
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() throws Exception {
        performUnauthorizedRequest(delete(String.format(REGION_ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAwsRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(REGION_ID_URL, ID)));

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        assertResponse(mvcResult, awsRegion, RegionCreatorUtils.AWS_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteAzureRegionById() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(REGION_ID_URL, ID)));

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        assertResponse(mvcResult, azureRegion, RegionCreatorUtils.AZURE_REGION_TYPE);
    }

    @Test
    @WithMockUser
    public void shouldDeleteGcpRegionById() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = performRequest(delete(String.format(REGION_ID_URL, ID)));

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        assertResponse(mvcResult, gcpRegion, RegionCreatorUtils.GCP_REGION_TYPE);
    }
}
