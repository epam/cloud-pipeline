/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesRemovalVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryPreferencesUpdateVO;
import com.epam.pipeline.controller.vo.dts.DtsRegistryVO;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.acl.dts.DtsRegistryApiService;
import com.epam.pipeline.test.creator.dts.DtsCreatorUtils;
import com.epam.pipeline.test.web.AbstractControllerTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(controllers = DtsRegistryController.class)
public class DtsRegistryControllerTest extends AbstractControllerTest {

    private static final String DTS_URL = SERVLET_PATH + "/dts";
    private static final String ID_URL = DTS_URL + "/%d";
    private static final String DTS_PREFERENCES_URL = ID_URL + "/preferences";
    private static final String DTS_HEARTBEAT_URL = ID_URL + "/heartbeat";
    private static final String ADMIN = "ADMIN";
    private static final String REGISTRY_ID_AS_STRING = Long.toString(ID);

    private final DtsRegistry dtsRegistry = DtsCreatorUtils.getDtsRegistry();
    private final DtsRegistryVO dtsRegistryVO = DtsCreatorUtils.getDtsRegistryVO();
    private final DtsRegistryPreferencesUpdateVO updateVO = DtsCreatorUtils.getPreferenceUpdateVO();
    private final DtsRegistryPreferencesRemovalVO removalVO = DtsCreatorUtils.getPreferenceRemovalVO();

    @Autowired
    private DtsRegistryApiService mockDtsRegistryApiService;

    @Test
    @WithMockUser
    public void shouldLoadAllDtsRegistries() {
        final List<DtsRegistry> dtsRegistries = Collections.singletonList(dtsRegistry);
        doReturn(dtsRegistries).when(mockDtsRegistryApiService).loadAll();

        final MvcResult mvcResult = performRequest(get(DTS_URL));

        verify(mockDtsRegistryApiService).loadAll();
        assertResponse(mvcResult, dtsRegistries, DtsCreatorUtils.DTS_REGISTRY_LIST_TYPE);
    }

    @Test
    public void shouldFailLoadAllDtsRegistries() {
        performUnauthorizedRequest(get(DTS_URL));
    }

    @Test
    @WithMockUser
    public void shouldLoadDtsRegistry() {
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).loadByNameOrId(REGISTRY_ID_AS_STRING);

        final MvcResult mvcResult = performRequest(get(String.format(ID_URL, ID)));

        verify(mockDtsRegistryApiService).loadByNameOrId(REGISTRY_ID_AS_STRING);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailLoadDtsRegistry() {
        performUnauthorizedRequest(get(String.format(ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldCreateDtsRegistry() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dtsRegistryVO);
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).create(dtsRegistryVO);

        final MvcResult mvcResult = performRequest(post(DTS_URL).content(content));

        verify(mockDtsRegistryApiService).create(dtsRegistryVO);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailCreateDtsRegistry() {
        performUnauthorizedRequest(post(DTS_URL));
    }

    @Test
    @WithMockUser
    public void shouldUpdateDtsRegistry() throws Exception {
        final String content = getObjectMapper().writeValueAsString(dtsRegistryVO);
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).update(REGISTRY_ID_AS_STRING, dtsRegistryVO);

        final MvcResult mvcResult = performRequest(put(String.format(ID_URL, ID)).content(content));

        verify(mockDtsRegistryApiService).update(REGISTRY_ID_AS_STRING, dtsRegistryVO);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailUpdateDtsRegistry() {
        performUnauthorizedRequest(put(String.format(ID_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldUpdateDtsRegistryHeartbeat() throws Exception {
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).updateHeartbeat(REGISTRY_ID_AS_STRING);

        final MvcResult mvcResult = performRequest(put(String.format(DTS_HEARTBEAT_URL, ID)));

        verify(mockDtsRegistryApiService).updateHeartbeat(REGISTRY_ID_AS_STRING);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailUpdateDtsRegistryHeartbeat() {
        performUnauthorizedRequest(put(String.format(DTS_HEARTBEAT_URL, ID)));
    }

    @Test
    @WithMockUser
    public void shouldDeleteDtsRegistry() {
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).delete(REGISTRY_ID_AS_STRING);

        final MvcResult mvcResult = performRequest(delete(String.format(ID_URL, ID)));

        verify(mockDtsRegistryApiService).delete(REGISTRY_ID_AS_STRING);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailDeleteDtsRegistry() {
        performUnauthorizedRequest(delete(String.format(ID_URL, ID)));
    }

    @Test
    @WithMockUser(roles = ADMIN)
    public void shouldUpdateDtsRegistryPreferences() throws JsonProcessingException {
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).upsertPreferences(REGISTRY_ID_AS_STRING, updateVO);

        final String content = getObjectMapper().writeValueAsString(updateVO);
        final MvcResult mvcResult = performRequest(put(String.format(DTS_PREFERENCES_URL, ID)).content(content));

        verify(mockDtsRegistryApiService).upsertPreferences(REGISTRY_ID_AS_STRING, updateVO);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailUpdateDtsRegistryPreferences() {
        performUnauthorizedRequest(put(String.format(DTS_PREFERENCES_URL, ID)));
    }

    @Test
    @WithMockUser(roles = ADMIN)
    public void shouldDeleteDtsRegistryPreferences() throws JsonProcessingException {
        doReturn(dtsRegistry).when(mockDtsRegistryApiService).deletePreferences(REGISTRY_ID_AS_STRING, removalVO);

        final String content = getObjectMapper().writeValueAsString(removalVO);
        final MvcResult mvcResult = performRequest(delete(String.format(DTS_PREFERENCES_URL, ID)).content(content));

        verify(mockDtsRegistryApiService).deletePreferences(REGISTRY_ID_AS_STRING, removalVO);
        assertResponse(mvcResult, dtsRegistry, DtsCreatorUtils.DTS_REGISTRY_TYPE);
    }

    @Test
    public void shouldFailDeleteDtsRegistryPreferences() {
        performUnauthorizedRequest(delete(String.format(DTS_PREFERENCES_URL, ID)));
    }
}
