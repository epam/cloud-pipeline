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

package com.epam.pipeline.controller.dts;

import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.epam.pipeline.manager.dts.DtsOperationsApiService;
import com.epam.pipeline.test.creator.dts.DtsCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = DtsOperationsController.class)
public class DtsOperationsControllerTest extends AbstractControllerTest {

    private static final String DTS_URL = SERVLET_PATH + "/dts";
    private static final String LIST_URL = DTS_URL + "/list/%d";
    private static final String SUBMISSION_URL = DTS_URL + "/%d/submission/";
    private static final String CLUSTER_URL = DTS_URL + "/%d/cluster/";

    private static final String PATH = "path";
    private static final String PAGE_SIZE = "pageSize";
    private static final String MARKER = "marker";
    private static final String RUN_ID = "runId";
    private static final String LONG_AS_STRING = String.valueOf(ID);
    private static final String INT_AS_STRING = String.valueOf(TEST_INT);

    @Autowired
    private DtsOperationsApiService mockDtsOperationsApiService;

    @Test
    @WithMockUser
    public void shouldListDtsDataStorage() {
        final DtsDataStorageListing dtsDataStorageListing = DtsCreatorUtils.getDtsDataStorageListing();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(PATH, TEST_STRING);
        params.add(PAGE_SIZE, INT_AS_STRING);
        params.add(MARKER, TEST_STRING);
        doReturn(dtsDataStorageListing).when(mockDtsOperationsApiService).list(TEST_STRING, ID, TEST_INT, TEST_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(LIST_URL, ID)).params(params));

        Mockito.verify(mockDtsOperationsApiService).list(TEST_STRING, ID, TEST_INT, TEST_STRING);
        assertResponse(mvcResult, dtsDataStorageListing, DtsCreatorUtils.DTS_DATA_STORAGE_LISTING_TYPE);
    }

    @Test
    public void shouldFailListDtsDataStorage() {
        performUnauthorizedRequest(get(String.format(LIST_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldFindSubmission() {
        final DtsSubmission dtsSubmission = DtsCreatorUtils.getDtsSubmission();
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(RUN_ID, LONG_AS_STRING);
        doReturn(dtsSubmission).when(mockDtsOperationsApiService).findSubmission(ID, ID);

        final MvcResult mvcResult = performRequest(get(String.format(SUBMISSION_URL, ID)).params(params));

        Mockito.verify(mockDtsOperationsApiService).findSubmission(ID, ID);
        assertResponse(mvcResult, dtsSubmission, DtsCreatorUtils.DTS_SUBMISSION_TYPE);
    }

    @Test
    public void shouldFailFindSubmission() {
        performUnauthorizedRequest(get(String.format(SUBMISSION_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldReturnClusterConfiguration() {
        final DtsClusterConfiguration dtsClusterConfiguration = DtsCreatorUtils.getDtsClusterConfiguration();
        doReturn(dtsClusterConfiguration).when(mockDtsOperationsApiService).getClusterConfiguration(ID);

        final MvcResult mvcResult = performRequest(get(String.format(CLUSTER_URL, ID)));

        Mockito.verify(mockDtsOperationsApiService).getClusterConfiguration(ID);
        assertResponse(mvcResult, dtsClusterConfiguration, DtsCreatorUtils.DTS_CLUSTER_CONFIG_TYPE);
    }

    @Test
    public void shouldFailReturnClusterConfiguration() {
        performUnauthorizedRequest(get(String.format(CLUSTER_URL, ID)));
    }
}
