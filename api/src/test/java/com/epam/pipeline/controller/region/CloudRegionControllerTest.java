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
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
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
    private static final int DURATION = 773;
    private static final String REGION_URL = SERVLET_PATH + "/cloud/region";
    private static final String LOAD_PROVIDERS_URL = REGION_URL + "/provider";
    private static final String LOAD_REGIONS_INFO_URL = REGION_URL + "/info";
    private static final String LOAD_AVAILABLE_REGIONS_URL = REGION_URL + "/available";
    private static final String REGION_ID_URL = REGION_URL + "/%d";
    private AwsRegion awsRegion;
    private ResponseResult<AwsRegion> expectedAwsRegionResult;

    @Autowired
    private CloudRegionApiService mockCloudRegionApiService;

    @Before
    public void setUp() {
        awsRegion = new AwsRegion();
        awsRegion.setId(ID);
        awsRegion.setName("testName");
        awsRegion.setRegionCode("7367");

        expectedAwsRegionResult = ControllerTestUtils.buildExpectedResult(awsRegion);
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
    public void shouldLoadRegionById() throws Exception{
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).load(ID);

        final MvcResult mvcResult = mvc().perform(get(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        final ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(mockCloudRegionApiService).load(longCaptor.capture());
        Assertions.assertThat(longCaptor.getValue()).isEqualTo(ID);

        Mockito.verify(mockCloudRegionApiService).load(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
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
        final List<String> regions = Collections.singletonList("testRegion");
        Mockito.doReturn(regions).when(mockCloudRegionApiService).loadAllAvailable(CloudProvider.AWS);

        final MvcResult mvcResult = mvc().perform(get(LOAD_AVAILABLE_REGIONS_URL)
                .param("provider", CloudProvider.AWS.name())
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        final ArgumentCaptor<CloudProvider> cloudProviderCaptor = ArgumentCaptor.forClass(CloudProvider.class);
        Mockito.verify(mockCloudRegionApiService).loadAllAvailable(cloudProviderCaptor.capture());
        Assertions.assertThat(cloudProviderCaptor.getValue()).isEqualTo(CloudProvider.AWS);

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
    public void shouldCreateRegion() throws Exception {
        final AWSRegionDTO awsRegionDTO = new AWSRegionDTO();
        awsRegionDTO.setBackupDuration(DURATION);
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        final MvcResult mvcResult = mvc().perform(post(REGION_URL)
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(awsRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        final ArgumentCaptor<AWSRegionDTO> awsRegionDTOCaptor = ArgumentCaptor.forClass(AWSRegionDTO.class);
        Mockito.verify(mockCloudRegionApiService).create(awsRegionDTOCaptor.capture());
        Assertions.assertThat(awsRegionDTOCaptor.getValue().getBackupDuration())
                .isEqualTo(awsRegionDTO.getBackupDuration());

        Mockito.verify(mockCloudRegionApiService).create(Mockito.refEq(awsRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }

    @Test
    public void shouldFailUpdateForUnauthorizedUser() throws Exception {
        mvc().perform(put(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldUpdateRegionById() throws Exception {
        final AWSRegionDTO awsRegionDTO = new AWSRegionDTO();
        awsRegionDTO.setBackupDuration(DURATION);
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        final MvcResult mvcResult = mvc().perform(put(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE)
                .content(getObjectMapper().writeValueAsString(awsRegionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        final ArgumentCaptor<AWSRegionDTO> awsRegionDTOCaptor = ArgumentCaptor.forClass(AWSRegionDTO.class);
        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), awsRegionDTOCaptor.capture());
        Assertions.assertThat(awsRegionDTOCaptor.getValue().getBackupDuration())
                .isEqualTo(awsRegionDTO.getBackupDuration());

        Mockito.verify(mockCloudRegionApiService).update(Mockito.eq(ID), Mockito.refEq(awsRegionDTO));

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }

    @Test
    public void shouldFailDeleteForUnauthorizedUser() throws Exception {
        mvc().perform(delete(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void shouldDeleteRegionById() throws Exception {
        Mockito.doReturn(awsRegion).when(mockCloudRegionApiService).delete(ID);

        final MvcResult mvcResult = mvc().perform(delete(String.format(REGION_ID_URL, ID))
                .servletPath(SERVLET_PATH)
                .contentType(EXPECTED_CONTENT_TYPE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EXPECTED_CONTENT_TYPE))
                .andReturn();

        final ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(mockCloudRegionApiService).delete(longCaptor.capture());
        Assertions.assertThat(longCaptor.getValue()).isEqualTo(ID);

        Mockito.verify(mockCloudRegionApiService).delete(ID);

        ControllerTestUtils.assertResponse(mvcResult, getObjectMapper(), expectedAwsRegionResult,
                new TypeReference<Result<AwsRegion>>() { });
    }
}
