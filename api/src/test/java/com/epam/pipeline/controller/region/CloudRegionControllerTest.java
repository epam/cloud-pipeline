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
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.util.ControllerTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.List;

@WebMvcTest(controllers = CloudRegionController.class)
public class CloudRegionControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String TEST_STRING = "TEST";
    private static final String REGION_URL = SERVLET_PATH + "/cloud/region";
    private static final String LOAD_PROVIDERS_URL = REGION_URL + "/provider";
    private static final String LOAD_REGIONS_INFO_URL = REGION_URL + "/info";
    private static final String LOAD_AVAILABLE_REGIONS_URL = REGION_URL + "/available";
    private static final String REGION_ID_URL = REGION_URL + "/%d";
    private static final String EMPTY_CONTENT = "";
    private static final MultiValueMap<String, String> EMPTY_PARAMS = new LinkedMultiValueMap<>();
    private final AwsRegion awsRegion = RegionCreatorUtils.getDefaultAwsRegion();
    private final AWSRegionDTO awsRegionDTO = RegionCreatorUtils.getDefaultAwsRegionDTO();
    private final AzureRegion azureRegion = RegionCreatorUtils.getDefaultAzureRegion();
    private final AzureRegionDTO azureRegionDTO = RegionCreatorUtils.getDefaultAzureRegionDTO();
    private final GCPRegion gcpRegion = RegionCreatorUtils.getDefaultGcpRegion();
    private final GCPRegionDTO gcpRegionDTO = RegionCreatorUtils.getDefaultGcpRegionDTO();
    private final TypeReference<Result<AwsRegion>> awsRegionTR = new TypeReference<Result<AwsRegion>>() { };
    private final TypeReference<Result<AzureRegion>> azureRegionTR = new TypeReference<Result<AzureRegion>>() { };
    private final TypeReference<Result<GCPRegion>> gcpRegionTR = new TypeReference<Result<GCPRegion>>() { };

    @Autowired
    private CloudRegionApiService mockCloudRegionApiService;

    @Autowired
    private ControllerTestUtils controllerTestUtils;

    @Test
    public void shouldFailLoadProvidersForUnauthorizedUser() throws Exception {
        controllerTestUtils.getRequestUnauthorized(mvc(), LOAD_PROVIDERS_URL);
    }

    @Test
    @WithMockUser
    public void shouldLoadProviders() throws Exception {
        final List<CloudProvider> cloudProviders = Collections.singletonList(CloudProvider.AWS);

        Mockito.doReturn(cloudProviders).when(mockCloudRegionApiService).loadProviders();

        final MvcResult mvcResult =
                controllerTestUtils.getRequest(mvc(), LOAD_PROVIDERS_URL, EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).loadProviders();

        controllerTestUtils.assertResponse(mvcResult, cloudProviders,
                new TypeReference<Result<List<CloudProvider>>>() { });
    }

    @Test
    public void shouldFailLoadAllForUnauthorizedUser() throws Exception {
        controllerTestUtils.getRequestUnauthorized(mvc(), REGION_URL);
    }

    @Test
    @WithMockUser
    public void shouldLoadAll() throws Exception {
        final List<AwsRegion> cloudRegions = Collections.singletonList(awsRegion);

        Mockito.doReturn(cloudRegions).when(mockCloudRegionApiService).loadAll();

        final MvcResult mvcResult = controllerTestUtils.getRequest(mvc(), REGION_URL, EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).loadAll();

        controllerTestUtils.assertResponse(mvcResult, cloudRegions,
                new TypeReference<Result<List<AwsRegion>>>() { });
    }

    @Test
    public void shouldFailLoadAllRegionsInfoForUnauthorizedUser() throws Exception {
        controllerTestUtils.getRequestUnauthorized(mvc(), LOAD_REGIONS_INFO_URL);
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRegionsInfo() throws Exception {
        final CloudRegionInfo awsRegionInfo = new CloudRegionInfo(awsRegion);
        final List<CloudRegionInfo> cloudRegionInfos = Collections.singletonList(awsRegionInfo);

        Mockito.doReturn(cloudRegionInfos).when(mockCloudRegionApiService).loadAllRegionsInfo();

        final MvcResult mvcResult =
                controllerTestUtils.getRequest(mvc(), LOAD_REGIONS_INFO_URL, EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).loadAllRegionsInfo();

        controllerTestUtils.assertResponse(mvcResult, cloudRegionInfos,
                new TypeReference<Result<List<CloudRegionInfo>>>() { });
    }

    @Test
    public void shouldFailLoadForUnauthorizedUser() throws Exception {
        controllerTestUtils.getRequestUnauthorized(mvc(), String.format(REGION_ID_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldLoadAwsRegionById() throws Exception{
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult =
                controllerTestUtils.getRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).load(ID);

        controllerTestUtils.assertResponse(mvcResult, awsRegion, awsRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldLoadAzureRegionById() throws Exception{
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult =
                controllerTestUtils.getRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).load(ID);

        controllerTestUtils.assertResponse(mvcResult, azureRegion, azureRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldLoadGcpRegionById() throws Exception{
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult =
                controllerTestUtils.getRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).load(ID);

        controllerTestUtils.assertResponse(mvcResult, gcpRegion, gcpRegionTR);
    }

    @Test
    public void shouldFailLoadAllAvailableRegionsForUnauthorizedUser() throws Exception {
        controllerTestUtils.getRequestUnauthorized(mvc(), LOAD_AVAILABLE_REGIONS_URL);
    }

    @Test
    @WithMockUser
    public void shouldLoadAllAvailableRegions() throws Exception {
        final List<String> regions = Collections.singletonList(TEST_STRING);
        Mockito.doReturn(regions).when(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("provider", CloudProvider.AWS.name());

        final MvcResult mvcResult =
                controllerTestUtils.getRequest(mvc(), LOAD_AVAILABLE_REGIONS_URL, params, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        controllerTestUtils.assertResponse(mvcResult, regions, new TypeReference<Result<List<String>>>() { });
    }

    @Test
    public void shouldFailCreateForUnauthorizedUser() throws Exception {
        controllerTestUtils.postRequestUnauthorized(mvc(), REGION_URL);
    }

    @Test
    @WithMockUser
    public void shouldCreateAwsRegion() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        final String content = getObjectMapper().writeValueAsString(awsRegionDTO);

        final MvcResult mvcResult =
                controllerTestUtils.postRequest(mvc(), REGION_URL, EMPTY_PARAMS, content);

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        controllerTestUtils.assertResponse(mvcResult, awsRegion, awsRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldCreateAzureRegion() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).create(Mockito.refEq(azureRegionDTO));

        final String content = getObjectMapper().writeValueAsString(azureRegionDTO);

        final MvcResult mvcResult =
                controllerTestUtils.postRequest(mvc(), REGION_URL, EMPTY_PARAMS, content);

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(azureRegionDTO));

        controllerTestUtils.assertResponse(mvcResult, azureRegion, azureRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldCreateGcpRegion() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).create(Mockito.refEq(gcpRegionDTO));

        final String content = getObjectMapper().writeValueAsString(gcpRegionDTO);

        final MvcResult mvcResult =
                controllerTestUtils.postRequest(mvc(), REGION_URL, EMPTY_PARAMS, content);

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(gcpRegionDTO));

        controllerTestUtils.assertResponse(mvcResult, gcpRegion, gcpRegionTR);
    }

    @Test
    public void shouldFailUpdateForUnauthorizedUser() throws Exception {
        controllerTestUtils.putRequestUnauthorized(mvc(), String.format(REGION_ID_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldUpdateAwsRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        final String content = getObjectMapper().writeValueAsString(awsRegionDTO);

        final MvcResult mvcResult =
                controllerTestUtils.putRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, content);

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        controllerTestUtils.assertResponse(mvcResult, awsRegion, awsRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldUpdateAzureRegionById() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService)
                .update(Mockito.eq(ID), Mockito.refEq(azureRegionDTO));

        final String content = getObjectMapper().writeValueAsString(azureRegionDTO);

        final MvcResult mvcResult =
                controllerTestUtils.putRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, content);

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(azureRegionDTO));

        controllerTestUtils.assertResponse(mvcResult, azureRegion, azureRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldUpdateGcpRegionById() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(gcpRegionDTO));

        final String content = getObjectMapper().writeValueAsString(gcpRegionDTO);

        final MvcResult mvcResult =
                controllerTestUtils.putRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, content);

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(gcpRegionDTO));

        controllerTestUtils.assertResponse(mvcResult, gcpRegion, gcpRegionTR);
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() throws Exception {
        controllerTestUtils.deleteRequestUnauthorized(mvc(), String.format(REGION_ID_URL, ID));
    }

    @Test
    @WithMockUser
    public void shouldDeleteAwsRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult =
                controllerTestUtils.deleteRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        controllerTestUtils.assertResponse(mvcResult, awsRegion, awsRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldDeleteAzureRegionById() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult =
                controllerTestUtils.deleteRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        controllerTestUtils.assertResponse(mvcResult, azureRegion, azureRegionTR);
    }

    @Test
    @WithMockUser
    public void shouldDeleteGcpRegionById() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult =
                controllerTestUtils.deleteRequest(mvc(), String.format(REGION_ID_URL, ID), EMPTY_PARAMS, EMPTY_CONTENT);

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        controllerTestUtils.assertResponse(mvcResult, gcpRegion, gcpRegionTR);
    }
}
