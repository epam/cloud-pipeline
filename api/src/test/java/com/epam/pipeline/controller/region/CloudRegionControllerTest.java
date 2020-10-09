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

import com.epam.pipeline.controller.vo.region.AzureRegionDTO;
import com.epam.pipeline.controller.vo.region.GCPRegionDTO;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.test.creator.region.RegionCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.epam.pipeline.controller.ResponseResult;
import com.epam.pipeline.controller.Result;
import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.region.CloudRegionApiService;
import com.epam.pipeline.util.ControllerTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Before;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CloudRegionController.class)
public class CloudRegionControllerTest extends AbstractControllerTest {

    private static final long ID = 1L;
    private static final String TEST_STRING = "TEST";
    private static final String REGION_URL = SERVLET_PATH + "/cloud/region";
    private static final String LOAD_PROVIDERS_URL = REGION_URL + "/provider";
    private static final String LOAD_REGIONS_INFO_URL = REGION_URL + "/info";
    private static final String LOAD_AVAILABLE_REGIONS_URL = REGION_URL + "/available";
    private static final String REGION_ID_URL = REGION_URL + "/%d";
    private AwsRegion awsRegion;
    private AWSRegionDTO awsRegionDTO;
    private AzureRegion azureRegion;
    private AzureRegionDTO azureRegionDTO;
    private GCPRegion gcpRegion;
    private GCPRegionDTO gcpRegionDTO;
    private ResponseResult<AwsRegion> expectedAwsRegionResult;
    private ResponseResult<AzureRegion> expectedAzureResult;
    private ResponseResult<GCPRegion> expectedGcpResult;

    @Autowired
    private CloudRegionApiService mockCloudRegionApiService;

    @Before
    public void setUp() {
        awsRegion = RegionCreatorUtils.getDefaultAwsRegion();
        azureRegion = RegionCreatorUtils.getDefaultAzureRegion();
        gcpRegion = RegionCreatorUtils.getDefaultGcpRegion();

        awsRegionDTO = RegionCreatorUtils.getDefaultAwsRegionDTO();
        azureRegionDTO = RegionCreatorUtils.getDefaultAzureRegionDTO();
        gcpRegionDTO = RegionCreatorUtils.getDefaultGcpRegionDTO();

        expectedAwsRegionResult = ControllerTestUtils.buildExpectedResult(awsRegion);
        expectedAzureResult = ControllerTestUtils.buildExpectedResult(azureRegion);
        expectedGcpResult = ControllerTestUtils.buildExpectedResult(gcpRegion);
    }

    @Test
    public void shouldFailLoadProvidersForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_PROVIDERS_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadProviders() throws Exception {
        final List<CloudProvider> cloudProviders = Collections.singletonList(CloudProvider.AWS);

        Mockito.doReturn(cloudProviders).when(mockCloudRegionApiService).loadProviders();

        final MvcResult mvcResult = mvc().perform(get(LOAD_PROVIDERS_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).loadProviders();

        final ResponseResult<List<CloudProvider>> expectedResult =
                ControllerTestUtils.buildExpectedResult(cloudProviders);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<CloudProvider>>>() { });
    }

    @Test
    public void shouldFailLoadAllForUnauthorizedUser() throws Exception {
        mvc().perform(get(REGION_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAll() throws Exception {
        final List<AwsRegion> cloudRegions = Collections.singletonList(awsRegion);

        Mockito.doReturn(cloudRegions).when(mockCloudRegionApiService).loadAll();

        final MvcResult mvcResult = mvc().perform(get(REGION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).loadAll();

        final ResponseResult<List<AwsRegion>> expectedResult =
                ControllerTestUtils.buildExpectedResult(cloudRegions);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<AwsRegion>>>() { });
    }

    @Test
    public void shouldFailLoadAllRegionsInfoForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_REGIONS_INFO_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAllRegionsInfo() throws Exception {
        final CloudRegionInfo awsRegionInfo = new CloudRegionInfo(awsRegion);
        final List<CloudRegionInfo> cloudRegionInfos = Collections.singletonList(awsRegionInfo);

        Mockito.doReturn(cloudRegionInfos).when(mockCloudRegionApiService).loadAllRegionsInfo();

        final MvcResult mvcResult = mvc().perform(get(LOAD_REGIONS_INFO_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).loadAllRegionsInfo();

        final ResponseResult<List<CloudRegionInfo>> expectedResult =
                ControllerTestUtils.buildExpectedResult(cloudRegionInfos);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<CloudRegionInfo>>>() { });
    }

    @Test
    public void shouldFailLoadForUnauthorizedUser() throws Exception {
        mvc().perform(get(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAwsRegionById() throws Exception{
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = mvc().perform(get(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).load(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldLoadAzureRegionById() throws Exception{
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = mvc().perform(get(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).load(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAzureResult,
                new TypeReference<Result<AzureRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldLoadGcpRegionById() throws Exception{
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = mvc().perform(get(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).load(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedGcpResult,
                new TypeReference<Result<GCPRegion>>() { });
    }

    @Test
    public void shouldFailLoadAllAvailableRegionsForUnauthorizedUser() throws Exception {
        mvc().perform(get(LOAD_AVAILABLE_REGIONS_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldLoadAllAvailableRegions() throws Exception {
        final List<String> regions = Collections.singletonList(TEST_STRING);
        Mockito.doReturn(regions).when(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        final MvcResult mvcResult = mvc().perform(get(LOAD_AVAILABLE_REGIONS_URL)
                .param("provider", CloudProvider.AWS.name())
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        final ResponseResult<List<String>> expectedResult = ControllerTestUtils.buildExpectedResult(regions);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedResult,
                new TypeReference<Result<List<String>>>() { });
    }

    @Test
    public void shouldFailCreateForUnauthorizedUser() throws Exception {
        mvc().perform(post(REGION_URL)
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldCreateAwsRegion() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        final MvcResult mvcResult = mvc().perform(post(REGION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(awsRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldCreateAzureRegion() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).create(Mockito.refEq(azureRegionDTO));

        final MvcResult mvcResult = mvc().perform(post(REGION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(azureRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(azureRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAzureResult,
                new TypeReference<Result<AzureRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldCreateGcpRegion() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).create(Mockito.refEq(gcpRegionDTO));

        final MvcResult mvcResult = mvc().perform(post(REGION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(gcpRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(gcpRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedGcpResult,
                new TypeReference<Result<GCPRegion>>() { });
    }

    @Test
    public void shouldFailUpdateForUnauthorizedUser() throws Exception {
        mvc().perform(put(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldUpdateAwsRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        final MvcResult mvcResult = mvc().perform(put(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(awsRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldUpdateAzureRegionById() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService)
                .update(Mockito.eq(ID), Mockito.refEq(azureRegionDTO));

        final MvcResult mvcResult = mvc().perform(put(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(azureRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(azureRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAzureResult,
                new TypeReference<Result<AzureRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldUpdateGcpRegionById() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(gcpRegionDTO));

        final MvcResult mvcResult = mvc().perform(put(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(gcpRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(gcpRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedGcpResult,
                new TypeReference<Result<GCPRegion>>() { });
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() throws Exception {
        mvc().perform(delete(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldDeleteAwsRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = mvc().perform(delete(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldDeleteAzureRegionById() throws Exception {
        Mockito.doReturn(azureRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = mvc().perform(delete(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAzureResult,
                new TypeReference<Result<AzureRegion>>() { });
    }

    @Test
    @WithMockUser
    public void shouldDeleteGcpRegionById() throws Exception {
        Mockito.doReturn(gcpRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = mvc().perform(delete(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedGcpResult,
                new TypeReference<Result<GCPRegion>>() { });
    }
}
